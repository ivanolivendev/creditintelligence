package com.creditintelligence.integration.documentintelligence;

import com.creditintelligence.document.application.DocumentSnapshotRef;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonDocumentClient {
    private final RestClient client;
    private final String token;

    public PythonDocumentClient(@Value("${document.intelligence.url}") String url,
            @Value("${document.intelligence.token}") String token) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(15000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
        this.token = token;
    }

    public ProcessResponse process(UUID analysisId, UUID attemptId, List<DocumentSnapshotRef> documents,
            String policyVersion, String correlationId) {
        var refs = documents.stream().map(document -> new DocumentRef(document.id(), document.version(),
                document.type().name(), document.objectKey(), document.sha256(), document.period())).toList();
        var financial = documents.stream().anyMatch(document -> document.period() != null);
        var request = new ProcessRequest(financial ? "2.0" : "1.0", analysisId, attemptId,
                refs, policyVersion, correlationId);
        return client.post().uri("/internal/v1/process-documents")
                .header("X-Internal-Token", token)
                .body(request)
                .retrieve().body(ProcessResponse.class);
    }

    public record DocumentRef(UUID documentId, int version, String type, String objectKey,
            String sha256, String period) {}
    public record ProcessRequest(String contractVersion, UUID analysisId, UUID attemptId,
            List<DocumentRef> documentRefs, String policyVersion, String correlationId) {}
    public record ExtractedField(UUID documentId, int version, String fieldName, String value,
            String currency, String extractionStatus, String evidenceRef, Integer page, String snippet) {}
    public record ExtractedTransaction(UUID documentId, int version, String transactionId,
            String postedAt, String direction, String amount, String currency,
            String descriptionOriginal, String classification, String evidenceRef,
            Integer page, String snippet) {}
    public record DocumentResult(UUID documentId, int version, List<ExtractedField> fields,
            List<ExtractedTransaction> transactions) {
        public DocumentResult(UUID documentId, int version, List<ExtractedField> fields) {
            this(documentId, version, fields, List.of());
        }
    }
    public record ProcessResponse(String contractVersion, UUID analysisId, UUID attemptId,
            List<DocumentResult> documentResults) {}
}
