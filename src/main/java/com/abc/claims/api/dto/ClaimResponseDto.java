package com.abc.claims.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClaimResponseDto(
        String policyId,
        String policyHolderId,
        LocalDate dateOfService,
        String coverageMainCategory,
        String coverageSubCategory,
        BigDecimal billedAmount,
        BigDecimal policyHolderPays,
        BigDecimal planPays,
        String ruleUsed,
        BigDecimal individualAccumulatedDeductible,
        BigDecimal familyAccumulatedDeductible,
        String errorCode,
        String errorMessage,
        String processingMessage
) {
}
