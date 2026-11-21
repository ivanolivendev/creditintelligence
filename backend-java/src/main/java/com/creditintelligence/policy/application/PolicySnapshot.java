package com.creditintelligence.policy.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PolicySnapshot(UUID id, String version, String checksum, String text,
        BigDecimal incomeTolerancePercent, List<String> requiredDocumentTypes,
        BigDecimal commitmentLimitRatio, BigDecimal salaryMatchTolerancePercent,
        BigDecimal incomeStabilityWarningRatio, int simulationTermMonths,
        BigDecimal simulationMonthlyInterestRate) {
    public PolicySnapshot(UUID id, String version, String checksum, String text,
            BigDecimal incomeTolerancePercent, List<String> requiredDocumentTypes) {
        this(id, version, checksum, text, incomeTolerancePercent, requiredDocumentTypes,
                null, null, null, 0, null);
    }
}
