package com.abc.claims.engine.rule;

import com.abc.claims.model.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageRulesTest {

    private final Plan plan = new Plan("P001", "ACE",
            new BigDecimal("6000"), new BigDecimal("12000"));

    @Test
    @DisplayName("NoChargeRule: plan pays full billed amount, holder pays 0, does NOT accumulate to deductible")
    void no_charge_rule_pays_full() {
        CoverageCalculation calc = new NoChargeRule("No Charge").apply(
                new BigDecimal("350"), plan, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(calc.policyHolderPays()).isEqualByComparingTo("0");
        assertThat(calc.planPays()).isEqualByComparingTo("350");
        assertThat(calc.accumulatesToDeductible()).isFalse();
        assertThat(calc.processingMessage()).contains("No Charge");
    }

    @Test
    @DisplayName("FlatDollarRule: plan pays the flat amount, holder pays the rest")
    void flat_dollar_pays_flat() {
        CoverageCalculation calc = new FlatDollarRule(new BigDecimal("120"), "$120").apply(
                new BigDecimal("250"), plan, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(calc.policyHolderPays()).isEqualByComparingTo("130");
        assertThat(calc.planPays()).isEqualByComparingTo("120");
        assertThat(calc.accumulatesToDeductible()).isTrue();
        assertThat(calc.processingMessage()).contains("$120");
    }

    @Test
    @DisplayName("FlatDollarRule: when billed < flat, plan pays only the billed amount and holder pays nothing")
    void flat_dollar_capped_at_billed() {
        CoverageCalculation calc = new FlatDollarRule(new BigDecimal("120"), "$120").apply(
                new BigDecimal("80"), plan, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(calc.policyHolderPays()).isEqualByComparingTo("0");
        assertThat(calc.planPays()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("PercentageAfterDeductible: individual met → 40% plan / 60% holder + message")
    void percentage_individual_met() {
        CoverageCalculation calc = new PercentageAfterDeductibleRule(new BigDecimal("0.40"), "40% AFTER DEDUCTIBLE").apply(
                new BigDecimal("1000"), plan, new BigDecimal("6000"), BigDecimal.ZERO);

        assertThat(calc.planPays()).isEqualByComparingTo("400");
        assertThat(calc.policyHolderPays()).isEqualByComparingTo("600");
        assertThat(calc.processingMessage()).contains("INDIVIDUAL").contains("met");
    }

    @Test
    @DisplayName("PercentageAfterDeductible: family met only → uses FAMILY message")
    void percentage_family_only_met() {
        CoverageCalculation calc = new PercentageAfterDeductibleRule(new BigDecimal("0.60"), "60% AFTER DEDUCTIBLE").apply(
                new BigDecimal("100"), plan, new BigDecimal("0"), new BigDecimal("12000"));

        assertThat(calc.processingMessage()).contains("FAMILY").contains("met");
        assertThat(calc.planPays()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("PercentageAfterDeductible: both met → uses combined message")
    void percentage_both_met() {
        CoverageCalculation calc = new PercentageAfterDeductibleRule(new BigDecimal("0.40"), "40% AFTER DEDUCTIBLE").apply(
                new BigDecimal("100"), plan, new BigDecimal("6000"), new BigDecimal("12000"));

        assertThat(calc.processingMessage()).contains("INDIVIDUAL and FAMILY");
    }

    @Test
    @DisplayName("PercentageAfterDeductible: deductible NOT met & billed <= remaining → holder pays full amount")
    void percentage_not_met_billed_within_remaining() {
        CoverageCalculation calc = new PercentageAfterDeductibleRule(new BigDecimal("0.40"), "40% AFTER DEDUCTIBLE").apply(
                new BigDecimal("1000"), plan, new BigDecimal("0"), new BigDecimal("0"));

        assertThat(calc.policyHolderPays()).isEqualByComparingTo("1000");
        assertThat(calc.planPays()).isEqualByComparingTo("0");
        assertThat(calc.processingMessage()).contains("not met");
    }

    @Test
    @DisplayName("PercentageAfterDeductible: deductible NOT met, billed would cross threshold → still holder pays 100% (atomic, pre-claim determination)")
    void percentage_crosses_threshold() {
        CoverageCalculation calc = new PercentageAfterDeductibleRule(new BigDecimal("0.40"), "40% AFTER DEDUCTIBLE").apply(
                new BigDecimal("1000"), plan, new BigDecimal("5500"), new BigDecimal("5500"));

        // Spec: determination is made once, pre-claim. Since neither threshold is met before
        // this claim, holder pays full $1000; the threshold is crossed for the NEXT claim.
        assertThat(calc.planPays()).isEqualByComparingTo("0");
        assertThat(calc.policyHolderPays()).isEqualByComparingTo("1000");
        assertThat(calc.processingMessage()).contains("not met");
    }
}
