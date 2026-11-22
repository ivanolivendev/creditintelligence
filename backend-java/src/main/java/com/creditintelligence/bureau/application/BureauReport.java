package com.creditintelligence.bureau.application;

import java.time.OffsetDateTime;
import java.util.List;

/** Provider-independent context. Null fields mean unknown, never zero. */
public record BureauReport(String provider, String product, String environment, String status,
        OffsetDateTime consultedAt, String responseVersion, String referenceId,
        Score score, Restrictions restrictions, List<String> limitations) {
    public record Score(Integer value, Integer minimum, Integer maximum, String model) {}
    public record Restrictions(Integer count) {}
}
