package com.abc.claims.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicyHolder(
        String policyId,
        String policyHolderId,
        String firstName,
        String lastName,
        String planId,
        LocalDate coverageStartDate,
        LocalDate coverageEndDate,
        BigDecimal startingIndividualAccumulatedDeductible,
        BigDecimal startingFamilyAccumulatedDeductible
) {
}
