package com.creditintelligence.proposal.application;

import com.creditintelligence.proposal.infrastructure.ApplicantEntity;
import com.creditintelligence.proposal.infrastructure.ApplicantRepository;
import com.creditintelligence.proposal.infrastructure.ProposalEntity;
import com.creditintelligence.proposal.infrastructure.ProposalRepository;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.NotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalService {
    private final ApplicantRepository applicants;
    private final ProposalRepository proposals;

    public ProposalService(ApplicantRepository applicants, ProposalRepository proposals) {
        this.applicants = applicants;
        this.proposals = proposals;
    }

    @Transactional
    public ProposalView create(String name, String syntheticIdentifier, BigDecimal income, String currency) {
        var applicant = applicants.findBySyntheticIdentifier(syntheticIdentifier).orElseGet(() ->
                applicants.save(new ApplicantEntity(UUID.randomUUID(), name.trim(), syntheticIdentifier)));
        if (!applicant.getName().equals(name.trim())) {
            throw new ConflictException("Synthetic identifier belongs to a different applicant name");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var proposal = proposals.save(new ProposalEntity(UUID.randomUUID(), applicant, income, currency,
                "DRAFT", now, now));
        return toView(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID id) {
        return toView(proposals.findById(id).orElseThrow(() -> new NotFoundException("Proposal not found")));
    }

    @Transactional
    public ProposalView lockForMutation(UUID id) {
        return toView(proposals.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found")));
    }

    @Transactional
    public void applyDecisionStatus(UUID id, String status) {
        var proposal = proposals.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        proposal.setStatus(status, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private ProposalView toView(ProposalEntity proposal) {
        var applicant = proposal.getApplicant();
        return new ProposalView(proposal.getId(),
                new ProposalView.ApplicantView(applicant.getId(), applicant.getName(),
                        applicant.getSyntheticIdentifier()),
                proposal.getDeclaredMonthlyIncome(), proposal.getCurrency(), proposal.getStatus(),
                proposal.getCreatedAt());
    }
}
