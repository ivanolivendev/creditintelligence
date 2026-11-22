package com.creditintelligence.proposal.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProposalView(
        UUID id,
        ApplicantView applicant,
        BigDecimal declaredMonthlyIncome,
        String currency,
        String status,
        OffsetDateTime createdAt) {
    public record ApplicantView(UUID id, String name, String syntheticIdentifier) {}
}
