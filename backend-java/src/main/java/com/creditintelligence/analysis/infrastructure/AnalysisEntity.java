package com.creditintelligence.analysis.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis")
public class AnalysisEntity {
    @Id
    private UUID id;
    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(nullable = false)
    private int attempt;
    @Column(name = "lease_token", nullable = false)
    private UUID leaseToken;
    @Column(name = "input_fingerprint", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String inputFingerprint;
    @Column(name = "document_snapshot_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String documentSnapshotHash;
    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    @Column(name = "error_code", length = 80)
    private String errorCode;
    @Version
    private long version;

    protected AnalysisEntity() {}

    public AnalysisEntity(UUID id, UUID proposalId, UUID leaseToken, String fingerprint,
            String snapshotHash, UUID policyVersionId, OffsetDateTime startedAt) {
        this.id = id;
        this.proposalId = proposalId;
        this.status = "RUNNING";
        this.attempt = 1;
        this.leaseToken = leaseToken;
        this.inputFingerprint = fingerprint;
        this.documentSnapshotHash = snapshotHash;
        this.policyVersionId = policyVersionId;
        this.startedAt = startedAt;
    }

    public void complete(OffsetDateTime at) {
        if (!"RUNNING".equals(status)) throw new IllegalStateException("Analysis is not running");
        status = "COMPLETED";
        completedAt = at;
    }

    public void fail(String failureStatus, String code, OffsetDateTime at) {
        if (!"RUNNING".equals(status)) throw new IllegalStateException("Analysis is not running");
        status = failureStatus;
        errorCode = code;
        completedAt = at;
    }

    public void retry(UUID nextLease, OffsetDateTime at) {
        if (!"FAILED_RETRYABLE".equals(status) && !"RUNNING".equals(status)) {
            throw new IllegalStateException("Analysis cannot be retried");
        }
        status = "RUNNING";
        attempt++;
        leaseToken = nextLease;
        startedAt = at;
        completedAt = null;
        errorCode = null;
    }

    public UUID getId() { return id; }
    public UUID getProposalId() { return proposalId; }
    public String getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public UUID getLeaseToken() { return leaseToken; }
    public String getInputFingerprint() { return inputFingerprint; }
    public String getDocumentSnapshotHash() { return documentSnapshotHash; }
    public UUID getPolicyVersionId() { return policyVersionId; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
}
