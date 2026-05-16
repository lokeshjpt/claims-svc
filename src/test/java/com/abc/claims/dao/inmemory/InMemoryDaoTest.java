package com.abc.claims.dao.inmemory;

import com.abc.claims.model.Plan;
import com.abc.claims.model.PolicyHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDaoTest {

    @Test
    @DisplayName("InMemoryPlanDao stores, finds (case-insensitive), and lists plans")
    void plan_dao_round_trip() {
        InMemoryPlanDao dao = new InMemoryPlanDao();
        Plan p = new Plan("P001", "ACE", new BigDecimal("6000"), new BigDecimal("12000"));
        dao.save(p);

        assertThat(dao.findById("p001")).contains(p);
        assertThat(dao.findById("missing")).isEmpty();
        assertThat(dao.findById(null)).isEmpty();
        assertThat(dao.findAll()).containsExactly(p);
    }

    @Test
    @DisplayName("InMemoryPolicyDao stores, finds, and lists policy holders (null-safe)")
    void policy_dao_round_trip() {
        InMemoryPolicyDao dao = new InMemoryPolicyDao();
        PolicyHolder h = new PolicyHolder("100001", "1000011", "Sam", "Collins",
                "P001", LocalDate.of(2004, 1, 1), null, BigDecimal.ZERO, BigDecimal.ZERO);
        dao.save(h);

        assertThat(dao.findByHolderId("1000011")).contains(h);
        assertThat(dao.findByHolderId(null)).isEmpty();
        assertThat(dao.findAll()).containsExactly(h);
    }

    @Test
    @DisplayName("InMemoryCoverageDao saves by composite key and falls back to wildcard sub-category")
    void coverage_dao_fallback_to_main_category() {
        InMemoryCoverageDao dao = new InMemoryCoverageDao();
        dao.save("P001", "Inpatient Hospital Care", "ROOM AND BOARD", "40% AFTER DEDUCTIBLE");
        dao.save("P001", "Wellness", "", "No Charge");

        Optional<String> exact = dao.findRule("P001", "Inpatient Hospital Care", "ROOM AND BOARD");
        Optional<String> fallback = dao.findRule("P001", "Wellness", "Anything goes");
        Optional<String> miss = dao.findRule("P001", "Unknown", "Subcat");

        assertThat(exact).contains("40% AFTER DEDUCTIBLE");
        assertThat(fallback).contains("No Charge");
        assertThat(miss).isEmpty();
    }
}
