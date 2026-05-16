package com.abc.claims.engine;

import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ErrorCode;
import com.abc.claims.model.PolicyHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationServiceTest {

    private final ValidationService validation = new ValidationService(LocalDate.of(2016, 12, 31));

    @Test
    @DisplayName("E0005 when required fields are blank or null")
    void malformed_request_returns_e0005() {
        ClaimRequest blankIds = new ClaimRequest("", "", LocalDate.of(2016, 5, 10),
                "Outpatient Services", "PRIMARY CARE OFFICE VISIT", new BigDecimal("140"), null, null);
        assertThat(validation.validate(blankIds, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isEqualTo(ErrorCode.E0005);

        ClaimRequest nullDate = new ClaimRequest("100001", "1000011", null,
                "Outpatient Services", "PRIMARY CARE OFFICE VISIT", new BigDecimal("140"), null, null);
        assertThat(validation.validate(nullDate, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isEqualTo(ErrorCode.E0005);

        ClaimRequest blankCategory = new ClaimRequest("100001", "1000011", LocalDate.of(2016, 5, 10),
                "", "", new BigDecimal("140"), null, null);
        assertThat(validation.validate(blankCategory, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isEqualTo(ErrorCode.E0005);

        ClaimRequest negativeAmount = new ClaimRequest("100001", "1000011", LocalDate.of(2016, 5, 10),
                "Outpatient Services", "PRIMARY CARE OFFICE VISIT", new BigDecimal("-10"), null, null);
        assertThat(validation.validate(negativeAmount, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isEqualTo(ErrorCode.E0005);

        ClaimRequest nullAmount = new ClaimRequest("100001", "1000011", LocalDate.of(2016, 5, 10),
                "Outpatient Services", "PRIMARY CARE OFFICE VISIT", null, null, null);
        assertThat(validation.validate(nullAmount, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isEqualTo(ErrorCode.E0005);
    }

    @Test
    @DisplayName("E0001 when policy holder is unknown")
    void unknown_holder_returns_e0001() {
        ClaimRequest req = req(LocalDate.of(2016, 5, 10));
        assertThat(validation.validate(req, null, "40% AFTER DEDUCTIBLE")).isEqualTo(ErrorCode.E0001);
    }

    @Test
    @DisplayName("E0004 when service date is in the future")
    void future_date_returns_e0004() {
        ClaimRequest req = req(LocalDate.of(2017, 1, 1));
        assertThat(validation.validate(req, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isEqualTo(ErrorCode.E0004);
    }

    @Test
    @DisplayName("E0002 when service date is outside the coverage period")
    void outside_coverage_returns_e0002() {
        ClaimRequest req = req(LocalDate.of(2016, 1, 15));
        assertThat(validation.validate(req, holder(LocalDate.of(2016, 2, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isEqualTo(ErrorCode.E0002);
    }

    @Test
    @DisplayName("E0003 when no coverage rule applies for the category")
    void unknown_category_returns_e0003() {
        ClaimRequest req = req(LocalDate.of(2016, 5, 10));
        assertThat(validation.validate(req, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), ""))
                .isEqualTo(ErrorCode.E0003);
    }

    @Test
    @DisplayName("returns null when all checks pass")
    void valid_claim_returns_null() {
        ClaimRequest req = req(LocalDate.of(2016, 5, 10));
        assertThat(validation.validate(req, holder(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 12, 31)), "$120"))
                .isNull();
    }

    private ClaimRequest req(LocalDate dos) {
        return new ClaimRequest("100001", "1000011", dos,
                "Outpatient Services", "PRIMARY CARE OFFICE VISIT",
                new BigDecimal("140"), null, null);
    }

    private PolicyHolder holder(LocalDate start, LocalDate end) {
        return new PolicyHolder("100001", "1000011", "Test", "User", "P0001",
                start, end, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
