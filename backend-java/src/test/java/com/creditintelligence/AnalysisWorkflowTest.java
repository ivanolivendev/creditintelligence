package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.creditintelligence.analysis.application.AIExplanationService;
import com.creditintelligence.analysis.application.AIExplanationView;
import com.creditintelligence.analysis.application.AnalysisDataPort;
import com.creditintelligence.analysis.application.AnalysisLifecyclePort.AnalysisRecord;
import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.analysis.application.FieldView;
import com.creditintelligence.analysis.application.FindingView;
import com.creditintelligence.analysis.application.SnapshotView;
import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.analysis.domain.FinancialEngine.Scenario;
import com.creditintelligence.analysis.infrastructure.AnalysisEntity;
import com.creditintelligence.analysis.infrastructure.AnalysisLifecycleStore;
import com.creditintelligence.analysis.infrastructure.AnalysisRepository;
import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.bureau.application.BureauService;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.DocumentResult;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ExtractedField;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ExtractedTransaction;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ProcessResponse;
import com.creditintelligence.policy.application.PolicyService;
import com.creditintelligence.policy.application.PolicySnapshot;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalView;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.NotFoundException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

class AnalysisWorkflowTest {
    private static final String KEY = "synthetic-analysis-key";
    private static final String CORRELATION_ID = "synthetic-correlation";
    private static final BigDecimal INCOME = new BigDecimal("8000.00");

