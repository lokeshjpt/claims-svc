package com.abc.claims.engine.rule;

import com.abc.claims.model.Plan;

import java.math.BigDecimal;

/**
 * Strategy interface for plan coverage rules.
 *
 * <p>Each concrete rule encapsulates how a billed amount is split between the
 * plan and the policy holder. Implementations must be stateless and side-effect free
 * so they can be safely shared across threads (REST, batch and GUI modes share the
 * same engine).</p>
 */
public interface CoverageRule {

    String rawRule();

    CoverageCalculation apply(BigDecimal billedAmount, Plan plan, BigDecimal individualYTD, BigDecimal familyYTD);
}
