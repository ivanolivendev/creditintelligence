package com.creditintelligence.policy.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<PolicyEntity, UUID> {
    Optional<PolicyEntity> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(OffsetDateTime now);
    Optional<PolicyEntity> findByVersion(String version);
}
