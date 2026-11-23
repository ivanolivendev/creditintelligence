package com.creditintelligence.bureau.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.creditintelligence.bureau.application.CreditBureauPort.BureauQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConsultCenterSandboxAdapterTest {
    private static final String VALID_REPORT = """
            {"consulta":{"produto":"credit_bureau","consultado_em":"2026-11-20T10:00:00Z"},
             "dados":{"score":{"pontuacao":640,"descricao_scoring_escolhido":"DEMO"}},"avisos":[]}
            """;
    private final ObjectMapper json = new ObjectMapper();
    private final ConsultCenterSandboxAdapter adapter =
            new ConsultCenterSandboxAdapter("http://127.0.0.1:1", "");

    @Test
    void mapsVerifiedEnvelopeWithoutInventingRestrictionCount() throws Exception {
        var body = json.readTree(VALID_REPORT);
        var report = adapter.map(body);
        assertEquals("AVAILABLE", report.status());
        assertEquals("SANDBOX", report.environment());
        assertEquals(640, report.score().value());
        assertNull(report.restrictions());
    }

    @Test
    void rejectsInvalidEnvelopeAndUnavailableToken() throws Exception {
        assertEquals("INVALID_RESPONSE", adapter.map(json.readTree("{}")).status());
        assertEquals("UNAVAILABLE", adapter.consult(new BureauQuery(UUID.randomUUID(),
                "COMPLETE", "SYNTHETIC_DOCUMENTARY_PRE_ANALYSIS")).status());
    }

    @Test
    void unauthorizedResponseDoesNotBecomeZeroRestrictions() throws Exception {
        try (var server = new ServerSocket(0)) {
            var responder = Thread.ofVirtual().start(() -> {
                try (var accepted = server.accept()) {
                    accepted.getOutputStream().write(("HTTP/1.1 401 Unauthorized\r\n"
                            + "Content-Length: 0\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    accepted.getOutputStream().flush();
                } catch (java.net.SocketException ex) {
                    // Closing the server while the client is finishing is expected cleanup.
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            var report = new ConsultCenterSandboxAdapter(
                    "http://127.0.0.1:" + server.getLocalPort(), "synthetic-test-token")
                    .consult(query());
            responder.join(1000);
            assertEquals("UNAVAILABLE", report.status());
            assertNull(report.restrictions());
        }
    }

    @Test
    void readTimeoutIsUnavailableWithoutInventingRestrictions() throws Exception {
        try (var server = new ServerSocket(0)) {
            var release = new CountDownLatch(1);
            var responder = Thread.ofVirtual().start(() -> {
                try (var accepted = server.accept()) {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        var body = VALID_REPORT.getBytes(StandardCharsets.UTF_8);
                        accepted.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                        accepted.getOutputStream().write(body);
                        accepted.getOutputStream().flush();
                    }
                } catch (java.net.SocketException ex) {
                    // Closing the server while the client is finishing is expected cleanup.
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            try {
                var report = new ConsultCenterSandboxAdapter(
                        "http://127.0.0.1:" + server.getLocalPort(), "synthetic-test-token")
                        .consult(query());
                assertEquals("UNAVAILABLE", report.status());
                assertNull(report.score());
                assertNull(report.restrictions());
            } finally {
                release.countDown();
                responder.join(1000);
                assertFalse(responder.isAlive(), "Local bureau responder must stop");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"COMPLETE,11144477735", "CLEAN,52998224725", "UNAVAILABLE,50250250284"})
    void looksUpOnlyThePublishedFixtureForTheRequestedCase(String caseName, String syntheticCpf) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var request = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            try (exchange) {
                request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                        + " " + exchange.getRequestHeaders().getFirst("APITOKEN"));
                var body = VALID_REPORT.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var client = new ConsultCenterSandboxAdapter("http://127.0.0.1:" + server.getAddress().getPort(),
                    "synthetic-test-token");
            var report = client.consult(new BureauQuery(UUID.randomUUID(), caseName, query().purpose()));

            assertEquals("GET /v3/consultas/credit-bureau?cpf=" + syntheticCpf + " synthetic-test-token",
                    request.get());
            assertEquals("AVAILABLE", report.status());
            assertEquals(640, report.score().value());
            assertEquals("DEMO", report.score().model());
            assertNull(report.restrictions());
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"pontuacao\":-1,\"descricao_scoring_escolhido\":\"DEMO\"}",
            "{\"pontuacao\":0}", "{\"pontuacao\":0,\"descricao_scoring_escolhido\":\" \"}",
            "{\"pontuacao\":2147483648,\"descricao_scoring_escolhido\":\"DEMO\"}"})
    void preservesValidEnvelopeWithoutInventingScoreWhenScoreIsInvalid(String score) throws Exception {
        var body = json.readTree("""
                {"consulta":{"produto":"credit_bureau","consultado_em":"2026-11-20T10:00:00Z"},
                 "dados":{"score":%s},"avisos":[]}
                """.formatted(score));

        var report = adapter.map(body);

        assertEquals("AVAILABLE", report.status());
        assertNull(report.score());
        assertNull(report.restrictions());
    }

    @Test
    void acceptsZeroAsAnExplicitScoreButRejectsInvalidTimestamp() throws Exception {
        var report = adapter.map(json.readTree(VALID_REPORT.replace("640", "0")));
        assertEquals(0, report.score().value());

        var invalid = adapter.map(json.readTree(VALID_REPORT.replace("2026-11-20T10:00:00Z", "invalid")));
        assertEquals("INVALID_RESPONSE", invalid.status());
        assertNull(invalid.consultedAt());
        assertNull(invalid.score());
    }

    private BureauQuery query() {
        return new BureauQuery(UUID.randomUUID(), "COMPLETE", "SYNTHETIC_DOCUMENTARY_PRE_ANALYSIS");
    }
}
