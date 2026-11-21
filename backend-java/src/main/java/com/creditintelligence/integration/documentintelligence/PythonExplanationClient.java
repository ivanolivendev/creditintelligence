package com.creditintelligence.integration.documentintelligence;

import java.util.List;
import java.util.UUID;
import com.creditintelligence.analysis.domain.FinancialEngine.Assessment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonExplanationClient {
    private final RestClient client;
    private final String token;

    public PythonExplanationClient(@Value("${document.intelligence.url}") String url,
            @Value("${document.intelligence.token}") String token) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(15000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
        this.token = token;
    }

    public ExplainResponse explain(ExplainRequest request) {
        return client.post().uri("/internal/v1/explain")
                .header("X-Internal-Token", token).body(request)
                .retrieve().body(ExplainResponse.class);
    }

    public record FindingContext(String code, String result, String details, String evidenceRef) {}
    public record ExplainRequest(String contractVersion, UUID analysisId, String policyVersion,
            String policyChecksum, String policyText, List<FindingContext> findings,
            List<String> allowedEvidenceRefs, List<String> allowedPolicyRefs, String correlationId,
            Assessment assessment, List<String> allowedTransactionRefs) {}
    public record FindingExplanation(String findingCode, String message,
            List<String> policyReferences, List<String> evidenceReferences) {}
    public record Hypothesis(String type, String description, List<String> transactionRefs,
            List<String> evidenceRefs, String reason, String reviewStatus) {}
    public record ExplainResponse(String contractVersion, UUID analysisId, String provider,
            String modelId, String promptVersion, String summary,
            List<FindingExplanation> explanations, List<Hypothesis> hypotheses,
            List<String> limitations) {
        public ExplainResponse(String contractVersion, UUID analysisId, String provider,
                String modelId, String promptVersion, String summary,
                List<FindingExplanation> explanations, List<String> limitations) {
            this(contractVersion, analysisId, provider, modelId, promptVersion, summary,
                    explanations, List.of(), limitations);
        }
    }
}
