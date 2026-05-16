package com.abc.claims.dao;

import com.abc.claims.model.Plan;

import java.util.Collection;
import java.util.Optional;

/**
 * Reference data access for {@link Plan}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code InMemoryPlanDao} — default; populated from the workbook on startup</li>
 *   <li>{@code OraclePlanDao}  — production; uses JdbcTemplate against Oracle</li>
 *   <li>{@code MongoPlanDao}   — alternate NoSQL repository</li>
 * </ul>
 * Implementations are activated via Spring profiles ({@code inmemory}, {@code oracle},
 * {@code mongo}) so the same {@link com.abc.claims.engine.ClaimsEngine} runs unchanged
 * in modular-monolith and microservice deployments.</p>
 */
public interface PlanDao {

    Optional<Plan> findById(String planId);

    Collection<Plan> findAll();

    void save(Plan plan);
}
