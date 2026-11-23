package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiContractValidationIntegrationTest extends PostgresIntegrationTest {
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "analyst:local_demo_only_change_me".getBytes(StandardCharsets.US_ASCII));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void validationFailureUsesProblemDetailsAndPropagatesCorrelationId() throws Exception {
        var response = mvc.perform(post("/api/v1/proposals")
                .header("Authorization", AUTH)
                .header("X-Correlation-ID", "validation-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicant\":{\"name\":\"\",\"syntheticIdentifier\":\"BAD\"},"
                        + "\"declaredMonthlyIncome\":-1,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-ID", "validation-001"))
                .andReturn().getResponse();

        var body = json.readTree(response.getContentAsString());
        assertThat(body.path("type").asText()).isNotBlank();
        assertThat(body.path("status").asInt()).isEqualTo(400);
        assertThat(body.path("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.path("correlationId").asText()).isEqualTo("validation-001");
        assertThat(body.path("fieldErrors").fieldNames()).toIterable()
                .contains("applicant.name", "declaredMonthlyIncome", "currency");
    }

    @Test
    void unsafeCorrelationIdIsReplacedAndUnauthenticatedEndpointsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/proposals/{id}", UUID.randomUUID())
                .header("X-Correlation-ID", "bad value"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/proposals/{id}", UUID.randomUUID())
                .header("Authorization", AUTH)
                .header("X-Correlation-ID", "bad value"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    void decisionValidationRejectsMalformedSnapshotAndMissingRequiredFields() throws Exception {
        var response = mvc.perform(post("/api/v1/proposals/{id}/decisions", UUID.randomUUID())
                .header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentSnapshotHash\":\"not-a-hash\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        var body = json.readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.path("fieldErrors").fieldNames()).toIterable()
                .contains("analysisId", "documentSnapshotHash", "outcome", "reason");
    }
}
