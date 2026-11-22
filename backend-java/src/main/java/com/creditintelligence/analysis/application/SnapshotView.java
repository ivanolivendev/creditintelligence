package com.creditintelligence.analysis.application;

import java.util.UUID;

public record SnapshotView(UUID documentId, String type, int version, String sha256, String period) {}
