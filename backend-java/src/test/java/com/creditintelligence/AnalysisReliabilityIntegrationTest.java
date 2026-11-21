package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisReliabilityIntegrationTest {
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "analyst:local_demo_only_change_me".getBytes(StandardCharsets.US_ASCII));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PythonDocumentClient python;
    @MockitoBean PythonExplanationClient explanations;

    @Test
    void sameKeyReturnsSameAnalysisAndChangedInputConflicts() throws Exception {
        var id = createProposal();
        validExtraction();
        var first = analyze(id, "request-key-001");
        var second = analyze(id, "request-key-001");
        assertThat(second.path("analysisId").asText()).isEqualTo(first.path("analysisId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis WHERE proposal_id = ?",
                Integer.class, id)).isEqualTo(1);
        jdbc.update("UPDATE proposal SET declared_monthly_income = 9000.00 WHERE id = ?", id);
        mvc.perform(post("/api/v1/proposals/{id}/analyses", id)
                .header("Authorization", AUTH).header("Idempotency-Key", "request-key-001"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis WHERE proposal_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void retryUsesNewLeaseAndLateAttemptCannotOverwrite() throws Exception {
        var id = createProposal();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown();
                        if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                    }
                    return new PythonDocumentClient.ProcessResponse("1.0", invocation.getArgument(0),
                            invocation.getArgument(1), List.of());
                });
        var first = CompletableFuture.supplyAsync(() -> {
            try { return analyze(id, "request-key-002"); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            jdbc.update("UPDATE analysis SET started_at = now() - interval '120 seconds' WHERE proposal_id = ?", id);
            var second = analyze(id, "request-key-002");
            assertThat(second.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(second.path("attempt").asInt()).isEqualTo(2);
        } finally {
            release.countDown();
        }
        var late = first.get(20, TimeUnit.SECONDS);
        assertThat(late.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(late.path("attempt").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM validation_finding WHERE analysis_id = ?",
                Integer.class, UUID.fromString(late.path("analysisId").asText()))).isEqualTo(2);
    }

    @Test
    void simultaneousRequestsWithSameKeyShareOneAnalysis() throws Exception {
        var id = createProposal();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                    return new PythonDocumentClient.ProcessResponse("1.0", invocation.getArgument(0),
                            invocation.getArgument(1), List.of());
                });
        var first = CompletableFuture.supplyAsync(() -> {
            try { return analyze(id, "request-key-004"); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            var concurrent = analyze(id, "request-key-004");
            assertThat(concurrent.path("status").asText()).isEqualTo("RUNNING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis WHERE proposal_id = ?",
                    Integer.class, id)).isEqualTo(1);
        } finally {
            release.countDown();
        }
        assertThat(first.get(20, TimeUnit.SECONDS).path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void retryablePythonFailureCanBeRetried() throws Exception {
        var id = createProposal();
        var calls = new AtomicInteger();
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) throw new ResourceAccessException("synthetic outage");
                    return new PythonDocumentClient.ProcessResponse("1.0", invocation.getArgument(0),
                            invocation.getArgument(1), List.of());
                });
        var failed = analyze(id, "request-key-003");
        assertThat(failed.path("status").asText()).isEqualTo("FAILED_RETRYABLE");
        var recovered = analyze(id, "request-key-003");
        assertThat(recovered.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(recovered.path("analysisId").asText()).isEqualTo(failed.path("analysisId").asText());
        assertThat(recovered.path("attempt").asInt()).isEqualTo(2);
    }

    private UUID createProposal() throws Exception {
        var payload = "{\"applicant\":{\"name\":\"Demo\",\"syntheticIdentifier\":\"DEMO-"
                + UUID.randomUUID().toString().substring(0, 12)
                + "\"},\"declaredMonthlyIncome\":8000.00,\"currency\":\"BRL\"}";
        var response = mvc.perform(post("/api/v1/proposals").header("Authorization", AUTH)
                .contentType("application/json").content(payload)).andExpect(status().isCreated())
                .andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).path("id").asText());
    }

    private JsonNode analyze(UUID id, String key) throws Exception {
        var response = mvc.perform(post("/api/v1/proposals/{id}/analyses", id)
                .header("Authorization", AUTH).header("Idempotency-Key", key))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private void validExtraction() {
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> new PythonDocumentClient.ProcessResponse("1.0",
                        invocation.getArgument(0), invocation.getArgument(1), List.of()));
    }
}
