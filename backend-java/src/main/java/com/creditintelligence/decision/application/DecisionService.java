package com.creditintelligence.decision.application;

import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.bureau.application.BureauService;
import com.creditintelligence.decision.domain.DecisionOutcome;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.shared.errors.ConflictException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionService {
    private final DecisionDataPort decisions;
    private final ProposalService proposals;
    private final DocumentService documents;
    private final AnalysisService analyses;
    private final BureauService bureau;

    public DecisionService(DecisionDataPort decisions, ProposalService proposals,
            DocumentService documents, AnalysisService analyses,
            BureauService bureau) {
        this.decisions = decisions;
        this.proposals = proposals;
        this.documents = documents;
        this.analyses = analyses;
        this.bureau = bureau;
    }

    @Transactional
    public DecisionView decide(UUID proposalId, UUID analysisId, String snapshotHash,
            DecisionOutcome outcome, String reason, String correlationId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !authentication.getAuthorities().stream().anyMatch(role -> "ROLE_ANALYST".equals(role.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException("Analyst role required");
        }
        var proposal = proposals.lockForMutation(proposalId);
        if ("APPROVED".equals(proposal.status()) || "REJECTED".equals(proposal.status())) {
            throw new ConflictException("Proposal already has a final decision");
        }
        var dossier = analyses.dossier(proposalId, analysisId);
        if (!"COMPLETED".equals(dossier.status()) || !dossier.documentSnapshotHash().equals(snapshotHash)
                || decisions.existsByAnalysisId(analysisId)) {
            throw new ConflictException("Analysis is not eligible for decision");
        }
        var current = documents.snapshot(proposalId);
        if (current.size() != dossier.documents().size()
                || current.stream().anyMatch(item -> dossier.documents().stream().noneMatch(bound ->
                        bound.documentId().equals(item.id()) && bound.version() == item.version()
                                && bound.sha256().equals(item.sha256())))) {
            throw new ConflictException("Document snapshot is stale");
        }
        if (dossier.financialAssessment() != null) {
            var bound = analyses.bureauConsultationId(analysisId);
            if (!java.util.Objects.equals(bound, bureau.latest(proposalId).id())) {
                throw new ConflictException("Bureau snapshot is stale");
            }
        }
        var actor = authentication.getName();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var saved = decisions.save(new DecisionDataPort.DecisionRecord(UUID.randomUUID(), proposalId, analysisId,
                actor, outcome, reason.trim(), snapshotHash, now));
        proposals.applyDecisionStatus(proposalId, outcome.name());
        decisions.audit(proposalId, analysisId, outcome, actor, correlationId, now);
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<DecisionView> list(UUID proposalId) {
        proposals.get(proposalId);
        return decisions.list(proposalId).stream().map(this::toView).toList();
    }

    private DecisionView toView(DecisionDataPort.DecisionRecord entity) {
        return new DecisionView(entity.id(), entity.proposalId(), entity.analysisId(), entity.analystId(),
                entity.outcome(), entity.reason(), entity.snapshotHash().trim(), entity.createdAt());
    }
}
