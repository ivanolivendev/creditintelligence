package com.creditintelligence.policy.infrastructure;

import com.creditintelligence.policy.application.PolicyDataPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyDataStore implements PolicyDataPort {
    private final PolicyRepository repository;

    public PolicyDataStore(PolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PolicyRecord> findByVersion(String version) {
        return repository.findByVersion(version).map(this::toRecord);
    }

    @Override
    public Optional<PolicyRecord> findById(UUID id) {
        return repository.findById(id).map(this::toRecord);
    }

    private PolicyRecord toRecord(PolicyEntity entity) {
        return new PolicyRecord(entity.getId(), entity.getVersion(), entity.getConfigJson(),
                entity.getTextContent(), entity.getChecksum());
    }
}
