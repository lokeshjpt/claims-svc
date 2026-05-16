package com.abc.claims.engine.deductible;

import com.abc.claims.model.PolicyHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

/**
 * Redis-backed deductible service for distributed deployments (e.g. Azure Cache for Redis).
 * Active under {@code spring.profiles.active=redis}.
 *
 * <p>Atomic increments via {@code HINCRBYFLOAT} ensure correctness when multiple
 * claim-processor instances mutate the same family or individual key concurrently.</p>
 */
@Component
@Profile("redis")
public class RedisDeductibleService implements DeductibleService {

    private static final String INDIVIDUAL_KEY = "claims:deductible:individual";
    private static final String FAMILY_KEY = "claims:deductible:family";

    private final StringRedisTemplate redisTemplate;

    public RedisDeductibleService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void initialize(Collection<PolicyHolder> holders) {
        holders.forEach(holder -> {
            redisTemplate.opsForHash().putIfAbsent(
                    INDIVIDUAL_KEY,
                    holder.policyHolderId(),
                    nullSafe(holder.startingIndividualAccumulatedDeductible()).toPlainString()
            );
            redisTemplate.opsForHash().putIfAbsent(
                    FAMILY_KEY,
                    holder.policyId(),
                    nullSafe(holder.startingFamilyAccumulatedDeductible()).toPlainString()
            );
        });
    }

    @Override
    public BigDecimal individualForHolder(String policyHolderId) {
        return read(INDIVIDUAL_KEY, policyHolderId);
    }

    @Override
    public BigDecimal familyForPolicy(String policyId) {
        return read(FAMILY_KEY, policyId);
    }

    @Override
    public void applyPayment(String policyHolderId, String policyId, BigDecimal policyHolderPays) {
        redisTemplate.opsForHash().increment(INDIVIDUAL_KEY, policyHolderId, policyHolderPays.doubleValue());
        redisTemplate.opsForHash().increment(FAMILY_KEY, policyId, policyHolderPays.doubleValue());
    }

    private BigDecimal read(String key, String field) {
        return Optional.ofNullable(redisTemplate.opsForHash().get(key, field))
                .map(Object::toString)
                .map(BigDecimal::new)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
