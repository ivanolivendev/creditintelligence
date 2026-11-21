package com.creditintelligence.bureau.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.creditintelligence.bureau.application.CreditBureauPort.BureauQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultCenterSandboxAdapterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ConsultCenterSandboxAdapter adapter =
            new ConsultCenterSandboxAdapter("http://127.0.0.1:1", "");

    @Test
    void mapsVerifiedEnvelopeWithoutInventingRestrictionCount() throws Exception {
        var body = json.readTree("""
                {"consulta":{"produto":"credit_bureau","consultado_em":"2026-11-20T10:00:00Z"},
                 "dados":{"score":{"pontuacao":640,"descricao_scoring_escolhido":"DEMO"}},"avisos":[]}
                """);
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
                    release.await(15, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            try {
                var started = System.nanoTime();
                var report = new ConsultCenterSandboxAdapter(
                        "http://127.0.0.1:" + server.getLocalPort(), "synthetic-test-token")
                        .consult(query());
                var seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
                assertEquals("UNAVAILABLE", report.status());
                assertNull(report.restrictions());
                assertTrue(seconds >= 7 && seconds < 13, "Configured eight-second read timeout must apply");
            } finally {
                release.countDown();
                responder.join(1000);
            }
        }
    }

    private BureauQuery query() {
        return new BureauQuery(UUID.randomUUID(), "COMPLETE", "SYNTHETIC_DOCUMENTARY_PRE_ANALYSIS");
    }
}
