package com.abc.claims.engine.rule;

import com.abc.claims.model.Plan;

import java.math.BigDecimal;

public record PercentageAfterDeductibleRule(BigDecimal planPaysFraction, String rawRule) implements CoverageRule {

    @Override
    public CoverageCalculation apply(BigDecimal billedAmount, Plan plan, BigDecimal individualYTD, BigDecimal familyYTD) {
        boolean individualMet = individualYTD.compareTo(plan.individualDeductibleThreshold()) >= 0;
        boolean familyMet = familyYTD.compareTo(plan.familyDeductibleThreshold()) >= 0;
        boolean deductibleMet = individualMet || familyMet;

        BigDecimal planPays;
        BigDecimal holderPays;
        if (deductibleMet) {
            planPays = billedAmount.multiply(planPaysFraction);
            holderPays = billedAmount.subtract(planPays);
        } else {
            // Spec: "If neither individual nor family deductible has reached its threshold,
            // policyholder pays 100%, plan pays 0%." The determination is made once, before
            // the claim, based on pre-claim YTD totals -- no straddling within a single claim.
            planPays = BigDecimal.ZERO;
            holderPays = billedAmount;
        }
        return new CoverageCalculation(holderPays, planPays, true, message(individualMet, familyMet));
    }

    private String message(boolean individualMet, boolean familyMet) {
        String pct = planPaysFraction.multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros().toPlainString() + "%";
        if (individualMet && familyMet) {
            return "ANNUAL DEDUCTIBLE (INDIVIDUAL and FAMILY) met, plan pays " + pct;
        }
        if (individualMet) {
            return "ANNUAL DEDUCTIBLE (INDIVIDUAL) met, plan pays " + pct;
        }
        if (familyMet) {
            return "ANNUAL DEDUCTIBLE (FAMILY) met, plan pays " + pct;
        }
        return "ANNUAL DEDUCTIBLE (INDIVIDUAL or FAMILY) not met, plan pays 0%";
    }
}
