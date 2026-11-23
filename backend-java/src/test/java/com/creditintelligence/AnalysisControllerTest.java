package com.creditintelligence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.analysis.api.AnalysisController;
import com.creditintelligence.analysis.application.AIExplanationView;
import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.analysis.application.AnalysisView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalysisControllerTest {
    private final AnalysisService service = mock(AnalysisService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AnalysisController(service, new ObjectMapper()))
            .setControllerAdvice(new com.creditintelligence.shared.errors.ApiExceptionHandler())
            .build();

    @Test
    void malformedLoanScenarioReturnsProblemDetailsWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/proposals/{id}/analyses", UUID.randomUUID())
                .requestAttr("correlationId", "corr-1")
                .contentType("application/json")
                .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void validScenarioIsDeserializedAndDelegated() throws Exception {
        var proposalId = UUID.randomUUID();
        var view = new AnalysisView(UUID.randomUUID(), proposalId, "RUNNING", 1, "a".repeat(64),
                "CREDIT_POLICY_DEMO_3.4", "checksum", null, "BRL", null, List.of(), List.of(), List.of(),
                List.of(), "NOT_REQUESTED", AIExplanationView.withoutContent("NOT_REQUESTED"), null, null, null, null);
        when(service.analyze(eq(proposalId), eq("corr-1"), eq("key-1234"), any())).thenReturn(view);

        mvc.perform(post("/api/v1/proposals/{id}/analyses", proposalId)
                .requestAttr("correlationId", "corr-1")
                .header("Idempotency-Key", "key-1234")
                .contentType("application/json")
                .content("{\"principalRequested\":1000,\"termMonths\":12,"
                        + "\"monthlyInterestRate\":0.02,\"currency\":\"BRL\",\"pricingSource\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/analyses/%s", "/dossier?analysisId=%s"})
    void returnsTheRequestedAnalysisAndItsSnapshotByEitherReadRoute(String suffix) throws Exception {
        var analysisId = UUID.randomUUID();
        var proposalId = UUID.randomUUID();
        var snapshotHash = "a".repeat(64);
        var view = new AnalysisView(analysisId, proposalId, "COMPLETED", 2, snapshotHash,
                "CREDIT_POLICY_DEMO_3.4", "checksum", null, "BRL", null, List.of(), List.of(), List.of(),
                List.of(), "NOT_REQUESTED", AIExplanationView.withoutContent("NOT_REQUESTED"), null, null, null, null);
        when(service.dossier(proposalId, analysisId)).thenReturn(view);

        mvc.perform(get("/api/v1/proposals/" + proposalId + suffix.formatted(analysisId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.documentSnapshotHash").value(snapshotHash))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
