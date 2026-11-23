package com.creditintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.creditintelligence.analysis.application.AIExplanationService;
import com.creditintelligence.analysis.application.AIExplanationView;
import com.creditintelligence.analysis.application.AnalysisDataPort;
import com.creditintelligence.analysis.application.FieldView;
import com.creditintelligence.analysis.application.FindingView;
import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.analysis.domain.FinancialEngine.FinancialProfile;
import com.creditintelligence.analysis.domain.FinancialEngine.IncomePeriod;
import com.creditintelligence.analysis.domain.FinancialEngine.Policy;
import com.creditintelligence.analysis.domain.FinancialEngine.Scenario;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplainRequest;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplainResponse;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.FindingExplanation;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.Hypothesis;
import com.creditintelligence.policy.application.PolicySnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestClientException;

class AIExplanationServiceTest {
    private final PythonExplanationClient python = mock(PythonExplanationClient.class);
    private final AnalysisDataPort data = mock(AnalysisDataPort.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AIExplanationService service = new AIExplanationService(
            python, data, jdbc, json, "mock");
    private final UUID analysisId = UUID.randomUUID();
    private final PolicySnapshot policy = new PolicySnapshot(UUID.randomUUID(), "POLICY-1.0", "checksum",
            "POLICY-1.0: Synthetic", new BigDecimal("5.00"), List.of("INCOME_PROOF"));

    @BeforeEach
    void context() {
        when(data.findings(analysisId)).thenReturn(List.of(
                new FindingView("INCOME_CONSISTENCY", "1.0", "WARNING", "Synthetic variance", null)));
        when(data.fields(analysisId)).thenReturn(List.of());
    }

    @Test
    void inventedPolicyReferenceBecomesInvalidOutput() {
        when(python.explain(any())).thenReturn(new ExplainResponse("1.0", analysisId, "MOCK",
                "deterministic-demo-v1", "1.0", "Synthetic explanation",
                List.of(new FindingExplanation("INCOME_CONSISTENCY", "Review required",
                        List.of("POLICY-1.0:INVENTED"), List.of())), List.of("Demo only")));
        service.generate(analysisId, policy, "test");
        verify(jdbc).update(anyString(), eq(analysisId), eq("INVALID_OUTPUT"),
                isNull(), isNull(), isNull(), isNull(), any(OffsetDateTime.class), eq(1),
                eq("INVALID_EXPLANATION_CONTRACT"), anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void providerFailureBecomesUnavailable() {
        when(python.explain(any())).thenThrow(new RestClientException("synthetic outage"));
        service.generate(analysisId, policy, "test");
        verify(jdbc).update(anyString(), eq(analysisId), eq("UNAVAILABLE"),
                isNull(), isNull(), isNull(), isNull(), any(OffsetDateTime.class), eq(3),
                eq("DOCUMENT_INTELLIGENCE_UNAVAILABLE"), anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void retryableProviderFailureIsRetriedAndSuccessfulResponseIsPersisted() {
        var response = new ExplainResponse("1.0", analysisId, "MOCK", "deterministic-demo-v1", "1.0",
                "Synthetic explanation", List.of(new FindingExplanation("INCOME_CONSISTENCY",
                "Review required", List.of("POLICY-1.0:INCOME_CONSISTENCY"), List.of())), List.of("Demo only"));
        when(python.explain(any()))
                .thenThrow(new PythonExplanationClient.ExplanationException("TEMPORARY", true,
                        "synthetic outage", new RuntimeException()))
                .thenReturn(response);

        service.generate(analysisId, policy, "test");

        verify(python, times(2)).explain(any());
        verify(jdbc).update(anyString(), eq(analysisId), eq("AVAILABLE"), eq("MOCK"),
                eq("deterministic-demo-v1"), eq("1.0"), anyString(), any(OffsetDateTime.class), eq(2),
                isNull(), anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void duplicateFindingCodeIsRejectedAsInvalidModelOutput() {
        when(data.findings(analysisId)).thenReturn(List.of(
                new FindingView("INCOME_CONSISTENCY", "1.0", "WARNING", "Synthetic variance", null),
                new FindingView("REQUIRED_DOCUMENTS", "1.0", "PASS", "All present", null)));
        var duplicate = new FindingExplanation("INCOME_CONSISTENCY", "Review required",
                List.of("POLICY-1.0:INCOME_CONSISTENCY"), List.of());
        when(python.explain(any())).thenReturn(new ExplainResponse("1.0", analysisId, "MOCK",
                "deterministic-demo-v1", "1.0", "Synthetic explanation",
                List.of(duplicate, duplicate), List.of("Demo only")));

        service.generate(analysisId, policy, "test");

        verify(jdbc).update(anyString(), eq(analysisId), eq("INVALID_OUTPUT"), isNull(), isNull(),
                isNull(), isNull(), any(OffsetDateTime.class), eq(1),
                eq("INVALID_EXPLANATION_CONTRACT"), anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void wrongProviderAndAnalysisIdAreRejectedBeforePersistence() {
        when(python.explain(any())).thenReturn(new ExplainResponse("1.0", UUID.randomUUID(), "BEDROCK",
                "model", "1.0", "Summary", List.of(new FindingExplanation("INCOME_CONSISTENCY", "Review",
                        List.of("POLICY-1.0:INCOME_CONSISTENCY"), List.of())), List.of("Demo")));

        service.generate(analysisId, policy, "test");

        verify(jdbc).update(anyString(), eq(analysisId), eq("INVALID_OUTPUT"), isNull(), isNull(), isNull(),
                isNull(), any(OffsetDateTime.class), eq(1), eq("INVALID_EXPLANATION_CONTRACT"), anyLong(),
                any(OffsetDateTime.class));
    }

    @Test
    void unsupportedHypothesisReferencesAreRejected() {
        var hypothesis = new PythonExplanationClient.Hypothesis("RISK", "Synthetic hypothesis",
                List.of("transaction-not-allowed"), List.of(), "Needs human review", "PENDING_HUMAN_REVIEW");
        when(python.explain(any())).thenReturn(new ExplainResponse("1.0", analysisId, "MOCK",
                "deterministic-demo-v1", "1.0", "Synthetic explanation",
                List.of(new FindingExplanation("INCOME_CONSISTENCY", "Review required",
                        List.of("POLICY-1.0:INCOME_CONSISTENCY"), List.of())), List.of(hypothesis), List.of("Demo")));

        service.generate(analysisId, policy, "test");

        verify(jdbc).update(anyString(), eq(analysisId), eq("INVALID_OUTPUT"), isNull(), isNull(), isNull(),
                isNull(), any(OffsetDateTime.class), eq(1), eq("INVALID_EXPLANATION_CONTRACT"), anyLong(),
                any(OffsetDateTime.class));
    }

    @Test
    void missingFindingReferenceIsRejected() {
        when(python.explain(any())).thenReturn(new ExplainResponse("1.0", analysisId, "MOCK",
                "deterministic-demo-v1", "1.0", "Synthetic explanation",
                List.of(new FindingExplanation("UNKNOWN_FINDING", "Review required", List.of(), List.of())),
                List.of("Demo")));

        service.generate(analysisId, policy, "test");

        verify(jdbc).update(anyString(), eq(analysisId), eq("INVALID_OUTPUT"), isNull(), isNull(), isNull(),
                isNull(), any(OffsetDateTime.class), eq(1), eq("INVALID_EXPLANATION_CONTRACT"), anyLong(),
                any(OffsetDateTime.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNRECOGNIZED"})
    void rejectsAnInvalidProviderWithOtherwiseValidOutput(String provider) {
        assertInvalid(response("1.0", provider, "model", "1.0", "Summary"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingModelId(String modelId) {
        assertInvalid(response("1.0", "MOCK", modelId, "1.0", "Summary"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingPromptVersion(String promptVersion) {
        assertInvalid(response("1.0", "MOCK", "model", promptVersion, "Summary"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingSummary(String summary) {
        assertInvalid(response("1.0", "MOCK", "model", "1.0", summary));
    }

    @Test
    void rejectsUnsupportedContractVersion() {
        assertInvalid(response("2.0", "MOCK", "model", "1.0", "Summary"));
    }

    @Test
    void rejectsNullResponse() {
        assertInvalid(null);
    }

    @Test
    void rejectsAResponseWithNoExplanationForTheFinding() {
        assertInvalid(new ExplainResponse("1.0", analysisId, "MOCK", "model", "1.0", "Summary",
                List.of(), List.of("Demo")));
    }

    @Test
    void rejectsInventedEvidenceInFindingExplanation() {
        assertInvalid(new ExplainResponse("1.0", analysisId, "MOCK", "model", "1.0", "Summary",
                List.of(new FindingExplanation("INCOME_CONSISTENCY", "Review required", List.of(),
                        List.of("evidence-not-provided"))), List.of("Demo")));
    }

    @Test
    void rejectsHypothesisClaimedToBeAlreadyReviewed() {
        assertInvalid(responseWithHypothesis(new Hypothesis("RISK", "Synthetic hypothesis", List.of(), List.of(),
                "Needs review", "APPROVED")));
    }

    @Test
    void rejectsInventedEvidenceInHypothesis() {
        assertInvalid(responseWithHypothesis(new Hypothesis("RISK", "Synthetic hypothesis", List.of(),
                List.of("evidence-not-provided"), "Needs review", "PENDING_HUMAN_REVIEW")));
    }

    @Test
    void configuredBedrockProviderRejectsMockBeforePersistence() {
        var bedrockService = new AIExplanationService(python, data, jdbc, json);
        when(python.explain(any())).thenReturn(response("1.0", "MOCK", "model", "1.0", "Summary"));

        bedrockService.generate(analysisId, policy, "test");

        verifyInvalidOutput();
    }

    @Test
    void acceptsEvidenceAndTransactionsFromContextAndPersistsTheResponse() throws Exception {
        var evidence = "synthetic-document:income:1";
        var assessmentEvidence = "synthetic-payslip:net-income:1";
        var transaction = "synthetic-transaction:001";
        when(data.fields(analysisId)).thenReturn(List.of(
                new FieldView(UUID.randomUUID(), 1, "income", "7500", "BRL", "EXTRACTED", evidence, 1, "income"),
                new FieldView(UUID.randomUUID(), 1, "income", "7500", "BRL", "EXTRACTED", evidence, 1, "income"),
                new FieldView(UUID.randomUUID(), 1, "missing", null, null, "MISSING", null, null, null)));
        var assessment = new FinancialEngine().assess(new FinancialProfile(List.of(new IncomePeriod("2026-09",
                        null, new BigDecimal("7500"), null, assessmentEvidence, null)), List.of(), List.of()),
                new Policy(new BigDecimal("0.30"), new BigDecimal("3.00"), new BigDecimal("0.10"), policy.version()),
                new Scenario(null, 12, BigDecimal.ZERO, "BRL", "SYNTHETIC"));
        when(data.financialAssessment(analysisId)).thenReturn(assessment);
        when(data.transactionReferences(analysisId)).thenReturn(List.of(transaction));
        var response = responseWithHypothesis(new Hypothesis("RISK", "Synthetic hypothesis", List.of(transaction),
                List.of(evidence, assessmentEvidence), "Needs review", "PENDING_HUMAN_REVIEW"));
        when(python.explain(any())).thenReturn(response);

        service.generate(analysisId, policy, "corr-synthetic");

        var request = ArgumentCaptor.forClass(ExplainRequest.class);
        verify(python).explain(request.capture());
        assertEquals(new ExplainRequest("1.0", analysisId, policy.version(), policy.checksum(), policy.text(),
                List.of(new PythonExplanationClient.FindingContext("INCOME_CONSISTENCY", "WARNING",
                        "Synthetic variance", null)), List.of(evidence, assessmentEvidence),
                List.of("POLICY-1.0:INCOME_CONSISTENCY"), "corr-synthetic", assessment, List.of(transaction)),
                request.getValue());
        var stored = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), eq(analysisId), eq("AVAILABLE"), eq("MOCK"), eq("model"), eq("1.0"),
                stored.capture(), any(OffsetDateTime.class), eq(1), isNull(), anyLong(), any(OffsetDateTime.class));
        assertEquals(response, json.readValue(stored.getValue(), ExplainResponse.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void persistsFailureCodeAndActualAttemptCount(boolean retryable) {
        when(python.explain(any())).thenThrow(new PythonExplanationClient.ExplanationException(
                "DOCUMENT_INTELLIGENCE_503", retryable, "synthetic outage", new RuntimeException()));

        long started = System.nanoTime();
        service.generate(analysisId, policy, "test");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        int attempts = retryable ? 3 : 1;
        var duration = ArgumentCaptor.forClass(Long.class);
        verify(python, times(attempts)).explain(any());
        verify(jdbc).update(anyString(), eq(analysisId), eq("UNAVAILABLE"), isNull(), isNull(), isNull(),
                isNull(), any(OffsetDateTime.class), eq(attempts), eq("DOCUMENT_INTELLIGENCE_503"), duration.capture(),
                any(OffsetDateTime.class));
        assertTrue(duration.getValue() <= elapsed, "Persisted duration must not exceed the whole call");
        if (retryable) {
            assertTrue(duration.getValue() >= 700, "The two backoffs must be included in the duration");
        }
    }

    @Test
    void interruptedRetryRestoresTheInterruptAndDoesNotDispatchAgain() {
        when(python.explain(any())).thenAnswer(call -> {
            if (!Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            throw new PythonExplanationClient.ExplanationException("TEMPORARY", true, "Synthetic outage", null);
        });

        try {
            var error = assertThrows(IllegalStateException.class, () -> service.generate(analysisId, policy, "test"));
            assertInstanceOf(InterruptedException.class, error.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        verify(python).explain(any());
        verifyNoInteractions(jdbc);
    }

    @Test
    void returnsNotRequestedWhenNoExplanationWasStored() {
        when(jdbc.queryForObject(anyString(), ArgumentMatchers.<RowMapper<AIExplanationView>>any(), eq(analysisId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertEquals(AIExplanationView.withoutContent("NOT_REQUESTED"), service.get(analysisId));
    }

    @Test
    void readsStoredExplanationAndKeepsMetadataFromTheRow() throws Exception {
        var response = response("1.0", "MOCK", "model", "1.0", "Summary");
        storedRow("AVAILABLE", json.writeValueAsString(response));

        assertEquals(new AIExplanationView("AVAILABLE", "BEDROCK", "stored-model", "stored-prompt",
                response.summary(), response.explanations(), response.hypotheses(), response.limitations()),
                service.get(analysisId));
    }

    @Test
    void invalidStoredJsonIsReportedAsPersistenceFailure() throws Exception {
        storedRow("AVAILABLE", "{invalid");

        var failure = assertThrows(IllegalStateException.class, () -> service.get(analysisId));
        assertEquals("Stored explanation cannot be read", failure.getMessage());
        assertInstanceOf(JsonProcessingException.class, failure.getCause());
    }

    @Test
    void unavailableRowDoesNotExposeContent() throws Exception {
        storedRow("UNAVAILABLE", "{invalid");

        assertEquals(AIExplanationView.withoutContent("UNAVAILABLE"), service.get(analysisId));
    }

    private ExplainResponse response(String contract, String provider, String modelId, String promptVersion,
            String summary) {
        return new ExplainResponse(contract, analysisId, provider, modelId, promptVersion, summary,
                List.of(new FindingExplanation("INCOME_CONSISTENCY", "Review required",
                        List.of("POLICY-1.0:INCOME_CONSISTENCY"), List.of())), List.of("Demo"));
    }

    private ExplainResponse responseWithHypothesis(Hypothesis hypothesis) {
        var response = response("1.0", "MOCK", "model", "1.0", "Summary");
        return new ExplainResponse("1.0", analysisId, "MOCK", "model", "1.0", "Summary",
                response.explanations(), List.of(hypothesis), response.limitations());
    }

    private void assertInvalid(ExplainResponse response) {
        when(python.explain(any())).thenReturn(response);
        service.generate(analysisId, policy, "test");
        verifyInvalidOutput();
    }

    private void verifyInvalidOutput() {
        verify(python).explain(any());
        verify(jdbc).update(anyString(), eq(analysisId), eq("INVALID_OUTPUT"),
                isNull(), isNull(), isNull(), isNull(), any(OffsetDateTime.class), eq(1),
                eq("INVALID_EXPLANATION_CONTRACT"), anyLong(), any(OffsetDateTime.class));
    }

    private void storedRow(String status, String content) throws Exception {
        var row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn(status);
        when(row.getString(2)).thenReturn("BEDROCK");
        when(row.getString(3)).thenReturn("stored-model");
        when(row.getString(4)).thenReturn("stored-prompt");
        when(row.getString(5)).thenReturn(content);
        doAnswer(call -> call.<RowMapper<AIExplanationView>>getArgument(1).mapRow(row, 0))
                .when(jdbc).queryForObject(anyString(), ArgumentMatchers.<RowMapper<AIExplanationView>>any(),
                        eq(analysisId));
    }
}
