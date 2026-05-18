package com.abc.claims.engine.rule;

import com.abc.claims.model.Plan;

import java.math.BigDecimal;

public record FlatDollarRule(BigDecimal planPaysFlatAmount, String rawRule) implements CoverageRule {

    @Override
    public CoverageCalculation apply(BigDecimal billedAmount, Plan plan, BigDecimal individualYTD, BigDecimal familyYTD) {
        BigDecimal planPays = planPaysFlatAmount.min(billedAmount);
        BigDecimal holderPays = billedAmount.subtract(planPays);
        return new CoverageCalculation(
                holderPays,
                planPays,
                true,
                "Flat dollar coverage - plan pays $" + planPaysFlatAmount.stripTrailingZeros().toPlainString() + " regardless of deductible"
        );
    }

    @Override
    public String rawRule() {
        // Match spreadsheet "Rule used" column convention: e.g. "$120 FLAT"
        return rawRule.trim().toUpperCase().endsWith("FLAT") ? rawRule : rawRule + " FLAT";
    }
}
