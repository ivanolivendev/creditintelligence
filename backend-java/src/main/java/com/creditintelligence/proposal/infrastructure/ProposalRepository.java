package com.creditintelligence.proposal.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProposalRepository extends JpaRepository<ProposalEntity, UUID> {
    List<ProposalEntity> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProposalEntity p where p.id = :id")
    Optional<ProposalEntity> findForUpdate(@Param("id") UUID id);
}
