package com.creditintelligence.analysis.application;

import java.util.UUID;

public record FieldView(UUID documentId, int documentVersion, String fieldName, String value,
        String currency, String extractionStatus, String evidenceRef, Integer page, String snippet) {}
