package com.abc.claims.engine.rule;

import com.abc.claims.model.Plan;

import java.math.BigDecimal;

public record NoChargeRule(String rawRule) implements CoverageRule {

    @Override
    public CoverageCalculation apply(BigDecimal billedAmount, Plan plan, BigDecimal individualYTD, BigDecimal familyYTD) {
        return new CoverageCalculation(
                BigDecimal.ZERO,
                billedAmount,
                false,
                "NO CHARGE coverage; plan pays 100%"
        );
    }
}
