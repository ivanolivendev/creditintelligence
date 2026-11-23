package com.creditintelligence.proposal.infrastructure;

import com.creditintelligence.proposal.application.ProposalDataPort;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ProposalDataStore implements ProposalDataPort {
    private final ApplicantRepository applicants;
    private final ProposalRepository proposals;

    public ProposalDataStore(ApplicantRepository applicants, ProposalRepository proposals) {
        this.applicants = applicants;
        this.proposals = proposals;
    }

    @Override
    public Optional<ApplicantData> findApplicant(String syntheticIdentifier) {
        return applicants.findBySyntheticIdentifier(syntheticIdentifier).map(this::toApplicant);
    }

    @Override
    public ApplicantData saveApplicant(UUID id, String name, String syntheticIdentifier) {
        return toApplicant(applicants.save(new ApplicantEntity(id, name, syntheticIdentifier)));
    }

    @Override
    public ProposalData saveProposal(UUID id, ApplicantData applicant, BigDecimal income, String currency,
            String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        var applicantEntity = applicants.findById(applicant.id()).orElseThrow();
        return toProposal(proposals.save(new ProposalEntity(id, applicantEntity, income, currency, status,
                createdAt, updatedAt)));
    }

    @Override
    public Optional<ProposalData> findProposal(UUID id) {
        return proposals.findById(id).map(this::toProposal);
    }

    @Override
    public List<ProposalData> listProposals() {
        return proposals.findAllByOrderByCreatedAtDesc().stream().map(this::toProposal).toList();
    }

    @Override
    public Optional<ProposalData> lockProposal(UUID id) {
        return proposals.findForUpdate(id).map(this::toProposal);
    }

    @Override
    public void updateStatus(UUID id, String status, OffsetDateTime updatedAt) {
        proposals.findForUpdate(id).orElseThrow().setStatus(status, updatedAt);
    }

    private ApplicantData toApplicant(ApplicantEntity entity) {
        return new ApplicantData(entity.getId(), entity.getName(), entity.getSyntheticIdentifier());
    }

    private ProposalData toProposal(ProposalEntity entity) {
        return new ProposalData(entity.getId(), toApplicant(entity.getApplicant()),
                entity.getDeclaredMonthlyIncome(), entity.getCurrency(), entity.getStatus(),
                entity.getCreatedAt());
    }
}
