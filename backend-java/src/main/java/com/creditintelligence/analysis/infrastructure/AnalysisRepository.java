package com.creditintelligence.analysis.infrastructure;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<AnalysisEntity, UUID> {
    Optional<AnalysisEntity> findByProposalIdAndInputFingerprint(UUID proposalId, String inputFingerprint);
    Optional<AnalysisEntity> findByIdAndProposalId(UUID id, UUID proposalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnalysisEntity a where a.id = :id")
    Optional<AnalysisEntity> findForUpdate(@Param("id") UUID id);
}
