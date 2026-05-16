package com.abc.claims.engine.deductible;

import com.abc.claims.model.PolicyHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDeductibleServiceTest {

    private final InMemoryDeductibleService service = new InMemoryDeductibleService();

    @BeforeEach
    void seedHolders() {
        service.initialize(List.of(
                holder("100001", "1000011", new BigDecimal("500"), new BigDecimal("750")),
                holder("100001", "1000012", new BigDecimal("250"), new BigDecimal("750")),
                holder("100002", "1000021", new BigDecimal("0"),   new BigDecimal("0"))
        ));
    }

    @Test
    @DisplayName("seeds individual and family balances from holder records")
    void seeds_starting_balances() {
        assertThat(service.individualForHolder("1000011")).isEqualByComparingTo("500");
        assertThat(service.individualForHolder("1000012")).isEqualByComparingTo("250");
        assertThat(service.familyForPolicy("100001")).isEqualByComparingTo("750");
        assertThat(service.familyForPolicy("100002")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("applyPayment increments both individual and family running totals")
    void apply_payment_updates_both_buckets() {
        service.applyPayment("1000011", "100001", new BigDecimal("200"));

        assertThat(service.individualForHolder("1000011")).isEqualByComparingTo("700");
        assertThat(service.familyForPolicy("100001")).isEqualByComparingTo("950");
        assertThat(service.individualForHolder("1000012")).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("missing ids resolve to zero, not null")
    void missing_ids_return_zero() {
        assertThat(service.individualForHolder("does-not-exist")).isEqualByComparingTo("0");
        assertThat(service.familyForPolicy("does-not-exist")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("initialize is idempotent — second call resets balances")
    void initialize_resets_state() {
        service.applyPayment("1000011", "100001", new BigDecimal("100"));
        service.initialize(List.of(holder("100001", "1000011", BigDecimal.ZERO, BigDecimal.ZERO)));
        assertThat(service.individualForHolder("1000011")).isEqualByComparingTo("0");
    }

    private PolicyHolder holder(String policyId, String holderId, BigDecimal indiv, BigDecimal family) {
        return new PolicyHolder(policyId, holderId, "Test", "User", "P0001",
                LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31), indiv, family);
    }
}
