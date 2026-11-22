package com.creditintelligence.document.application;

import com.creditintelligence.document.domain.DocumentType;
import java.util.UUID;

public record DocumentSnapshotRef(UUID id, UUID proposalId, DocumentType type, int version,
        String objectKey, String sha256, String period) {}
