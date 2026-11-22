package com.creditintelligence.bureau.infrastructure;

import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.bureau.application.CreditBureauPort;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Maps the public, static v3 homologation contract; never accepts an arbitrary CPF. */
@Component
@ConditionalOnProperty(name = "bureau.mode", havingValue = "sandbox")
public final class ConsultCenterSandboxAdapter implements CreditBureauPort {
    private final RestClient client;
    private final String token;

    public ConsultCenterSandboxAdapter(@Value("${bureau.url}") String url,
            @Value("${bureau.sandbox-token}") String token) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.token = token;
    }

    @Override
    public BureauReport consult(BureauQuery query) {
        // These three identifiers are published as synthetic cases in the v3 OpenAPI.
        var testCpf = switch (query.caseName()) {
            case "COMPLETE" -> "11144477735";
            case "CLEAN" -> "52998224725";
            case "UNAVAILABLE" -> "50250250284";
            default -> throw new IllegalArgumentException("Unknown sandbox case");
        };
        if (token == null || token.isBlank()) return failed("UNAVAILABLE", "Sandbox token not configured");
        try {
            var body = client.get().uri(builder -> builder.path("/v3/consultas/credit-bureau")
                            .queryParam("cpf", testCpf).build())
                    .header("APITOKEN", token).retrieve().body(JsonNode.class);
            return map(body);
        } catch (RestClientException ex) {
            return failed("UNAVAILABLE", "Sandbox HTTP request unavailable (" +
                    ex.getClass().getSimpleName() + ")");
        } catch (RuntimeException ex) {
            return failed("INVALID_RESPONSE", "Sandbox response did not match the verified v3 contract");
        }
    }

    BureauReport map(JsonNode body) {
        if (body == null || !body.path("consulta").isObject()
                || !"credit_bureau".equals(body.path("consulta").path("produto").asText())
                || !body.path("dados").isObject() || !body.path("avisos").isArray()) {
            return failed("INVALID_RESPONSE", "Sandbox response did not match the verified v3 envelope");
        }
        OffsetDateTime consultedAt;
        try {
            consultedAt = OffsetDateTime.parse(body.path("consulta").path("consultado_em").asText());
        } catch (RuntimeException ex) {
            return failed("INVALID_RESPONSE", "Sandbox response timestamp is invalid");
        }
        var scoreNode = body.path("dados").path("score");
        BureauReport.Score score = null;
        if (scoreNode.isObject() && scoreNode.path("pontuacao").canConvertToInt()
                && scoreNode.path("pontuacao").asInt() >= 0) {
            var model = scoreNode.path("descricao_scoring_escolhido").asText(null);
            if (model != null && !model.isBlank()) {
                score = new BureauReport.Score(scoreNode.path("pontuacao").asInt(),
                        null, null, model);
            }
        }
        return new BureauReport("CONSULT_CENTER", "CREDIT_BUREAU", "SANDBOX",
                "AVAILABLE", consultedAt, "3.0.0", null, score, null,
                List.of("Homologação estática; não representa histórico de pessoa real.",
                        "O produto não forneceu contagem canônica de restrições neste mapeamento."));
    }

    private BureauReport failed(String status, String reason) {
        return new BureauReport("CONSULT_CENTER", "CREDIT_BUREAU", "SANDBOX",
                status, null, "3.0.0", null, null, null, List.of(reason));
    }
}
