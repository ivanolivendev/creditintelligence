package com.creditintelligence.proposal.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantRepository extends JpaRepository<ApplicantEntity, UUID> {
    Optional<ApplicantEntity> findBySyntheticIdentifier(String syntheticIdentifier);
}
