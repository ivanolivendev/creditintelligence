package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.creditintelligence.analysis.application.AIExplanationService;
import com.creditintelligence.analysis.application.AnalysisDataPort;
import com.creditintelligence.analysis.application.AnalysisLifecyclePort;
import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.bureau.application.BureauService;
import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.policy.application.PolicyService;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalView;
import com.creditintelligence.shared.errors.ConflictException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class AnalysisServiceTest {
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final AnalysisLifecyclePort lifecycle = mock(AnalysisLifecyclePort.class);
    private final AnalysisDataPort data = mock(AnalysisDataPort.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final PolicyService policies = mock(PolicyService.class);
    private final PythonDocumentClient python = mock(PythonDocumentClient.class);
    private final AIExplanationService explanations = mock(AIExplanationService.class);
    private final BureauService bureau = mock(BureauService.class);
    private final AnalysisService service = new AnalysisService(transactions, lifecycle, data, proposals, documents,
            policies, python, explanations, bureau);

    @Test
    void rejectsMalformedIdempotencyKeyBeforeOpeningTransaction() {
        assertThatThrownBy(() -> service.analyze(UUID.randomUUID(), "corr-1", "bad key"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) error)
                        .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(transactions, proposals, documents, python);
    }

    @Test
    void preventsNewAnalysisAfterFinalProposalDecision() {
        var proposalId = UUID.randomUUID();
        executeTransactionCallbacks();
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal(proposalId, "APPROVED"));

        assertThatThrownBy(() -> service.analyze(proposalId, "corr-1", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Finalized proposal cannot start a new analysis");
        verifyNoInteractions(documents, python, policies, bureau);
    }

    @Test
    void rejectsFinancialAnalysisWithoutThreeMatchingDocumentPeriods() {
        var proposalId = UUID.randomUUID();
        executeTransactionCallbacks();
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal(proposalId, "DRAFT"));
        when(documents.snapshot(proposalId)).thenReturn(List.of(
                document(proposalId, DocumentType.PAYSLIP, "2026-10")));
        when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(null,
                new BureauReport("NONE", "CREDIT_BUREAU", "MOCK", "NOT_REQUESTED", null,
                        null, null, null, null, List.of("not requested"))));

        assertThatThrownBy(() -> service.analyze(proposalId, "corr-1", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) error)
                        .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(policies, python, explanations);
    }

    @Test
    void rejectsLoanScenarioWhenProposalHasNoFinancialDocuments() {
        var proposalId = UUID.randomUUID();
        executeTransactionCallbacks();
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal(proposalId, "DRAFT"));
        when(documents.snapshot(proposalId)).thenReturn(List.of(document(proposalId, DocumentType.INCOME_PROOF, null)));

        var scenario = new com.creditintelligence.analysis.domain.FinancialEngine.Scenario(
                new BigDecimal("1000.00"), 12, new BigDecimal("0.01"), "BRL", "MANUAL");
        assertThatThrownBy(() -> service.analyze(proposalId, "corr-1", null, scenario))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) error)
                        .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(python, explanations, bureau);
    }

    @SuppressWarnings("unchecked")
    private void executeTransactionCallbacks() {
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private ProposalView proposal(UUID id, String status) {
        return new ProposalView(id, null, new BigDecimal("8000.00"), "BRL", status, OffsetDateTime.now());
    }

    private DocumentSnapshotRef document(UUID proposalId, DocumentType type, String period) {
        return new DocumentSnapshotRef(UUID.randomUUID(), proposalId, type, 1,
                "proposal/document.pdf", "abc123", period);
    }
}
