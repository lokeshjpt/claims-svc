package com.abc.claims.dao.inmemory;

import com.abc.claims.dao.CoverageDao;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile({"default", "inmemory"})
public class InMemoryCoverageDao implements CoverageDao {

    private final Map<String, String> ruleByKey = new ConcurrentHashMap<>();

    @Override
    public Optional<String> findRule(String planId, String mainCategory, String subCategory) {
        String exact = ruleByKey.get(key(planId, mainCategory, subCategory));
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(ruleByKey.get(key(planId, mainCategory, "")));
    }

    @Override
    public void save(String planId, String mainCategory, String subCategory, String rawRule) {
        ruleByKey.put(key(planId, mainCategory, subCategory), rawRule);
    }

    private String key(String planId, String mainCategory, String subCategory) {
        return normalize(planId) + "|" + normalize(mainCategory) + "|" + normalize(subCategory);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
