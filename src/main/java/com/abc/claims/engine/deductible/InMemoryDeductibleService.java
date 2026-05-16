package com.abc.claims.engine.deductible;

import com.abc.claims.model.PolicyHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"default", "inmemory"})
public class InMemoryDeductibleService implements DeductibleService {

    private final Map<String, BigDecimal> individualByHolderId = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> familyByPolicyId = new ConcurrentHashMap<>();

    @Override
    public void initialize(Collection<PolicyHolder> holders) {
        individualByHolderId.clear();
        familyByPolicyId.clear();
        holders.forEach(holder -> {
            individualByHolderId.put(normalize(holder.policyHolderId()), safe(holder.startingIndividualAccumulatedDeductible()));
            familyByPolicyId.merge(
                    normalize(holder.policyId()),
                    safe(holder.startingFamilyAccumulatedDeductible()),
                    BigDecimal::max
            );
        });
    }

    @Override
    public BigDecimal individualForHolder(String policyHolderId) {
        return individualByHolderId.getOrDefault(normalize(policyHolderId), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal familyForPolicy(String policyId) {
        return familyByPolicyId.getOrDefault(normalize(policyId), BigDecimal.ZERO);
    }

    @Override
    public void applyPayment(String policyHolderId, String policyId, BigDecimal policyHolderPays) {
        individualByHolderId.merge(normalize(policyHolderId), safe(policyHolderPays), BigDecimal::add);
        familyByPolicyId.merge(normalize(policyId), safe(policyHolderPays), BigDecimal::add);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
