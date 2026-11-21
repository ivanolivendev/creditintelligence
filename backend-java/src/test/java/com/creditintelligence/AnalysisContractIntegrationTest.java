package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisContractIntegrationTest {
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "analyst:local_demo_only_change_me".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PythonDocumentClient python;

    @Test
    void rejectsEvidenceOutsideSnapshotWithoutPersistingFacts() throws Exception {
        var payload = "{\"applicant\":{\"name\":\"Maria Silva\",\"syntheticIdentifier\":\"DEMO-"
                + UUID.randomUUID().toString().substring(0, 12)
                + "\"},\"declaredMonthlyIncome\":8000.00,\"currency\":\"BRL\"}";
        var created = mvc.perform(post("/api/v1/proposals").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var proposalId = json.readTree(created.getContentAsString()).path("id").asText();
        var bytes = Files.readAllBytes(Path.of("/fixtures/documents/income-proof-demo.pdf"));
        var file = new MockMultipartFile("file", "income.pdf", "application/pdf", bytes);
        mvc.perform(multipart("/api/v1/proposals/{id}/documents", proposalId)
                .file(file).param("type", "INCOME_PROOF").header("Authorization", AUTH))
                .andExpect(status().isCreated());

        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> new PythonDocumentClient.ProcessResponse("1.0",
                        invocation.getArgument(0), invocation.getArgument(1),
                        List.of(new PythonDocumentClient.DocumentResult(UUID.randomUUID(), 1, List.of()))));
        var response = mvc.perform(post("/api/v1/proposals/{id}/analyses", proposalId)
                .header("Authorization", AUTH).contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        var analysis = json.readTree(response.getContentAsString());
        assertThat(analysis.path("status").asText()).isEqualTo("FAILED_PERMANENT");
        assertThat(analysis.path("errorCode").asText()).isEqualTo("INVALID_EXTRACTION_RESPONSE");
        assertThat(analysis.path("extractedFields").size()).isZero();
        assertThat(analysis.path("findings").size()).isZero();
        assertThat(analysis.path("incomeTolerancePercent").decimalValue())
                .isEqualByComparingTo("5.00");
        assertThat(analysis.path("requiredDocumentTypes").size()).isEqualTo(1);
        assertThat(analysis.path("requiredDocumentTypes").get(0).asText()).isEqualTo("INCOME_PROOF");
    }

    @Test
    void rejectsInventedBankTransactionReferenceWithoutAssessment() throws Exception {
        var payload = "{\"applicant\":{\"name\":\"Alex Demo\",\"syntheticIdentifier\":\"DEMO-"
                + UUID.randomUUID().toString().substring(0, 12)
                + "\"},\"declaredMonthlyIncome\":8000.00,\"currency\":\"BRL\"}";
        var created = mvc.perform(post("/api/v1/proposals").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var proposalId = json.readTree(created.getContentAsString()).path("id").asText();
        for (var month : List.of("2026-08", "2026-09", "2026-10")) {
            for (var entry : List.of("payslip", "statement")) {
                var bytes = Files.readAllBytes(Path.of("/fixtures/documents/" + entry + "-" + month + "-demo.pdf"));
                mvc.perform(multipart("/api/v1/proposals/{id}/documents", proposalId)
                        .file(new MockMultipartFile("file", entry + ".pdf", "application/pdf", bytes))
                        .param("type", "payslip".equals(entry) ? "PAYSLIP" : "BANK_STATEMENT")
                        .param("period", month).header("Authorization", AUTH))
                        .andExpect(status().isCreated());
            }
        }
        mvc.perform(post("/api/v1/proposals/{id}/bureau-consultations", proposalId)
                .header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseName\":\"COMPLETE\"}"))
                .andExpect(status().isOk());
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    UUID analysisId = invocation.getArgument(0);
                    UUID attemptId = invocation.getArgument(1);
                    List<com.creditintelligence.document.application.DocumentSnapshotRef> refs =
                            invocation.getArgument(2);
                    var results = refs.stream().map(ref -> {
                        if (ref.type().name().equals("PAYSLIP")) {
                            return new PythonDocumentClient.DocumentResult(ref.id(), ref.version(), List.of());
                        }
                        var invented = new PythonDocumentClient.ExtractedTransaction(ref.id(), ref.version(),
                                "tx-" + ref.period(), ref.period() + "-28", "CREDIT", "7300.00", "BRL", "SALARIO DEMO",
                                "POSSIBLE_SALARY", "invented-reference", 1, "Synthetic snippet");
                        return new PythonDocumentClient.DocumentResult(ref.id(), ref.version(),
                                List.of(), List.of(invented));
                    }).toList();
                    return new PythonDocumentClient.ProcessResponse("2.0", analysisId, attemptId, results);
                });
        var response = mvc.perform(post("/api/v1/proposals/{id}/analyses", proposalId)
                .header("Authorization", AUTH)).andExpect(status().isOk())
                .andReturn().getResponse();
        var analysis = json.readTree(response.getContentAsString());
        assertThat(analysis.path("status").asText()).isEqualTo("FAILED_PERMANENT");
        assertThat(analysis.path("financialAssessment").isNull()).isTrue();
        assertThat(analysis.path("extractedFields").size()).isZero();
    }
}
