package com.creditintelligence.document.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Application seam for immutable document metadata and versions. */
public interface DocumentDataPort {
    int findMaxVersion(UUID proposalId, String type);
    int findMaxVersionForPeriod(UUID proposalId, String type, String period);
    DocumentRecord save(DocumentRecord document);
    List<DocumentRecord> list(UUID proposalId);

    record DocumentRecord(UUID id, UUID proposalId, String type, String objectKey, String sha256,
            int version, OffsetDateTime uploadedAt, String status, String period) {}
}
