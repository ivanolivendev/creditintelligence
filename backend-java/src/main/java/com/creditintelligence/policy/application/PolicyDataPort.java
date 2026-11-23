package com.creditintelligence.policy.application;

import java.util.Optional;
import java.util.UUID;

/** Application seam for versioned policy retrieval. */
public interface PolicyDataPort {
    Optional<PolicyRecord> findByVersion(String version);
    Optional<PolicyRecord> findById(UUID id);

    record PolicyRecord(UUID id, String version, String configJson, String textContent, String checksum) {}
}
