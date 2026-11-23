package com.creditintelligence.analysis.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Application seam for analysis lifecycle, leases and idempotency bindings. */
public interface AnalysisLifecyclePort {
    Optional<AnalysisRecord> findByIdAndProposal(UUID analysisId, UUID proposalId);
    Optional<AnalysisRecord> findById(UUID analysisId);
    Optional<AnalysisRecord> findByFingerprint(UUID proposalId, String fingerprint);
    Optional<AnalysisRecord> lock(UUID analysisId);
    AnalysisRecord create(UUID id, UUID proposalId, UUID lease, String fingerprint, String snapshotHash,
            UUID policyId, OffsetDateTime startedAt);
    AnalysisRecord retry(AnalysisRecord analysis, UUID lease, OffsetDateTime startedAt);
    void complete(UUID analysisId, UUID lease, OffsetDateTime completedAt);
    void fail(UUID analysisId, UUID lease, String status, String errorCode, OffsetDateTime completedAt);
    Optional<RequestBinding> binding(UUID proposalId, String requestKey);
    void bind(UUID proposalId, String requestKey, String fingerprint, UUID analysisId, OffsetDateTime createdAt);

    record RequestBinding(String fingerprint, UUID analysisId) {}
    record AnalysisRecord(UUID id, UUID proposalId, String status, int attempt, UUID leaseToken,
            String fingerprint, String snapshotHash, UUID policyVersionId, OffsetDateTime startedAt,
            OffsetDateTime completedAt, String errorCode) {}
}
