package com.abc.claims.dao.oracle;

import com.abc.claims.dao.CoverageDao;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("oracle")
public class OracleCoverageDao implements CoverageDao {

    private final JdbcTemplate jdbcTemplate;

    public OracleCoverageDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findRule(String planId, String mainCategory, String subCategory) {
        List<String> rows = jdbcTemplate.query(
                """
                SELECT raw_rule FROM plan_coverage
                WHERE plan_id = ?
                  AND UPPER(main_category) = UPPER(?)
                  AND (UPPER(sub_category) = UPPER(?) OR sub_category IS NULL)
                ORDER BY CASE WHEN sub_category IS NULL THEN 1 ELSE 0 END FETCH FIRST 1 ROWS ONLY
                """,
                (rs, rowNum) -> rs.getString(1),
                planId, mainCategory, subCategory
        );
        return rows.stream().findFirst();
    }

    @Override
    public void save(String planId, String mainCategory, String subCategory, String rawRule) {
        jdbcTemplate.update(
                "INSERT INTO plan_coverage (plan_id, main_category, sub_category, raw_rule) VALUES (?, ?, ?, ?)",
                planId, mainCategory, subCategory, rawRule
        );
    }
}
