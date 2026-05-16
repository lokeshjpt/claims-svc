package com.abc.claims.engine;

import com.abc.claims.dao.CoverageDao;
import com.abc.claims.dao.PlanDao;
import com.abc.claims.dao.PolicyDao;
import com.abc.claims.engine.deductible.DeductibleService;
import com.abc.claims.engine.deductible.InMemoryDeductibleService;
import com.abc.claims.engine.rule.CoverageCalculation;
import com.abc.claims.engine.rule.CoverageRule;
import com.abc.claims.engine.rule.CoverageRuleParser;
import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import com.abc.claims.model.ErrorCode;
import com.abc.claims.model.Plan;
import com.abc.claims.model.PolicyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Core engine shared by batch, REST and GUI modes.
 *
 * <p>The engine delegates three concerns to swappable collaborators:
 * <ol>
 *   <li>{@link com.abc.claims.dao.PlanDao}/{@link PolicyDao}/{@link CoverageDao} — reference data (in-memory, Oracle, Mongo)</li>
 *   <li>{@link CoverageRule} strategies parsed by {@link CoverageRuleParser}</li>
 *   <li>{@link DeductibleService} — running deductible state (in-memory, Redis)</li>
 * </ol>
 * This makes the engine a clean module-boundary candidate: it can run as a library
 * inside the monolith today and be extracted as a microservice tomorrow without
 * touching its public API.</p>
 */
@Service
public class ClaimsEngine {

    private final PolicyDao policyDao;
    private final PlanDao planDao;
    private final CoverageDao coverageDao;
    private final CoverageRuleParser coverageRuleParser;
    private final ValidationService validationService;
    private final DeductibleService statefulDeductibleService;

    public ClaimsEngine(
            PolicyDao policyDao,
            PlanDao planDao,
            CoverageDao coverageDao,
            CoverageRuleParser coverageRuleParser,
            ValidationService validationService,
            DeductibleService statefulDeductibleService
    ) {
        this.policyDao = policyDao;
        this.planDao = planDao;
        this.coverageDao = coverageDao;
        this.coverageRuleParser = coverageRuleParser;
        this.validationService = validationService;
        this.statefulDeductibleService = statefulDeductibleService;
    }

    public ClaimResult processForBatchOrGui(ClaimRequest request) {
        return process(request, statefulDeductibleService, false);
    }

    public ClaimResult processForApi(ClaimRequest request) {
        DeductibleService caller = perRequestStore(request);
        return process(request, caller, true);
    }

    public List<ClaimResult> processBatch(List<ClaimRequest> requests) {
        return requests.stream().map(this::processForBatchOrGui).toList();
    }

    private ClaimResult process(ClaimRequest request, DeductibleService deductibleService, boolean statelessApiMode) {
        Optional<PolicyHolder> holderOpt = policyDao.findByHolderId(request.policyHolderId());
        Optional<String> rawCoverageRule = holderOpt.flatMap(holder ->
                coverageDao.findRule(holder.planId(), request.coverageMainCategory(), request.coverageSubCategory()));

        ErrorCode errorCode = validationService.validate(request, holderOpt.orElse(null), rawCoverageRule.orElse(null));
        if (errorCode != null) {
            return errorResult(request, errorCode);
        }

        PolicyHolder holder = holderOpt.get();
        Plan plan = planDao.findById(holder.planId()).orElse(null);
        Optional<CoverageRule> ruleOpt = coverageRuleParser.parse(rawCoverageRule.get());
        if (plan == null || ruleOpt.isEmpty()) {
            return errorResult(request, ErrorCode.E0003);
        }
        CoverageRule rule = ruleOpt.get();

        BigDecimal billedAmount = safeMoney(request.billedAmount());
        BigDecimal currentIndividual = deductibleService.individualForHolder(holder.policyHolderId());
        BigDecimal currentFamily = deductibleService.familyForPolicy(holder.policyId());

        CoverageCalculation calc = rule.apply(billedAmount, plan, currentIndividual, currentFamily);
        BigDecimal policyHolderPays = roundMoney(calc.policyHolderPays());
        BigDecimal planPays = roundMoney(calc.planPays());

        BigDecimal updatedIndividual = currentIndividual;
        BigDecimal updatedFamily = currentFamily;
        if (calc.accumulatesToDeductible() && policyHolderPays.compareTo(BigDecimal.ZERO) > 0) {
            deductibleService.applyPayment(holder.policyHolderId(), holder.policyId(), policyHolderPays);
            updatedIndividual = deductibleService.individualForHolder(holder.policyHolderId());
            updatedFamily = deductibleService.familyForPolicy(holder.policyId());
        }

        return new ClaimResult(
                request.policyId(),
                request.policyHolderId(),
                request.dateOfService(),
                request.coverageMainCategory(),
                request.coverageSubCategory(),
                roundMoney(billedAmount),
                policyHolderPays,
                planPays,
                rule.rawRule(),
                roundMoney(updatedIndividual),
                roundMoney(updatedFamily),
                null,
                null,
                calc.processingMessage()
        );
    }

    private DeductibleService perRequestStore(ClaimRequest request) {
        InMemoryDeductibleService perRequest = new InMemoryDeductibleService();
        PolicyHolder holder = policyDao.findByHolderId(request.policyHolderId()).orElse(null);
        if (holder == null) {
            return perRequest;
        }
        perRequest.initialize(List.of(new PolicyHolder(
                holder.policyId(), holder.policyHolderId(),
                holder.firstName(), holder.lastName(), holder.planId(),
                holder.coverageStartDate(), holder.coverageEndDate(),
                safeMoney(request.individualAccumulatedDeductible()),
                safeMoney(request.familyAccumulatedDeductible())
        )));
        return perRequest;
    }

    private ClaimResult errorResult(ClaimRequest request, ErrorCode errorCode) {
        return new ClaimResult(
                request.policyId(),
                request.policyHolderId(),
                request.dateOfService(),
                request.coverageMainCategory(),
                request.coverageSubCategory(),
                safeMoney(request.billedAmount()),
                null, null, null, null, null,
                errorCode.name(),
                errorCode.message(),
                null
        );
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal roundMoney(BigDecimal value) {
        return safeMoney(value).setScale(2, RoundingMode.HALF_UP);
    }
}
