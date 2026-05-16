package com.abc.claims.dao.mongo;

import com.abc.claims.dao.PlanDao;
import com.abc.claims.model.Plan;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

/**
 * NoSQL implementation. Activated with {@code spring.profiles.active=mongo}.
 *
 * <p>Demonstrates that the engine is storage-agnostic. Document layout is intentionally
 * the same as the {@link Plan} record so MongoTemplate can map it directly.</p>
 */
@Repository
@Profile("mongo")
public class MongoPlanDao implements PlanDao {

    private static final String COLLECTION = "plan";
    private final MongoTemplate mongoTemplate;

    public MongoPlanDao(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Plan> findById(String planId) {
        return Optional.ofNullable(
                mongoTemplate.findOne(Query.query(Criteria.where("planId").is(planId)), Plan.class, COLLECTION));
    }

    @Override
    public Collection<Plan> findAll() {
        return mongoTemplate.findAll(Plan.class, COLLECTION);
    }

    @Override
    public void save(Plan plan) {
        mongoTemplate.save(plan, COLLECTION);
    }
}
