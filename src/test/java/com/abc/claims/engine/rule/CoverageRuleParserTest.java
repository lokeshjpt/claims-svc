package com.abc.claims.engine.rule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageRuleParserTest {

    private final CoverageRuleParser parser = new CoverageRuleParser();

    @Test
    void parses_no_charge_case_insensitively() {
        assertThat(parser.parse("No Charge")).get().isInstanceOf(NoChargeRule.class);
        assertThat(parser.parse("NO CHARGE")).get().isInstanceOf(NoChargeRule.class);
    }

    @Test
    void parses_percentage_after_deductible() {
        CoverageRule rule = parser.parse("40% AFTER DEDUCTIBLE").orElseThrow();
        assertThat(rule).isInstanceOf(PercentageAfterDeductibleRule.class);
        assertThat(((PercentageAfterDeductibleRule) rule).planPaysFraction())
                .isEqualByComparingTo(new BigDecimal("0.40"));
    }

    @Test
    void parses_flat_dollar_with_or_without_dollar_sign() {
        assertThat(((FlatDollarRule) parser.parse("$120").orElseThrow()).planPaysFlatAmount())
                .isEqualByComparingTo("120");
        assertThat(((FlatDollarRule) parser.parse("100").orElseThrow()).planPaysFlatAmount())
                .isEqualByComparingTo("100");
    }

    @Test
    void returns_empty_for_unrecognized_rule() {
        assertThat(parser.parse("HUH?")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
    }
}
