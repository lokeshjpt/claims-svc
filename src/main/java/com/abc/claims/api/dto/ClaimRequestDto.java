package com.abc.claims.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * REST request DTO. Fields are intentionally non-validated at the framework
 * level so that missing / malformed payloads flow into the engine and surface
 * as business error code {@code E0005} in a 200 response body — consistent
 * with how CSV batch and GUI modes report row-level errors.
 */
public record ClaimRequestDto(
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
