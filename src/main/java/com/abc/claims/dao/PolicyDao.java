package com.abc.claims.dao;

import com.abc.claims.model.PolicyHolder;

import java.util.Collection;
import java.util.Optional;

public interface PolicyDao {

    Optional<PolicyHolder> findByHolderId(String policyHolderId);

    Collection<PolicyHolder> findAll();

    void save(PolicyHolder policyHolder);
}
