package com.abc.claims.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClaimRequest(
        String policyId,
        String policyHolderId,
        LocalDate dateOfService,
        String coverageMainCategory,
        String coverageSubCategory,
        BigDecimal billedAmount,
        BigDecimal individualAccumulatedDeductible,
        BigDecimal familyAccumulatedDeductible
) {
}
