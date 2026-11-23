package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.analysis.application.AnalysisView;
import com.creditintelligence.analysis.application.SnapshotView;
import com.creditintelligence.analysis.domain.FinancialEngine.Assessment;
import com.creditintelligence.bureau.application.BureauService;
import com.creditintelligence.decision.application.DecisionService;
import com.creditintelligence.decision.application.DecisionView;
import com.creditintelligence.decision.domain.DecisionOutcome;
import com.creditintelligence.decision.infrastructure.DecisionDataStore;
import com.creditintelligence.decision.infrastructure.DecisionEntity;
import com.creditintelligence.decision.infrastructure.DecisionRepository;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalView;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.NotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

class DecisionServiceTest {
    private static final String SNAPSHOT_HASH = "a".repeat(64);
    private static final String DOCUMENT_HASH = "c".repeat(64);
    private final DecisionRepository decisions = mock(DecisionRepository.class);
    private final JdbcTemplate audit = mock(JdbcTemplate.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final AnalysisService analyses = mock(AnalysisService.class);
    private final BureauService bureau = mock(BureauService.class);
    private final DecisionService service = new DecisionService(new DecisionDataStore(decisions, audit), proposals,
            documents, analyses, bureau);
    private final UUID proposalId = UUID.randomUUID();
    private final UUID analysisId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiresAnalystRoleBeforeLoadingProposal() {
        assertThatThrownBy(() -> service.decide(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64),
                DecisionOutcome.APPROVED, "Approved", "corr-1"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(proposals, analyses, documents, decisions, bureau, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsAnUnauthenticatedPrincipalOrAnAuthenticatedNonAnalyst(boolean authenticated) {
        var principal = authenticated
                ? UsernamePasswordAuthenticationToken.authenticated("viewer", "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_VIEWER")))
                : UsernamePasswordAuthenticationToken.unauthenticated("analyst", "n/a");
        SecurityContextHolder.getContext().setAuthentication(principal);

        assertThatThrownBy(() -> decide(DecisionOutcome.APPROVED)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(proposals, analyses, documents, decisions, bureau, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED"})
    void rejectsDecisionForFinalizedProposal(String finalStatus) {
        analyst();
        var proposalId = UUID.randomUUID();
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal(proposalId, finalStatus));

        assertThatThrownBy(() -> service.decide(proposalId, UUID.randomUUID(), "a".repeat(64),
                DecisionOutcome.APPROVED, "Approved", "corr-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Proposal already has a final decision");
        verifyNoInteractions(analyses, documents, decisions, bureau, audit);
    }

    @Test
    void rejectsDecisionWhenAnalysisIsNotCompleted() {
        analyst();
        readyForDecision();
        when(analyses.dossier(proposalId, analysisId)).thenReturn(analysis("RUNNING", SNAPSHOT_HASH, null));

        assertThatThrownBy(() -> decide(DecisionOutcome.APPROVED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Analysis is not eligible for decision");
        verifyNoInteractions(documents, decisions, bureau, audit);
    }

    @Test
    void rejectsDecisionWhenAnalysisDoesNotExist() {
        analyst();
        readyForDecision();
        when(analyses.dossier(proposalId, analysisId)).thenThrow(new NotFoundException("Analysis not found"));

        assertThatThrownBy(() -> decide(DecisionOutcome.APPROVED)).isInstanceOf(NotFoundException.class)
                .hasMessage("Analysis not found");
        verifyNoInteractions(documents, decisions, bureau, audit);
    }

    @Test
    void rejectsDecisionWhenSubmittedSnapshotHashDiffers() {
        analyst();
        readyForDecision();
        when(analyses.dossier(proposalId, analysisId)).thenReturn(analysis("COMPLETED", "b".repeat(64), null));

        assertThatThrownBy(() -> decide(DecisionOutcome.APPROVED)).isInstanceOf(ConflictException.class)
                .hasMessage("Analysis is not eligible for decision");
        verifyNoInteractions(documents, decisions, bureau, audit);
    }

    @Test
    void rejectsASecondDecisionAgainstTheSameAnalysis() {
        analyst();
        readyForDecision();
        when(analyses.dossier(proposalId, analysisId)).thenReturn(analysis("COMPLETED", SNAPSHOT_HASH, null));
        when(decisions.existsByAnalysisId(analysisId)).thenReturn(true);

        assertThatThrownBy(() -> decide(DecisionOutcome.APPROVED)).isInstanceOf(ConflictException.class)
                .hasMessage("Analysis is not eligible for decision");
        verify(decisions, never()).saveAndFlush(any());
        verifyNoInteractions(documents, bureau, audit);
    }

    @ParameterizedTest
    @EnumSource(DocumentChange.class)
    void rejectsDecisionWhenAnyBoundDocumentChanges(DocumentChange change) {
        analyst();
        readyForDecision();
        when(analyses.dossier(proposalId, analysisId)).thenReturn(analysis("COMPLETED", SNAPSHOT_HASH, null));
        var current = switch (change) {
            case REMOVED -> List.<DocumentSnapshotRef>of();
            case DIFFERENT_ID -> List.of(document(UUID.randomUUID(), 1, DOCUMENT_HASH));
            case DIFFERENT_VERSION -> List.of(document(documentId, 2, DOCUMENT_HASH));
            case DIFFERENT_HASH -> List.of(document(documentId, 1, "d".repeat(64)));
        };
        when(documents.snapshot(proposalId)).thenReturn(current);

        assertThatThrownBy(() -> decide(DecisionOutcome.APPROVED)).isInstanceOf(ConflictException.class)
                .hasMessage("Document snapshot is stale");
        verify(decisions, never()).saveAndFlush(any());
        verifyNoInteractions(bureau, audit);
    }

    @Test
    void rejectsDecisionWhenTheFinancialAnalysisBureauSnapshotIsStale() {
        analyst();
        readyForDecision();
        when(analyses.dossier(proposalId, analysisId)).thenReturn(analysis("COMPLETED", SNAPSHOT_HASH,
                mock(Assessment.class)));
        when(documents.snapshot(proposalId)).thenReturn(List.of(document(documentId, 1, DOCUMENT_HASH)));
        when(analyses.bureauConsultationId(analysisId)).thenReturn(UUID.randomUUID());
        when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(UUID.randomUUID(), null));

        assertThatThrownBy(() -> decide(DecisionOutcome.APPROVED)).isInstanceOf(ConflictException.class)
                .hasMessage("Bureau snapshot is stale");
        verify(decisions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(DecisionOutcome.class)
    void recordsEligibleDecisionAndChangesProposalStatus(DecisionOutcome outcome) {
        analyst();
        readyForDecision();
        var consultation = UUID.randomUUID();
        when(analyses.dossier(proposalId, analysisId)).thenReturn(analysis("COMPLETED", SNAPSHOT_HASH,
                mock(Assessment.class)));
        when(documents.snapshot(proposalId)).thenReturn(List.of(document(documentId, 1, DOCUMENT_HASH)));
        when(analyses.bureauConsultationId(analysisId)).thenReturn(consultation);
        when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(consultation, null));
        when(decisions.saveAndFlush(any(DecisionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DecisionView result = decide(outcome);

        assertThat(result.id()).isNotNull();
        assertThat(result.proposalId()).isEqualTo(proposalId);
        assertThat(result.analysisId()).isEqualTo(analysisId);
        assertThat(result.outcome()).isEqualTo(outcome);
        assertThat(result.reason()).isEqualTo("Reviewed synthetic proposal");
        assertThat(result.analystId()).isEqualTo("analyst");
        assertThat(result.documentSnapshotHash()).isEqualTo(SNAPSHOT_HASH);
        assertThat(result.createdAt()).isNotNull();
        var saved = ArgumentCaptor.forClass(DecisionEntity.class);
        verify(decisions).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo(outcome.name());
        assertThat(saved.getValue().getReason()).isEqualTo("Reviewed synthetic proposal");
        verify(proposals).applyDecisionStatus(proposalId, outcome.name());
        verify(audit).update(contains("INSERT INTO audit_record"), any(UUID.class), eq("PROPOSAL"), eq(proposalId),
                eq("DECISION_RECORDED"), eq("analyst"), eq("corr-1"),
                eq("{\"analysisId\":\"" + analysisId + "\",\"outcome\":\"" + outcome.name() + "\"}"),
                eq(result.createdAt()));
    }

    @Test
    void listsDecisionsInRepositoryOrderAfterLoadingProposal() {
        var proposalId = UUID.randomUUID();
        var first = new DecisionEntity(UUID.randomUUID(), proposalId, UUID.randomUUID(), "analyst",
                "APPROVED", "First", SNAPSHOT_HASH + " ", OffsetDateTime.now().minusMinutes(1));
        var second = new DecisionEntity(UUID.randomUUID(), proposalId, UUID.randomUUID(), "analyst",
                "REJECTED", "Second", "b".repeat(64), OffsetDateTime.now());
        when(decisions.findByProposalIdOrderByCreatedAtAsc(proposalId)).thenReturn(List.of(first, second));

        var result = service.list(proposalId);

        assertThat(result).extracting(DecisionView::outcome)
                .containsExactly(DecisionOutcome.APPROVED, DecisionOutcome.REJECTED);
        assertThat(result).extracting(DecisionView::reason).containsExactly("First", "Second");
        assertThat(result).extracting(DecisionView::documentSnapshotHash).containsExactly(SNAPSHOT_HASH, "b".repeat(64));
        verify(proposals).get(proposalId);
    }

    private void analyst() {
        var authentication = new UsernamePasswordAuthenticationToken("analyst", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private ProposalView proposal(UUID id, String status) {
        return new ProposalView(id, null, new BigDecimal("8000.00"), "BRL", status, OffsetDateTime.now());
    }

    private void readyForDecision() {
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal(proposalId, "PENDING_HUMAN_REVIEW"));
    }

    private DecisionView decide(DecisionOutcome outcome) {
        return service.decide(proposalId, analysisId, SNAPSHOT_HASH, outcome, "  Reviewed synthetic proposal  ", "corr-1");
    }

    private AnalysisView analysis(String status, String hash, Assessment assessment) {
        return new AnalysisView(analysisId, proposalId, status, 1, hash, "POLICY-1.0", "checksum",
                new BigDecimal("8000.00"), "BRL", new BigDecimal("5.00"), List.of(),
                List.of(new SnapshotView(documentId, "IDENTIFICATION", 1, DOCUMENT_HASH, null)), List.of(),
                List.of(), "NOT_REQUESTED", null, null, OffsetDateTime.now(), null, assessment);
    }

    private DocumentSnapshotRef document(UUID id, int version, String hash) {
        return new DocumentSnapshotRef(id, proposalId, DocumentType.IDENTIFICATION, version,
                proposalId + "/" + id + ".pdf", hash, null);
    }

    private enum DocumentChange { REMOVED, DIFFERENT_ID, DIFFERENT_VERSION, DIFFERENT_HASH }
}
