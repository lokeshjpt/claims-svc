package com.abc.claims.engine.rule;

import java.math.BigDecimal;

public record CoverageCalculation(
        BigDecimal policyHolderPays,
        BigDecimal planPays,
        boolean accumulatesToDeductible,
        String processingMessage
) {
}
