package com.abc.claims.dao.inmemory;

import com.abc.claims.dao.PolicyDao;
import com.abc.claims.model.PolicyHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile({"default", "inmemory"})
public class InMemoryPolicyDao implements PolicyDao {

    private final Map<String, PolicyHolder> byHolderId = new ConcurrentHashMap<>();

    @Override
    public Optional<PolicyHolder> findByHolderId(String policyHolderId) {
        return Optional.ofNullable(byHolderId.get(normalize(policyHolderId)));
    }

    @Override
    public Collection<PolicyHolder> findAll() {
        return byHolderId.values();
    }

    @Override
    public void save(PolicyHolder policyHolder) {
        byHolderId.put(normalize(policyHolder.policyHolderId()), policyHolder);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
