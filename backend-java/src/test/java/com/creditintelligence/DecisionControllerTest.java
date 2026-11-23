package com.creditintelligence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.decision.api.DecisionController;
import com.creditintelligence.decision.application.DecisionService;
import com.creditintelligence.decision.application.DecisionView;
import com.creditintelligence.decision.domain.DecisionOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DecisionControllerTest {
    private final DecisionService service = mock(DecisionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DecisionController(service))
            .setControllerAdvice(new com.creditintelligence.shared.errors.ApiExceptionHandler())
            .build();

    @Test
    void createsDecisionWithLocationAndCorrelationAttribute() throws Exception {
        var proposalId = UUID.randomUUID();
        var decision = decision(proposalId, DecisionOutcome.APPROVED, "Approved");
        when(service.decide(eq(proposalId), any(), any(), any(), any(), eq("corr-1"))).thenReturn(decision);

        mvc.perform(post("/api/v1/proposals/{id}/decisions", proposalId)
                .requestAttr("correlationId", "corr-1")
                .contentType("application/json")
                .content("{\"analysisId\":\"" + decision.analysisId() + "\","
                        + "\"documentSnapshotHash\":\"" + "a".repeat(64) + "\","
                        + "\"outcome\":\"APPROVED\",\"reason\":\"Approved\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/proposals/" + proposalId + "/decisions/" + decision.id()))
                .andExpect(jsonPath("$.outcome").value("APPROVED"));
    }

    @Test
    void listsDecisionViewsFromService() throws Exception {
        var proposalId = UUID.randomUUID();
        when(service.list(proposalId)).thenReturn(List.of(decision(proposalId, DecisionOutcome.REJECTED, "Rejected")));

        mvc.perform(get("/api/v1/proposals/{id}/decisions", proposalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$[0].reason").value("Rejected"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void rejectsBlankReasonWithoutInvokingDecisionService(String reason) throws Exception {
        var proposalId = UUID.randomUUID();
        mvc.perform(post("/api/v1/proposals/{id}/decisions", proposalId)
                .requestAttr("correlationId", "corr-1")
                .contentType("application/json")
                .content("{\"analysisId\":\"" + UUID.randomUUID() + "\","
                        + "\"documentSnapshotHash\":\"" + "a".repeat(64) + "\","
                        + "\"outcome\":\"APPROVED\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private DecisionView decision(UUID proposalId, DecisionOutcome outcome, String reason) {
        return new DecisionView(UUID.randomUUID(), proposalId, UUID.randomUUID(), "analyst", outcome, reason,
                "a".repeat(64), OffsetDateTime.parse("2026-11-21T00:00:00Z"));
    }
}
