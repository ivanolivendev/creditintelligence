package com.creditintelligence.proposal.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Deep application seam for proposal persistence; adapters own JPA details. */
public interface ProposalDataPort {
    Optional<ApplicantData> findApplicant(String syntheticIdentifier);
    ApplicantData saveApplicant(UUID id, String name, String syntheticIdentifier);
    ProposalData saveProposal(UUID id, ApplicantData applicant, BigDecimal income, String currency,
            String status, OffsetDateTime createdAt, OffsetDateTime updatedAt);
    Optional<ProposalData> findProposal(UUID id);
    List<ProposalData> listProposals();
    Optional<ProposalData> lockProposal(UUID id);
    void updateStatus(UUID id, String status, OffsetDateTime updatedAt);

    record ApplicantData(UUID id, String name, String syntheticIdentifier) {}

    record ProposalData(UUID id, ApplicantData applicant, BigDecimal declaredMonthlyIncome,
            String currency, String status, OffsetDateTime createdAt) {}
}
