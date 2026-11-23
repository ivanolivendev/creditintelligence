package com.creditintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.DocumentRef;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.DocumentResult;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ProcessRequest;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ProcessResponse;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplainRequest;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplainResponse;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplanationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientException;

class PythonClientsTest {
    private static final String TOKEN = "synthetic-test-token";
    private final ObjectMapper json = new ObjectMapper();
    private final UUID analysisId = UUID.randomUUID();
    private final AtomicReference<Request> observed = new AtomicReference<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;

    @BeforeEach
    void bindLocalStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void closeLocalStub() {
        release.countDown();
        server.stop(0);
    }

    @ParameterizedTest
    @CsvSource({"1.0,", "2.0,2026-09"})
    void processBindsExactDocumentSnapshotAndSelectsContractByPeriod(String version, String period) throws Exception {
        var attemptId = UUID.randomUUID();
        var document = new DocumentSnapshotRef(UUID.randomUUID(), UUID.randomUUID(), DocumentType.INCOME_PROOF,
                3, "synthetic/input.pdf", "synthetic-hash", period);
        var result = new ProcessResponse(version, analysisId, attemptId,
                List.of(new DocumentResult(document.id(), document.version(), List.of(), List.of())));
        respond(200, json.writeValueAsString(result));
        var client = new PythonDocumentClient(url(), TOKEN, 1000, 1000);

        assertEquals(result, client.process(analysisId, attemptId, List.of(document), "POLICY-1.0", "corr-123"));

        var sent = assertRequest("/internal/v1/process-documents", ProcessRequest.class);
        assertEquals(new ProcessRequest(version, analysisId, attemptId,
                List.of(new DocumentRef(document.id(), document.version(), document.type().name(),
                        document.objectKey(), document.sha256(), period)), "POLICY-1.0", "corr-123"), sent);
    }

    @Test
    void explainPreservesInputScopeAndDeserializesProviderMetadata() throws Exception {
        var expected = new ExplainResponse("1.0", analysisId, "MOCK", "synthetic-model", "1.0", "Summary",
                List.of(), List.of("Synthetic assessment"));
        respond(200, json.writeValueAsString(expected));
        var client = new PythonExplanationClient(url(), TOKEN, 1000, 1000);
        var request = explanationRequest();

        assertEquals(expected, client.explain(request));
        assertEquals(request, assertRequest("/internal/v1/explain", ExplainRequest.class));
    }

    @ParameterizedTest
    @CsvSource({"403,false", "503,true"})
    void explainClassifiesClientAndServerErrors(int status, boolean retryable) throws Exception {
        var detail = "{\"detail\":\"Synthetic provider failure\"}";
        respond(status, detail);
        var client = new PythonExplanationClient(url(), TOKEN, 1000, 1000);

        var failure = assertThrows(ExplanationException.class, () -> client.explain(explanationRequest()));

        assertEquals("DOCUMENT_INTELLIGENCE_" + status, failure.code());
        assertEquals(retryable, failure.retryable());
        assertEquals(detail, failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failsWithReadTimeoutBeforeAnUnresponsiveProviderEventuallyReturns(boolean explanation) throws Exception {
        var attemptId = UUID.randomUUID();
        var response = explanation
                ? new ExplainResponse("1.0", analysisId, "MOCK", "synthetic-model", "1.0", "Summary",
                        List.of(), List.of())
                : new ProcessResponse("1.0", analysisId, attemptId, List.of());
        respond(200, json.writeValueAsString(response), 800);

        var failure = assertThrows(RestClientException.class, () -> {
            if (explanation) {
                new PythonExplanationClient(url(), TOKEN, 1000, 200).explain(explanationRequest());
            } else {
                new PythonDocumentClient(url(), TOKEN, 1000, 200)
                        .process(analysisId, attemptId, List.of(), "POLICY-1.0", "corr-123");
            }
        });

        assertInstanceOf(SocketTimeoutException.class, failure.getCause());
    }

    private void respond(int status, String jsonBody) {
        respond(status, jsonBody, 0);
    }

    private void respond(int status, String jsonBody, long delayMillis) {
        server.createContext("/", exchange -> {
            try (exchange) {
                observed.set(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                if (delayMillis > 0 && release.await(delayMillis, TimeUnit.MILLISECONDS)) {
                    return;
                }
                var bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
    }

    private <T> T assertRequest(String path, Class<T> bodyType) throws Exception {
        var request = observed.get();
        assertEquals("POST", request.method());
        assertEquals(path, request.path());
        assertEquals(TOKEN, request.token());
        return json.readValue(request.body(), bodyType);
    }

    private ExplainRequest explanationRequest() {
        return new ExplainRequest("1.0", analysisId, "POLICY-1.0", "synthetic-checksum", "Synthetic policy",
                List.of(), List.of("evidence:1"), List.of("POLICY-1.0:INCOME"), "corr-123", null, List.of("tx:1"));
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private record Request(String method, String path, String token, String body) {}
}
