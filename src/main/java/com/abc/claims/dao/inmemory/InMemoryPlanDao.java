package com.abc.claims.dao.inmemory;

import com.abc.claims.dao.PlanDao;
import com.abc.claims.model.Plan;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile({"default", "inmemory"})
public class InMemoryPlanDao implements PlanDao {

    private final Map<String, Plan> plans = new ConcurrentHashMap<>();

    @Override
    public Optional<Plan> findById(String planId) {
        return Optional.ofNullable(plans.get(normalize(planId)));
    }

    @Override
    public Collection<Plan> findAll() {
        return plans.values();
    }

    @Override
    public void save(Plan plan) {
        plans.put(normalize(plan.planId()), plan);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
