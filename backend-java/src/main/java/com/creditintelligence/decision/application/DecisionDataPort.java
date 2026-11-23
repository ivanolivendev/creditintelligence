package com.creditintelligence.decision.application;

import com.creditintelligence.decision.domain.DecisionOutcome;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Application seam for decisions and their audit record. */
public interface DecisionDataPort {
    boolean existsByAnalysisId(UUID analysisId);
    DecisionRecord save(DecisionRecord decision);
    List<DecisionRecord> list(UUID proposalId);
    void audit(UUID proposalId, UUID analysisId, DecisionOutcome outcome, String actor,
            String correlationId, OffsetDateTime createdAt);

    record DecisionRecord(UUID id, UUID proposalId, UUID analysisId, String analystId,
            DecisionOutcome outcome, String reason, String snapshotHash, OffsetDateTime createdAt) {}
}
