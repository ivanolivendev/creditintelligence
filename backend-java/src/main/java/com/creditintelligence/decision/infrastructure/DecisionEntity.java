package com.creditintelligence.decision.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analyst_decision")
public class DecisionEntity {
    @Id
    private UUID id;
    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;
    @Column(name = "analyst_id", nullable = false, length = 120)
    private String analystId;
    @Column(nullable = false, length = 40)
    private String outcome;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(name = "document_snapshot_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String documentSnapshotHash;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DecisionEntity() {}

    public DecisionEntity(UUID id, UUID proposalId, UUID analysisId, String analystId,
            String outcome, String reason, String snapshotHash, OffsetDateTime createdAt) {
        this.id = id;
        this.proposalId = proposalId;
        this.analysisId = analysisId;
        this.analystId = analystId;
        this.outcome = outcome;
        this.reason = reason;
        this.documentSnapshotHash = snapshotHash;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getProposalId() { return proposalId; }
    public UUID getAnalysisId() { return analysisId; }
    public String getAnalystId() { return analystId; }
    public String getOutcome() { return outcome; }
    public String getReason() { return reason; }
    public String getDocumentSnapshotHash() { return documentSnapshotHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
