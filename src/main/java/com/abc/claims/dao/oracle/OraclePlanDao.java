package com.abc.claims.dao.oracle;

import com.abc.claims.dao.PlanDao;
import com.abc.claims.model.Plan;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Oracle-backed implementation. Activated with {@code spring.profiles.active=oracle}.
 *
 * <p>Schema (suggested):
 * <pre>
 *   CREATE TABLE plan (
 *     plan_id              VARCHAR2(20) PRIMARY KEY,
 *     plan_name            VARCHAR2(120) NOT NULL,
 *     individual_threshold NUMBER(12,2) NOT NULL,
 *     family_threshold     NUMBER(12,2) NOT NULL
 *   );
 * </pre>
 * </p>
 */
@Repository
@Profile("oracle")
public class OraclePlanDao implements PlanDao {

    private final JdbcTemplate jdbcTemplate;

    public OraclePlanDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Plan> findById(String planId) {
        List<Plan> rows = jdbcTemplate.query(
                "SELECT plan_id, plan_name, individual_threshold, family_threshold FROM plan WHERE plan_id = ?",
                (rs, rowNum) -> new Plan(
                        rs.getString("plan_id"),
                        rs.getString("plan_name"),
                        rs.getBigDecimal("individual_threshold"),
                        rs.getBigDecimal("family_threshold")
                ),
                planId
        );
        return rows.stream().findFirst();
    }

    @Override
    public Collection<Plan> findAll() {
        return jdbcTemplate.query(
                "SELECT plan_id, plan_name, individual_threshold, family_threshold FROM plan",
                (rs, rowNum) -> new Plan(
                        rs.getString("plan_id"),
                        rs.getString("plan_name"),
                        rs.getBigDecimal("individual_threshold"),
                        rs.getBigDecimal("family_threshold")
                )
        );
    }

    @Override
    public void save(Plan plan) {
        jdbcTemplate.update(
                """
                MERGE INTO plan p
                USING (SELECT ? AS plan_id FROM dual) src ON (p.plan_id = src.plan_id)
                WHEN MATCHED THEN UPDATE SET plan_name = ?, individual_threshold = ?, family_threshold = ?
                WHEN NOT MATCHED THEN INSERT (plan_id, plan_name, individual_threshold, family_threshold)
                  VALUES (?, ?, ?, ?)
                """,
                plan.planId(), plan.planName(),
                nullSafe(plan.individualDeductibleThreshold()), nullSafe(plan.familyDeductibleThreshold()),
                plan.planId(), plan.planName(),
                nullSafe(plan.individualDeductibleThreshold()), nullSafe(plan.familyDeductibleThreshold())
        );
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
