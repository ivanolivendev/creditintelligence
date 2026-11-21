package com.creditintelligence.document.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_metadata")
public class DocumentEntity {
    @Id
    private UUID id;
    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String sha256;
    @Column(name = "document_version", nullable = false)
    private int documentVersion;
    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(name = "period_key", length = 7)
    private String periodKey;

    protected DocumentEntity() {}

    public DocumentEntity(UUID id, UUID proposalId, String type, String objectKey, String sha256,
            int documentVersion, OffsetDateTime uploadedAt, String status) {
        this(id, proposalId, type, objectKey, sha256, documentVersion, uploadedAt, status, null);
    }

    public DocumentEntity(UUID id, UUID proposalId, String type, String objectKey, String sha256,
            int documentVersion, OffsetDateTime uploadedAt, String status, String periodKey) {
        this.id = id;
        this.proposalId = proposalId;
        this.type = type;
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.documentVersion = documentVersion;
        this.uploadedAt = uploadedAt;
        this.status = status;
        this.periodKey = periodKey;
    }

    public UUID getId() { return id; }
    public UUID getProposalId() { return proposalId; }
    public String getType() { return type; }
    public String getObjectKey() { return objectKey; }
    public String getSha256() { return sha256; }
    public int getDocumentVersion() { return documentVersion; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public String getStatus() { return status; }
    public String getPeriodKey() { return periodKey; }
}
