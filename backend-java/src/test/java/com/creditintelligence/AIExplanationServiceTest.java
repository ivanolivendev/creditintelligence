package com.creditintelligence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.creditintelligence.analysis.application.AIExplanationService;
import com.creditintelligence.analysis.application.FindingView;
import com.creditintelligence.analysis.infrastructure.AnalysisDataStore;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplainResponse;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.FindingExplanation;
import com.creditintelligence.policy.application.PolicySnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClientException;

class AIExplanationServiceTest {
    private final PythonExplanationClient python = mock(PythonExplanationClient.class);
    private final AnalysisDataStore data = mock(AnalysisDataStore.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AIExplanationService service = new AIExplanationService(python, data, jdbc, new ObjectMapper());
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
}
