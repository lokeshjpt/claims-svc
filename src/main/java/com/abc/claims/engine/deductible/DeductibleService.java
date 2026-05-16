package com.abc.claims.engine.deductible;

import com.abc.claims.model.PolicyHolder;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Deductible state store abstraction.
 *
 * <p>This interface intentionally hides the storage mechanism. The same
 * {@link com.abc.claims.engine.ClaimsEngine} runs on top of any implementation:</p>
 *
 * <ul>
 *   <li>{@link InMemoryDeductibleService} — default for batch and tests</li>
 *   <li>{@code RedisDeductibleService}     — distributed hot-path reads via Azure Cache for Redis</li>
 *   <li>{@code OracleDeductibleService}    — transactional ledger in Oracle ATP</li>
 * </ul>
 *
 * <p>The contract is deliberately small: read individual / family year-to-date,
 * then apply a holder payment atomically. That makes it trivial to extract this
 * package as an independent microservice (e.g. {@code deductible-service}) behind
 * the same interface, called via REST/gRPC or a Spring Cloud client.</p>
 */
public interface DeductibleService {

    /**
     * Seed the store with the starting (year-to-date) balances. Typically called once
     * per batch run; a no-op for distributed back-ends that persist across runs.
     */
    void initialize(Collection<PolicyHolder> holders);

    BigDecimal individualForHolder(String policyHolderId);

    BigDecimal familyForPolicy(String policyId);

    /**
     * Atomically increment both the individual and family running totals by
     * {@code policyHolderPays}.
     */
    void applyPayment(String policyHolderId, String policyId, BigDecimal policyHolderPays);
}
