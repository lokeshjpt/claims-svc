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
            BigDecimal remainingIndividual = plan.individualDeductibleThreshold().subtract(individualYTD).max(BigDecimal.ZERO);
            BigDecimal remainingFamily = plan.familyDeductibleThreshold().subtract(familyYTD).max(BigDecimal.ZERO);
            BigDecimal remainingToDeductible = remainingIndividual.min(remainingFamily);
            if (billedAmount.compareTo(remainingToDeductible) <= 0) {
                planPays = BigDecimal.ZERO;
                holderPays = billedAmount;
            } else {
                BigDecimal overage = billedAmount.subtract(remainingToDeductible);
                planPays = overage.multiply(planPaysFraction);
                holderPays = billedAmount.subtract(planPays);
            }
        }
        return new CoverageCalculation(holderPays, planPays, true, message(individualMet, familyMet));
    }

    private String message(boolean individualMet, boolean familyMet) {
        if (individualMet && familyMet) {
            return "ANNUAL DEDUCTIBLE (INDIVIDUAL and FAMILY) met, plan pays based on " + rawRule;
        }
        if (individualMet) {
            return "ANNUAL DEDUCTIBLE (INDIVIDUAL) met, plan pays based on " + rawRule;
        }
        if (familyMet) {
            return "ANNUAL DEDUCTIBLE (FAMILY) met, plan pays based on " + rawRule;
        }
        return "ANNUAL DEDUCTIBLE (INDIVIDUAL or FAMILY) not met, plan pays 0%";
    }
}
