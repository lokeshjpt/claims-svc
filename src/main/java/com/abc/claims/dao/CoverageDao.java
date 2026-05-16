package com.abc.claims.dao;

import java.util.Optional;

public interface CoverageDao {

    Optional<String> findRule(String planId, String mainCategory, String subCategory);

    void save(String planId, String mainCategory, String subCategory, String rawRule);
}
