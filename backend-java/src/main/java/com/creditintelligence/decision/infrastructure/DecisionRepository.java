package com.creditintelligence.decision.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<DecisionEntity, UUID> {
    boolean existsByAnalysisId(UUID analysisId);
    List<DecisionEntity> findByProposalIdOrderByCreatedAtAsc(UUID proposalId);
}
