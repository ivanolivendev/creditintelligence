package com.creditintelligence.document.application;

import com.creditintelligence.document.domain.DocumentType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentView(UUID id, UUID proposalId, DocumentType type, int version,
        String sha256, OffsetDateTime uploadedAt, String status, String period) {}
