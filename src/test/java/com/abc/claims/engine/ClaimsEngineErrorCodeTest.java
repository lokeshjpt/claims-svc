package com.abc.claims.engine;

import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import com.abc.claims.model.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClaimsEngineErrorCodeTest {

    @Autowired
    ClaimsEngine engine;

    @Test
    @DisplayName("E0005 — malformed claim data (missing date) reported in 200 body")
    void e0005_malformed_request() {
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100001", "1000011", null,
                "Outpatient Services", "PRIMARY CARE OFFICE VISIT",
                new BigDecimal("150"), null, null));

        assertThat(r.errorCode()).isEqualTo(ErrorCode.E0005.name());
        assertThat(r.errorMessage()).isEqualTo(ErrorCode.E0005.message());
        assertThat(r.policyHolderPays()).isNull();
        assertThat(r.planPays()).isNull();
    }

    @Test
    @DisplayName("E0001 — policy holder does not exist")
    void e0001_unknown_holder() {
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100001", "9999999", LocalDate.of(2016, 10, 12),
                "Prescription Drugs", "GENERIC", new BigDecimal("61.4"), null, null));

        assertThat(r.errorCode()).isEqualTo(ErrorCode.E0001.name());
        assertThat(r.errorMessage()).isEqualTo(ErrorCode.E0001.message());
        assertThat(r.policyHolderPays()).isNull();
        assertThat(r.planPays()).isNull();
    }

    @Test
    @DisplayName("E0002 — coverage not active on date of service")
    void e0002_outside_coverage_period() {
        // Holder 1000041 coverage ends 2016-05-31 per PolicyData
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100004", "1000041", LocalDate.of(2016, 6, 15),
                "Outpatient Services", "PRIMARY CARE OFFICE VISIT",
                new BigDecimal("150"), null, null));

        assertThat(r.errorCode()).isEqualTo(ErrorCode.E0002.name());
        assertThat(r.errorMessage()).isEqualTo(ErrorCode.E0002.message());
    }

    @Test
    @DisplayName("E0003 — unknown sub-category has no rule")
    void e0003_unknown_category() {
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100002", "1000021", LocalDate.of(2016, 6, 15),
                "Preventive Care", "UNKNOWN SUBCATEGORY XYZ",
                new BigDecimal("350"), null, null));

        assertThat(r.errorCode()).isEqualTo(ErrorCode.E0003.name());
        assertThat(r.errorMessage()).isEqualTo(ErrorCode.E0003.message());
    }

    @Test
    @DisplayName("E0004 — future-dated claim rejected")
    void e0004_future_date() {
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100002", "1000021", LocalDate.of(2017, 1, 15),
                "Outpatient Services", "LAB TESTS",
                new BigDecimal("1200"), null, null));

        assertThat(r.errorCode()).isEqualTo(ErrorCode.E0004.name());
        assertThat(r.errorMessage()).isEqualTo(ErrorCode.E0004.message());
    }

    @Test
    @DisplayName("Flat dollar — plan pays $120 regardless of deductible")
    void flat_dollar_rule_pays_flat_amount() {
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100007", "1000071", LocalDate.of(2016, 7, 10),
                "Emergency And Urgent Care", "URGENT CARE VISIT",
                new BigDecimal("250"), new BigDecimal("4460.82"), new BigDecimal("4460.82")));

        assertThat(r.errorCode()).isNull();
        assertThat(r.ruleUsed()).isEqualTo("$120");
        assertThat(r.planPays()).isEqualByComparingTo("120");
        assertThat(r.policyHolderPays()).isEqualByComparingTo("130");
    }

    @Test
    @DisplayName("No Charge — preventive care fully covered")
    void no_charge_rule_fully_covered() {
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100002", "1000021", LocalDate.of(2016, 6, 15),
                "Preventive Care", "ROUTINE PHYSICAL EXAM",
                new BigDecimal("350"), new BigDecimal("4000"), new BigDecimal("4000")));

        assertThat(r.errorCode()).isNull();
        assertThat(r.ruleUsed()).isEqualTo("No Charge");
        assertThat(r.policyHolderPays()).isEqualByComparingTo("0");
        assertThat(r.planPays()).isEqualByComparingTo("350");
    }

    @Test
    @DisplayName("Percentage rule — deductible not yet met → holder pays full amount")
    void percentage_rule_deductible_not_met() {
        ClaimResult r = engine.processForApi(new ClaimRequest(
                "100001", "1000011", LocalDate.of(2016, 5, 6),
                "Inpatient Hospital Care", "ROOM AND BOARD",
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(r.errorCode()).isNull();
        assertThat(r.ruleUsed()).isEqualTo("40% AFTER DEDUCTIBLE");
        assertThat(r.policyHolderPays()).isEqualByComparingTo("1000");
        assertThat(r.planPays()).isEqualByComparingTo("0");
    }
}
