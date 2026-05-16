package com.abc.claims.dao.oracle;

import com.abc.claims.dao.PolicyDao;
import com.abc.claims.model.PolicyHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("oracle")
public class OraclePolicyDao implements PolicyDao {

    private final JdbcTemplate jdbcTemplate;

    public OraclePolicyDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PolicyHolder> findByHolderId(String policyHolderId) {
        List<PolicyHolder> rows = jdbcTemplate.query(
                "SELECT policy_id, policy_holder_id, first_name, last_name, plan_id, coverage_start_date, " +
                        "coverage_end_date, starting_individual_deductible, starting_family_deductible " +
                        "FROM policy_holder WHERE policy_holder_id = ?",
                (rs, rowNum) -> mapRow(rs),
                policyHolderId
        );
        return rows.stream().findFirst();
    }

    @Override
    public Collection<PolicyHolder> findAll() {
        return jdbcTemplate.query(
                "SELECT policy_id, policy_holder_id, first_name, last_name, plan_id, coverage_start_date, " +
                        "coverage_end_date, starting_individual_deductible, starting_family_deductible " +
                        "FROM policy_holder",
                (rs, rowNum) -> mapRow(rs)
        );
    }

    @Override
    public void save(PolicyHolder policyHolder) {
        jdbcTemplate.update(
                "INSERT INTO policy_holder (policy_id, policy_holder_id, first_name, last_name, plan_id, " +
                        "coverage_start_date, coverage_end_date, starting_individual_deductible, starting_family_deductible) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                policyHolder.policyId(), policyHolder.policyHolderId(),
                policyHolder.firstName(), policyHolder.lastName(), policyHolder.planId(),
                sqlDate(policyHolder.coverageStartDate()), sqlDate(policyHolder.coverageEndDate()),
                policyHolder.startingIndividualAccumulatedDeductible(),
                policyHolder.startingFamilyAccumulatedDeductible()
        );
    }

    private PolicyHolder mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PolicyHolder(
                rs.getString("policy_id"),
                rs.getString("policy_holder_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("plan_id"),
                toLocalDate(rs.getDate("coverage_start_date")),
                toLocalDate(rs.getDate("coverage_end_date")),
                rs.getBigDecimal("starting_individual_deductible"),
                rs.getBigDecimal("starting_family_deductible")
        );
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private Date sqlDate(LocalDate localDate) {
        return localDate == null ? null : Date.valueOf(localDate);
    }
}