    private final UUID proposalId = UUID.randomUUID();
    private final PolicySnapshot standardPolicy = new PolicySnapshot(UUID.randomUUID(), "POLICY-1.0",
            "a".repeat(64), "Synthetic policy", new BigDecimal("3.00"), List.of("INCOME_PROOF", "IDENTIFICATION"));
    private final PolicySnapshot financialPolicy = new PolicySnapshot(UUID.randomUUID(), "CREDIT_POLICY_DEMO_3.4",
            "b".repeat(64), "Synthetic financial policy", new BigDecimal("3.00"), List.of("PAYSLIP", "BANK_STATEMENT"),
            new BigDecimal("0.30"), new BigDecimal("3.00"), new BigDecimal("0.10"), 12, new BigDecimal("0.01"));

    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final AnalysisRepository repository = mock(AnalysisRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AnalysisLifecycleStore lifecycle = new AnalysisLifecycleStore(repository, jdbc);
    private final AnalysisDataPort data = mock(AnalysisDataPort.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final PolicyService policies = mock(PolicyService.class);
    private final PythonDocumentClient python = mock(PythonDocumentClient.class);
    private final AIExplanationService explanations = mock(AIExplanationService.class);
    private final BureauService bureau = mock(BureauService.class);
    private final AnalysisService service = new AnalysisService(transactions, lifecycle, data, proposals, documents,
            policies, python, explanations, bureau);

    private final Map<UUID, AnalysisEntity> analyses = new HashMap<>();
    private final Map<String, ResultSet> bindings = new HashMap<>();
    private final Map<UUID, List<SnapshotView>> snapshots = new HashMap<>();
    private final Map<UUID, List<FindingView>> findings = new HashMap<>();
    private final Map<UUID, FinancialEngine.Assessment> assessments = new HashMap<>();
    private final Map<UUID, UUID> bureauReferences = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void configurePorts() {
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());

        when(repository.saveAndFlush(any(AnalysisEntity.class))).thenAnswer(invocation -> {
            AnalysisEntity entity = invocation.getArgument(0);
            analyses.put(entity.getId(), entity);
            return entity;
        });
        when(repository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(analyses.get(invocation.<UUID>getArgument(0))));
        when(repository.findForUpdate(any())).thenAnswer(invocation -> repository.findById(invocation.getArgument(0)));
        when(repository.findByIdAndProposalId(any(), eq(proposalId))).thenAnswer(invocation ->
                repository.findById(invocation.getArgument(0)));
        when(repository.findByProposalIdAndInputFingerprint(eq(proposalId), anyString())).thenAnswer(invocation ->
                analyses.values().stream().filter(row -> row.getInputFingerprint().equals(invocation.getArgument(1)))
                        .findFirst());

        when(jdbc.update(anyString(), eq(proposalId), anyString(), anyString(), any(UUID.class),
                any(OffsetDateTime.class))).thenAnswer(invocation -> {
                    var row = mock(ResultSet.class);
                    when(row.getString(1)).thenReturn(invocation.<String>getArgument(3) + " ");
                    when(row.getObject(2, UUID.class)).thenReturn(invocation.getArgument(4));
                    bindings.put(invocation.getArgument(2), row);
                    return 1;
                });
        when(jdbc.query(anyString(), any(RowMapper.class), eq(proposalId), anyString())).thenAnswer(invocation -> {
            var binding = bindings.get(invocation.<String>getArgument(3));
            RowMapper<?> mapper = invocation.getArgument(1);
            return binding == null ? List.of() : List.of(mapper.mapRow(binding, 0));
        });

        doAnswer(invocation -> {
            List<DocumentSnapshotRef> refs = invocation.getArgument(1);
            snapshots.put(invocation.getArgument(0), refs.stream().map(ref -> new SnapshotView(
                    ref.id(), ref.type().name(), ref.version(), ref.sha256(), ref.period())).toList());
            return null;
        }).when(data).saveSnapshot(any(), anyList());
        when(data.snapshot(any())).thenAnswer(invocation -> snapshots.get(invocation.<UUID>getArgument(0)));
        doAnswer(invocation -> {
            findings.computeIfAbsent(invocation.getArgument(0), id -> new ArrayList<>())
                    .add(invocation.getArgument(1));
            return null;
        }).when(data).saveFinding(any(), any());
        when(data.findings(any())).thenAnswer(invocation -> findings.getOrDefault(invocation.getArgument(0), List.of()));
        doAnswer(invocation -> {
            assessments.put(invocation.getArgument(0), invocation.getArgument(4));
            bureauReferences.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(data).saveFinancialAssessment(any(), eq(financialPolicy.id()), any(), any(), any());
        when(data.financialAssessment(any())).thenAnswer(invocation -> assessments.get(invocation.<UUID>getArgument(0)));
        when(data.bureauConsultationId(any())).thenAnswer(invocation -> bureauReferences.get(invocation.<UUID>getArgument(0)));

        var proposal = new ProposalView(proposalId, null, INCOME, "BRL", "DRAFT", OffsetDateTime.now(ZoneOffset.UTC));
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal);
        when(proposals.get(proposalId)).thenReturn(proposal);
        when(policies.current()).thenReturn(standardPolicy);
        when(policies.financial()).thenReturn(financialPolicy);
        when(policies.get(standardPolicy.id())).thenReturn(standardPolicy);
        when(policies.get(financialPolicy.id())).thenReturn(financialPolicy);
        when(explanations.get(any())).thenReturn(AIExplanationView.withoutContent("NOT_REQUESTED"));
    }

    @Test
    void reusesCompletedInputAndBindsAnotherKeyToTheSameAnalysis() {
        var proof = document(DocumentType.INCOME_PROOF, null, 1, "c");
        var identification = document(DocumentType.IDENTIFICATION, null, 1, "d");
        when(documents.snapshot(proposalId)).thenReturn(List.of(proof, identification), List.of(identification, proof));
        successfulExtraction();

        var first = service.analyze(proposalId, CORRELATION_ID, KEY);
        var repeated = service.analyze(proposalId, CORRELATION_ID, KEY);
        var secondKey = service.analyze(proposalId, CORRELATION_ID, "another-synthetic-key");

        assertThat(repeated).isEqualTo(first);
        assertThat(secondKey).isEqualTo(first);
        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.attempt()).isEqualTo(1);
        assertThat(first.completedAt()).isAfterOrEqualTo(first.startedAt());
        assertThat(first.documentSnapshotHash()).matches("[a-f0-9]{64}");
        assertThat(first.policyVersion()).isEqualTo(standardPolicy.version());
        assertThat(first.policyChecksum()).isEqualTo(standardPolicy.checksum());
        assertThat(first.findings()).extracting(FindingView::code, FindingView::result, FindingView::evidenceRef)
                .containsExactly(tuple("INCOME_CONSISTENCY", "PASS", evidence(proof, "monthlyIncome")),
                        tuple("REQUIRED_DOCUMENTS", "PASS", null));
        assertThat(lifecycle.binding(proposalId, "another-synthetic-key"))
                .isEqualTo(lifecycle.binding(proposalId, KEY)).isPresent();
        assertThat(analyses).containsOnlyKeys(first.analysisId());
        verify(python).process(eq(first.analysisId()), any(), eq(List.of(proof, identification)),
                eq(standardPolicy.version()), eq(CORRELATION_ID));
        verify(explanations).generate(first.analysisId(), standardPolicy, CORRELATION_ID);
    }

    @Test
    void rejectsChangedInputForBoundKeyAndKeepsOldSnapshotWhenStartingWithoutKey() {
        var previous = document(DocumentType.INCOME_PROOF, null, 1, "c");
        var updated = document(DocumentType.INCOME_PROOF, null, 2, "d");
        when(documents.snapshot(proposalId)).thenReturn(List.of(previous), List.of(updated));
        successfulExtraction();

        var first = service.analyze(proposalId, CORRELATION_ID, KEY);
        assertThatThrownBy(() -> service.analyze(proposalId, CORRELATION_ID, KEY))
                .isInstanceOf(ConflictException.class).hasMessage("Idempotency-Key belongs to a different input");
        assertThat(analyses).containsOnlyKeys(first.analysisId());
        assertThat(first.findings()).extracting(FindingView::code, FindingView::result, FindingView::details)
                .contains(tuple("REQUIRED_DOCUMENTS", "FAIL", "Missing: [IDENTIFICATION]"));

        var changed = service.analyze(proposalId, CORRELATION_ID, null);

        assertThat(changed.analysisId()).isNotEqualTo(first.analysisId());
        assertThat(changed.documentSnapshotHash()).isNotEqualTo(first.documentSnapshotHash());
        assertThat(changed.documents()).extracting(SnapshotView::documentId, SnapshotView::version, SnapshotView::sha256)
                .containsExactly(tuple(updated.id(), 2, updated.sha256()));
        assertThat(service.dossier(proposalId, first.analysisId())).isEqualTo(first);
        assertThat(first.documents()).extracting(SnapshotView::documentId, SnapshotView::version, SnapshotView::sha256)
                .containsExactly(tuple(previous.id(), 1, previous.sha256()));
        assertThatThrownBy(() -> service.dossier(UUID.randomUUID(), first.analysisId()))
                .isInstanceOf(NotFoundException.class).hasMessage("Analysis not found");
    }

    @Test
    void retainsOriginalPolicyInTheDossierAfterTheCurrentPolicyChanges() {
        var proof = document(DocumentType.INCOME_PROOF, null, 1, "c");
        when(documents.snapshot(proposalId)).thenReturn(List.of(proof));
        var revisedPolicy = new PolicySnapshot(UUID.randomUUID(), "POLICY-1.1", "e".repeat(64),
                "Synthetic revised policy", new BigDecimal("2.00"), List.of("INCOME_PROOF"));
        when(policies.current()).thenReturn(standardPolicy, revisedPolicy);
        when(policies.get(revisedPolicy.id())).thenReturn(revisedPolicy);
        successfulExtraction();

        var original = service.analyze(proposalId, CORRELATION_ID, KEY);
        assertThatThrownBy(() -> service.analyze(proposalId, CORRELATION_ID, KEY))
                .isInstanceOf(ConflictException.class).hasMessage("Idempotency-Key belongs to a different input");
        var revised = service.analyze(proposalId, CORRELATION_ID, null);

        assertThat(revised.analysisId()).isNotEqualTo(original.analysisId());
        assertThat(revised.documentSnapshotHash()).isEqualTo(original.documentSnapshotHash());
        assertThat(revised.policyVersion()).isEqualTo(revisedPolicy.version());
        assertThat(revised.policyChecksum()).isEqualTo(revisedPolicy.checksum());
        var saved = service.dossier(proposalId, original.analysisId());
        assertThat(saved).isEqualTo(original);
        assertThat(saved.policyVersion()).isEqualTo(standardPolicy.version());
        assertThat(saved.policyChecksum()).isEqualTo(standardPolicy.checksum());
        verify(python).process(eq(revised.analysisId()), any(), eq(List.of(proof)),
                eq(revisedPolicy.version()), eq(CORRELATION_ID));
    }

    @Test
    void leavesEvidenceSnapshotIntactOnRetryableFailureAndCompletesWithNewLease() {
        var proof = document(DocumentType.INCOME_PROOF, null, 2, "e");
        when(documents.snapshot(proposalId)).thenReturn(List.of(proof));
        when(python.process(any(), any(), anyList(), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("Synthetic transport failure"))
                .thenAnswer(invocation -> extraction(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));

        var failed = service.analyze(proposalId, CORRELATION_ID, KEY);
        var failedLease = analyses.get(failed.analysisId()).getLeaseToken();

        assertThat(failed.status()).isEqualTo("FAILED_RETRYABLE");
        assertThat(failed.errorCode()).isEqualTo("DOCUMENT_INTELLIGENCE_UNAVAILABLE");
        assertThat(failed.findings()).isEmpty();
        assertThat(failed.documents()).containsExactly(new SnapshotView(proof.id(), "INCOME_PROOF", 2, proof.sha256(), null));
        assertThat(failed.financialAssessment()).isNull();

        var recovered = service.analyze(proposalId, CORRELATION_ID, KEY);

        assertThat(recovered.status()).isEqualTo("COMPLETED");
        assertThat(recovered.errorCode()).isNull();
        assertThat(recovered.analysisId()).isEqualTo(failed.analysisId());
        assertThat(recovered.attempt()).isEqualTo(2);
        assertThat(recovered.documentSnapshotHash()).isEqualTo(failed.documentSnapshotHash());
        assertThat(recovered.documents()).isEqualTo(failed.documents());
        var currentLease = analyses.get(recovered.analysisId()).getLeaseToken();
        assertThat(currentLease).isNotEqualTo(failedLease);
        verify(python).process(failed.analysisId(), failedLease, List.of(proof), standardPolicy.version(), CORRELATION_ID);
        verify(python).process(recovered.analysisId(), currentLease, List.of(proof), standardPolicy.version(), CORRELATION_ID);
        verify(data).saveSnapshot(failed.analysisId(), List.of(proof));
        verify(explanations).generate(recovered.analysisId(), standardPolicy, CORRELATION_ID);
    }

    @Test
    void neverRetriesAnInvalidExtractionForTheSameFingerprint() {
        var proof = document(DocumentType.INCOME_PROOF, null, 1, "e");
        when(documents.snapshot(proposalId)).thenReturn(List.of(proof));
        when(python.process(any(), any(), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> extraction(invocation.getArgument(0), UUID.randomUUID(),
                        invocation.getArgument(2)));

        var failed = service.analyze(proposalId, CORRELATION_ID, null);
        var repeated = service.analyze(proposalId, CORRELATION_ID, null);

        assertThat(failed.status()).isEqualTo("FAILED_PERMANENT");
        assertThat(failed.errorCode()).isEqualTo("INVALID_EXTRACTION_RESPONSE");
        assertThat(failed.attempt()).isEqualTo(1);
        assertThat(failed.findings()).isEmpty();
        assertThat(failed.documents()).hasSize(1);
        assertThat(repeated).isEqualTo(failed);
        verify(python).process(eq(failed.analysisId()), any(), eq(List.of(proof)),
                eq(standardPolicy.version()), eq(CORRELATION_ID));
    }

    @Test
    void lifecycleNeverExposesAStaleLeaseOrTransitionsAFinalizedRecord() {
        var id = UUID.randomUUID();
        var firstLease = UUID.randomUUID();
        var nextLease = UUID.randomUUID();
        var started = OffsetDateTime.parse("2026-10-01T12:00:00Z");
        var retriedAt = started.plusMinutes(2);
        var completedAt = started.plusMinutes(3);
        var initial = lifecycle.create(id, proposalId, firstLease, "a".repeat(64), "b".repeat(64),
                standardPolicy.id(), started);

        assertThat(initial).isEqualTo(new AnalysisRecord(
                id, proposalId, "RUNNING", 1, firstLease, "a".repeat(64), "b".repeat(64),
                standardPolicy.id(), started, null, null));
        lifecycle.fail(id, firstLease, "FAILED_RETRYABLE", "SYNTHETIC_OUTAGE", started.plusSeconds(10));
        var retried = lifecycle.retry(lifecycle.findById(id).orElseThrow(), nextLease, retriedAt);

        assertThat(retried.status()).isEqualTo("RUNNING");
        assertThat(retried.attempt()).isEqualTo(2);
        assertThat(retried.leaseToken()).isEqualTo(nextLease);
        assertThat(retried.startedAt()).isEqualTo(retriedAt);
        assertThat(retried.completedAt()).isNull();
        assertThat(retried.errorCode()).isNull();
        assertThatThrownBy(() -> lifecycle.complete(id, firstLease, completedAt))
                .isInstanceOf(IllegalStateException.class).hasMessage("Analysis attempt is no longer current");
        lifecycle.complete(id, nextLease, completedAt);

        var completed = lifecycle.findByIdAndProposal(id, proposalId).orElseThrow();
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.completedAt()).isEqualTo(completedAt);
        assertThat(completed.snapshotHash()).isEqualTo(initial.snapshotHash());
        assertThat(completed.policyVersionId()).isEqualTo(initial.policyVersionId());
        assertThatThrownBy(() -> lifecycle.retry(completed, UUID.randomUUID(), completedAt.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Analysis cannot be retried");
        assertThatThrownBy(() -> lifecycle.fail(id, nextLease, "FAILED_PERMANENT", "SYNTHETIC", completedAt))
                .isInstanceOf(IllegalStateException.class).hasMessage("Analysis is not running");
        assertThat(lifecycle.findById(id)).contains(completed);
    }

    @Test
    void aCurrentLeaseDoesNotDispatchAnotherExtraction() {
        when(documents.snapshot(proposalId)).thenReturn(List.of());
        when(python.process(any(), any(), anyList(), anyString(), anyString())).thenAnswer(invocation -> {
            var activeId = invocation.<UUID>getArgument(0);
            var reused = service.analyze(proposalId, CORRELATION_ID, KEY);
            assertThat(reused.analysisId()).isEqualTo(activeId);
            assertThat(reused.status()).isEqualTo("RUNNING");
            assertThat(reused.attempt()).isEqualTo(1);
            assertThat(reused.findings()).isEmpty();
            return extraction(activeId, invocation.getArgument(1), List.of());
        }).thenThrow(new AssertionError("An active lease cannot dispatch a second extraction"));

        var completed = service.analyze(proposalId, CORRELATION_ID, KEY);

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.findings()).extracting(FindingView::code, FindingView::result)
                .containsExactly(tuple("INCOME_CONSISTENCY", "NOT_EVALUATED"), tuple("REQUIRED_DOCUMENTS", "FAIL"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expiredLeaseCannotCompleteOrFailTheNewRunningAttempt(boolean transportFailure) {
        var proof = document(DocumentType.INCOME_PROOF, null, 1, "e");
        when(documents.snapshot(proposalId)).thenReturn(List.of(proof));
        var dispatched = new AtomicInteger();
        var previousLease = new AtomicReference<UUID>();
        when(python.process(any(), any(), anyList(), anyString(), anyString())).thenAnswer(invocation -> {
            var id = invocation.<UUID>getArgument(0);
            var lease = invocation.<UUID>getArgument(1);
            if (dispatched.getAndIncrement() == 0) {
                previousLease.set(lease);
                var initial = analyses.get(id);
                var expiredAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
                analyses.put(id, new AnalysisEntity(id, proposalId, lease, initial.getInputFingerprint(),
                        initial.getDocumentSnapshotHash(), initial.getPolicyVersionId(), expiredAt));
                assertThatThrownBy(() -> service.analyze(proposalId, CORRELATION_ID, KEY))
                        .isInstanceOf(IllegalArgumentException.class).hasMessage("Synthetic worker interruption");
                if (transportFailure) {
                    throw new ResourceAccessException("Late transport failure");
                }
                return extraction(id, lease, List.of(proof));
            }
            assertThat(lease).isNotEqualTo(previousLease.get());
            throw new IllegalArgumentException("Synthetic worker interruption");
        });

        var current = service.analyze(proposalId, CORRELATION_ID, KEY);

        assertThat(current.status()).isEqualTo("RUNNING");
        assertThat(current.attempt()).isEqualTo(2);
        assertThat(current.errorCode()).isNull();
        assertThat(current.completedAt()).isNull();
        assertThat(current.findings()).isEmpty();
        assertThat(current.documents()).hasSize(1);
        assertThat(dispatched).hasValue(2);
        verify(data).saveSnapshot(current.analysisId(), List.of(proof));
        verify(explanations, never()).generate(any(), any(), any());
    }

    @Test
    void completesFinancialAnalysisWithDocumentEvidenceAndStoredBureauAndPolicy() {
        var refs = financialDocuments();
        when(documents.snapshot(proposalId)).thenReturn(refs);
        var consultationId = UUID.randomUUID();
        var report = bureauReport("AVAILABLE");
        when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(consultationId, report));
        successfulExtraction();

        var dossier = service.analyze(proposalId, CORRELATION_ID, KEY);

        assertThat(dossier.status()).isEqualTo("COMPLETED");
        assertThat(dossier.policyVersion()).isEqualTo(financialPolicy.version());
        assertThat(dossier.policyChecksum()).isEqualTo(financialPolicy.checksum());
        assertThat(dossier.documents()).hasSize(6);
        assertThat(dossier.documentSnapshotHash())
                .isEqualTo("1214020c48dee937d7212fa2b06b7dce5b77cfc1fb6a4c22cdfe41386a74e3c0");
        assertThat(dossier.financialAssessment().bureau()).isEqualTo(report);
        assertThat(dossier.financialAssessment().reviewStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(dossier.financialAssessment().averageNetIncome()).isEqualByComparingTo("8000.00");
        assertThat(dossier.financialAssessment().maxNewInstallment()).isEqualByComparingTo("2400.00");
        assertThat(dossier.financialAssessment().simulation().termMonths()).isEqualTo(12);
        assertThat(dossier.financialAssessment().simulation().monthlyInterestRate()).isEqualByComparingTo("0.01");
        assertThat(dossier.financialAssessment().simulation().pricingSource()).isEqualTo("MANUAL_DEMO");
        assertThat(dossier.findings()).extracting(FindingView::code, FindingView::result, FindingView::evidenceRef)
                .contains(tuple("INCOME_CONSISTENCY", "PASS", evidence(refs.getFirst(), "netSalary")),
                        tuple("REQUIRED_BANK_STATEMENTS", "PASS", null),
                        tuple("REQUIRED_PAYSLIPS", "PASS", evidence(refs.getFirst(), "netSalary")));
        verify(data, times(15)).saveField(eq(dossier.analysisId()), any(FieldView.class));
        verify(data).saveFinancialAssessment(eq(dossier.analysisId()), eq(financialPolicy.id()),
                eq(consultationId), any(), eq(dossier.financialAssessment()));
        assertThat(service.bureauConsultationId(dossier.analysisId())).isEqualTo(consultationId);
        verify(python).process(eq(dossier.analysisId()), any(), eq(refs), eq(financialPolicy.version()), eq(CORRELATION_ID));
    }

    @Test
    void incompleteFinancialEvidenceIsNotPresentedAsDeclaredIncomeConfirmation() {
        var refs = financialDocuments();
        when(documents.snapshot(proposalId)).thenReturn(refs);
        when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(UUID.randomUUID(), bureauReport("AVAILABLE")));
        when(python.process(any(), any(), anyList(), anyString(), anyString())).thenAnswer(invocation ->
                extraction(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        ref -> !"2026-08".equals(ref.period())));
        var scenario = new Scenario(BigDecimal.ZERO, 600, BigDecimal.ONE, "BRL", "SYNTHETIC");

        var dossier = service.analyze(proposalId, CORRELATION_ID, KEY, scenario);

        assertThat(dossier.status()).isEqualTo("COMPLETED");
        assertThat(dossier.financialAssessment().reviewStatus()).isEqualTo("MORE_INFO_REQUIRED");
        assertThat(dossier.financialAssessment().validIncomePeriods()).isEqualTo(2);
        assertThat(dossier.financialAssessment().averageNetIncome()).isEqualByComparingTo("8000.00");
        assertThat(dossier.financialAssessment().limitations()).contains("Net salary not factual for period 2026-08");
        assertThat(dossier.findings()).extracting(FindingView::code, FindingView::result, FindingView::evidenceRef)
                .contains(tuple("INCOME_CONSISTENCY", "NOT_EVALUATED", null));
    }

    @Test
    void normalizesRateScaleButChangesFingerprintForANewBureauConsultation() {
        var refs = financialDocuments();
        when(documents.snapshot(proposalId)).thenReturn(refs, refs.reversed());
        var older = new BureauService.Snapshot(UUID.randomUUID(), bureauReport("AVAILABLE"));
        var newer = new BureauService.Snapshot(UUID.randomUUID(), bureauReport("AVAILABLE"));
        when(bureau.latest(proposalId)).thenReturn(older, older, newer);
        successfulExtraction();
        var zeroRate = new Scenario(BigDecimal.ZERO, 1, BigDecimal.ZERO, "BRL", "SYNTHETIC");
        var scaledRate = new Scenario(BigDecimal.ZERO, 1, new BigDecimal("0.000"), "BRL", "SYNTHETIC");

        var first = service.analyze(proposalId, CORRELATION_ID, KEY, zeroRate);
        var repeated = service.analyze(proposalId, CORRELATION_ID, KEY, scaledRate);
        var changed = service.analyze(proposalId, CORRELATION_ID, null, zeroRate);

        assertThat(repeated).isEqualTo(first);
        assertThat(changed.analysisId()).isNotEqualTo(first.analysisId());
        assertThat(changed.documentSnapshotHash()).isEqualTo(first.documentSnapshotHash());
        assertThat(first.financialAssessment().simulation().requestedPayment()).isEqualByComparingTo("0.00");
        assertThat(first.financialAssessment().simulation().termMonths()).isEqualTo(1);
        assertThat(service.bureauConsultationId(first.analysisId())).isEqualTo(older.id());
        assertThat(service.bureauConsultationId(changed.analysisId())).isEqualTo(newer.id());
    }

    @Test
    void rejectsThreePeriodsThatDifferBetweenPayslipsAndStatements() {
        var refs = new ArrayList<>(financialDocuments());
        refs.set(5, document(DocumentType.BANK_STATEMENT, "2026-11", 1, "d"));
        when(documents.snapshot(proposalId)).thenReturn(refs);
        when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(UUID.randomUUID(), bureauReport("AVAILABLE")));

        assertThatThrownBy(() -> service.analyze(proposalId, CORRELATION_ID, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getReason()).contains("three matching payslip and bank statement periods");
                });
        assertThat(analyses).isEmpty();
        verifyNoInteractions(python);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "UNBOUND", "UNAVAILABLE"})
    void requiresAnAvailableBureauConsultationBoundToAnId(String status) {
        when(documents.snapshot(proposalId)).thenReturn(financialDocuments());
        if ("UNBOUND".equals(status)) {
            when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(null, bureauReport("AVAILABLE")));
        } else if ("UNAVAILABLE".equals(status)) {
            when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(UUID.randomUUID(), bureauReport(status)));
        }

        assertThatThrownBy(() -> service.analyze(proposalId, CORRELATION_ID, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).isEqualTo("A successful bureau consultation is required before financial analysis");
                });
        assertThat(analyses).isEmpty();
        verifyNoInteractions(python);
    }

    @ParameterizedTest
    @MethodSource("invalidScenarios")
    void rejectsInvalidLoanScenariosBeforePersistingOrProcessing(Scenario invalid) {
        when(documents.snapshot(proposalId)).thenReturn(financialDocuments());
        when(bureau.latest(proposalId)).thenReturn(new BureauService.Snapshot(UUID.randomUUID(), bureauReport("AVAILABLE")));

        assertThatThrownBy(() -> service.analyze(proposalId, CORRELATION_ID, null, invalid))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getReason()).isEqualTo("Invalid loan scenario");
                });
        assertThat(analyses).isEmpty();
        verifyNoInteractions(python);
    }

    static Stream<Scenario> invalidScenarios() {
        return Stream.of(
                new Scenario(INCOME, 0, BigDecimal.ZERO, "BRL", "SYNTHETIC"),
                new Scenario(INCOME, 601, BigDecimal.ZERO, "BRL", "SYNTHETIC"),
                new Scenario(INCOME, 12, null, "BRL", "SYNTHETIC"),
                new Scenario(INCOME, 12, new BigDecimal("-0.01"), "BRL", "SYNTHETIC"),
                new Scenario(INCOME, 12, new BigDecimal("1.01"), "BRL", "SYNTHETIC"),
                new Scenario(INCOME, 12, BigDecimal.ZERO, "USD", "SYNTHETIC"),
                new Scenario(new BigDecimal("-1"), 12, BigDecimal.ZERO, "BRL", "SYNTHETIC"));
    }

    private void successfulExtraction() {
        when(python.process(any(), any(), anyList(), anyString(), anyString())).thenAnswer(invocation ->
                extraction(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    private ProcessResponse extraction(UUID analysisId, UUID lease, List<DocumentSnapshotRef> refs) {
        return extraction(analysisId, lease, refs, ref -> true);
    }

    private ProcessResponse extraction(UUID analysisId, UUID lease, List<DocumentSnapshotRef> refs,
            Predicate<DocumentSnapshotRef> factualIncome) {
        var results = refs.stream().map(ref -> switch (ref.type()) {
            case INCOME_PROOF -> new DocumentResult(ref.id(), ref.version(),
                    List.of(field(ref, "monthlyIncome", "8000.00", "BRL")));
            case PAYSLIP -> new DocumentResult(ref.id(), ref.version(), List.of(
                    field(ref, "competence", ref.period(), null), field(ref, "grossSalary", "9000.00", "BRL"),
                    factualIncome.test(ref) ? field(ref, "netSalary", "8000.00", "BRL")
                            : new ExtractedField(ref.id(), ref.version(), "netSalary", null, null,
                                    "MISSING", null, null, null),
                    field(ref, "payrollDeduction", "1000.00", "BRL")));
            case BANK_STATEMENT -> new DocumentResult(ref.id(), ref.version(),
                    List.of(field(ref, "statementPeriod", ref.period(), null)),
                    List.of(new ExtractedTransaction(ref.id(), ref.version(), "tx1", ref.period() + "-10",
                            "CREDIT", "8000.00", "BRL", "SYNTHETIC SALARIO", "POSSIBLE_SALARY",
                            evidence(ref, "tx1"), 1, "Synthetic salary credit"),
                            new ExtractedTransaction(ref.id(), ref.version(), "tx2", ref.period() + "-11",
                                    "DEBIT", "1000.00", "BRL", "SYNTHETIC CONSIGNADO", "RECURRING_PAYMENT",
                                    evidence(ref, "tx2"), 1, "Synthetic payroll debit")));
            default -> new DocumentResult(ref.id(), ref.version(), List.of());
        }).toList();
        var contractVersion = refs.stream().anyMatch(ref -> ref.period() != null) ? "2.0" : "1.0";
        return new ProcessResponse(contractVersion, analysisId, lease, results);
    }

    private ExtractedField field(DocumentSnapshotRef ref, String name, String value, String currency) {
        return new ExtractedField(ref.id(), ref.version(), name, value, currency, "EXTRACTED",
                evidence(ref, name), 1, "Synthetic " + name);
    }

    private String evidence(DocumentSnapshotRef ref, String name) {
        return ref.id() + ":v" + ref.version() + ":p1:" + name;
    }

    private List<DocumentSnapshotRef> financialDocuments() {
        return List.of(
                document("00000000-0000-4000-8000-000000000001", DocumentType.PAYSLIP, "2026-08", 1, "a"),
                document("00000000-0000-4000-8000-000000000002", DocumentType.BANK_STATEMENT, "2026-08", 2, "b"),
                document("00000000-0000-4000-8000-000000000003", DocumentType.PAYSLIP, "2026-09", 1, "a"),
                document("00000000-0000-4000-8000-000000000004", DocumentType.BANK_STATEMENT, "2026-09", 2, "b"),
                document("00000000-0000-4000-8000-000000000005", DocumentType.PAYSLIP, "2026-10", 1, "a"),
                document("00000000-0000-4000-8000-000000000006", DocumentType.BANK_STATEMENT, "2026-10", 2, "b"));
    }

    private DocumentSnapshotRef document(DocumentType type, String period, int version, String digest) {
        return document(UUID.randomUUID().toString(), type, period, version, digest);
    }

    private DocumentSnapshotRef document(String documentId, DocumentType type, String period, int version, String digest) {
        var id = UUID.fromString(documentId);
        return new DocumentSnapshotRef(id, proposalId, type, version, "synthetic/" + id + ".pdf",
                digest.repeat(64), period);
    }

    private BureauReport bureauReport(String status) {
        return new BureauReport("MOCK", "CREDIT_BUREAU", "MOCK", status, OffsetDateTime.now(ZoneOffset.UTC),
                "1.0", "synthetic-reference", new BureauReport.Score(700, 0, 1000, "DEMO"),
                new BureauReport.Restrictions(0), List.of());
    }
}
