package com.creditintelligence.decision.application;

import com.creditintelligence.decision.domain.DecisionOutcome;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DecisionView(UUID id, UUID proposalId, UUID analysisId, String analystId,
        DecisionOutcome outcome, String reason, String documentSnapshotHash,
        OffsetDateTime createdAt) {}
