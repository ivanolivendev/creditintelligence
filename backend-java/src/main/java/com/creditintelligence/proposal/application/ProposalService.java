package com.creditintelligence.proposal.application;

import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.NotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalService {
    private final ProposalDataPort data;

    public ProposalService(ProposalDataPort data) {
        this.data = data;
    }

    @Transactional
    public ProposalView create(String name, String syntheticIdentifier, BigDecimal income, String currency) {
        var identifier = syntheticIdentifier == null || syntheticIdentifier.isBlank()
                ? generatedSyntheticIdentifier()
                : syntheticIdentifier.trim();
        var applicant = data.findApplicant(identifier).orElseGet(() ->
                data.saveApplicant(UUID.randomUUID(), name.trim(), identifier));
        if (!applicant.name().equals(name.trim())) {
            throw new ConflictException("Synthetic identifier belongs to a different applicant name");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var proposal = data.saveProposal(UUID.randomUUID(), applicant, income, currency, "DRAFT", now, now);
        return toView(proposal);
    }

    private String generatedSyntheticIdentifier() {
        return "DEMO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID id) {
        return toView(data.findProposal(id).orElseThrow(() -> new NotFoundException("Proposal not found")));
    }

    @Transactional(readOnly = true)
    public List<ProposalView> list() {
        return data.listProposals().stream().map(this::toView).toList();
    }

    @Transactional
    public ProposalView lockForMutation(UUID id) {
        return toView(data.lockProposal(id)
                .orElseThrow(() -> new NotFoundException("Proposal not found")));
    }

    @Transactional
    public void applyDecisionStatus(UUID id, String status) {
        data.lockProposal(id).orElseThrow(() -> new NotFoundException("Proposal not found"));
        data.updateStatus(id, status, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private ProposalView toView(ProposalDataPort.ProposalData proposal) {
        var applicant = proposal.applicant();
        return new ProposalView(proposal.id(),
                new ProposalView.ApplicantView(applicant.id(), applicant.name(), applicant.syntheticIdentifier()),
                proposal.declaredMonthlyIncome(), proposal.currency(), proposal.status(), proposal.createdAt());
    }
}
