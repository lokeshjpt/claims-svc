package com.abc.claims.model;

import java.math.BigDecimal;

public record Plan(
        String planId,
        String planName,
        BigDecimal individualDeductibleThreshold,
        BigDecimal familyDeductibleThreshold
) {
}
