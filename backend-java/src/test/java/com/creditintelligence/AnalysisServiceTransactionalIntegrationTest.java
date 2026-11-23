package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisServiceTransactionalIntegrationTest extends PostgresIntegrationTest {
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "analyst:local_demo_only_change_me".getBytes(StandardCharsets.US_ASCII));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisService service;
    @MockitoBean PythonDocumentClient python;
    @MockitoBean PythonExplanationClient explanations;

    @Test
    void completesFinancialAnalysisAndPersistsAllObservableResults() throws Exception {
        var proposalId = createProposal();
        uploadFinancialDocuments(proposalId);
        consultBureau(proposalId);
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> financialExtraction(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2)));
        when(explanations.explain(any())).thenAnswer(invocation -> {
            var request = (PythonExplanationClient.ExplainRequest) invocation.getArgument(0);
            var items = request.findings().stream()
                    .map(finding -> new PythonExplanationClient.FindingExplanation(
                            finding.code(), "Synthetic explanation", request.allowedPolicyRefs().stream()
                                    .filter(reference -> reference.endsWith(":" + finding.code())).toList(),
                            List.of()))
                    .toList();
            return new PythonExplanationClient.ExplainResponse("1.0", request.analysisId(), "MOCK",
                    "mock-model", "prompt-1", "Synthetic summary", items, List.of(), List.of());
        });

        var response = mvc.perform(post("/api/v1/proposals/{id}/analyses", proposalId)
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"principalRequested\":\"100000.00\",\"termMonths\":12,"
                                + "\"monthlyInterestRate\":\"0.01\",\"currency\":\"BRL\","
                                + "\"pricingSource\":\"TEST\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        var analysisId = UUID.fromString(body.path("analysisId").asText());

        assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("attempt").asInt()).isEqualTo(1);
        assertThat(body.path("financialAssessment").path("reviewStatus").asText())
                .isEqualTo("READY_FOR_REVIEW");
        assertThat(body.path("findings").findValuesAsText("code"))
                .contains("SALARY_RECONCILIATION", "INCOME_CONSISTENCY");
        assertThat(body.path("aiExplanationStatus").asText()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM analysis WHERE id = ?", String.class, analysisId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_snapshot_item WHERE analysis_id = ?",
                Integer.class, analysisId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM extracted_field WHERE analysis_id = ?",
                Integer.class, analysisId)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_assessment WHERE analysis_id = ?",
                Integer.class, analysisId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM validation_finding WHERE analysis_id = ?",
                Integer.class, analysisId)).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "AMBIGUOUS", "INVALID"})
    void nonFactualPayslipIncomeDoesNotPassIncomeConsistency(String netStatus) throws Exception {
        var proposalId = createProposal();
        uploadFinancialDocuments(proposalId);
        consultBureau(proposalId);
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> financialExtraction(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2), netStatus));

        var response = mvc.perform(post("/api/v1/proposals/{id}/analyses", proposalId)
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"principalRequested\":\"100000.00\",\"termMonths\":12,"
                                + "\"monthlyInterestRate\":\"0.01\",\"currency\":\"BRL\","
                                + "\"pricingSource\":\"TEST\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        var consistency = java.util.stream.StreamSupport.stream(body.path("findings").spliterator(), false)
                .filter(finding -> "INCOME_CONSISTENCY".equals(finding.path("code").asText()))
                .findFirst().orElseThrow();

        assertThat(consistency.path("result").asText()).isEqualTo("NOT_EVALUATED");
        assertThat(body.path("financialAssessment").path("reviewStatus").asText())
                .isEqualTo("MORE_INFO_REQUIRED");
    }

    @Test
    void rollsBackPartialPersistenceWhenPostgresRejectsAnIntermediateWrite() throws Exception {
        var proposalId = createProposal();
        uploadFinancialDocuments(proposalId);
        consultBureau(proposalId);
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> financialExtraction(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2)));
        installFailureTrigger();
        try {
            assertThatThrownBy(() -> service.analyze(UUID.fromString(proposalId), "rollback-test", null))
                    .isInstanceOf(RuntimeException.class);

            var analysisId = jdbc.queryForObject("SELECT id FROM analysis WHERE proposal_id = ?",
                    UUID.class, UUID.fromString(proposalId));
            assertThat(jdbc.queryForObject("SELECT status FROM analysis WHERE id = ?", String.class, analysisId))
                    .isEqualTo("RUNNING");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM document_snapshot_item WHERE analysis_id = ?",
                    Integer.class, analysisId)).isEqualTo(6);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM extracted_field WHERE analysis_id = ?",
                    Integer.class, analysisId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_assessment WHERE analysis_id = ?",
                    Integer.class, analysisId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM validation_finding WHERE analysis_id = ?",
                    Integer.class, analysisId)).isZero();
        } finally {
            removeFailureTrigger();
        }
    }

    private String createProposal() throws Exception {
        var payload = "{\"applicant\":{\"name\":\"Synthetic Applicant\",\"syntheticIdentifier\":\"DEMO-"
                + UUID.randomUUID().toString().substring(0, 12)
                + "\"},\"declaredMonthlyIncome\":8000.00,\"currency\":\"BRL\"}";
        var response = mvc.perform(post("/api/v1/proposals").header("Authorization", AUTH)
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("id").asText();
    }

    private void uploadFinancialDocuments(String proposalId) throws Exception {
        for (var period : List.of("2026-08", "2026-09", "2026-10")) {
            for (var type : List.of("PAYSLIP", "BANK_STATEMENT")) {
                var prefix = "PAYSLIP".equals(type) ? "payslip" : "statement";
                mvc.perform(multipart("/api/v1/proposals/{id}/documents", proposalId)
                                .file(new MockMultipartFile("file", prefix + ".pdf", "application/pdf",
                                        Files.readAllBytes(sharedFixture(prefix + "-" + period + "-demo.pdf"))))
                                .param("type", type).param("period", period)
                                .header("Authorization", AUTH))
                        .andExpect(status().isCreated());
            }
        }
    }

    private void consultBureau(String proposalId) throws Exception {
        mvc.perform(post("/api/v1/proposals/{id}/bureau-consultations", proposalId)
                        .header("Authorization", AUTH).contentType("application/json")
                        .content("{\"caseName\":\"COMPLETE\"}"))
                .andExpect(status().isOk());
    }

    private PythonDocumentClient.ProcessResponse financialExtraction(UUID analysisId, UUID attemptId,
            List<DocumentSnapshotRef> documents) {
        return financialExtraction(analysisId, attemptId, documents, "EXTRACTED");
    }

    private PythonDocumentClient.ProcessResponse financialExtraction(UUID analysisId, UUID attemptId,
            List<DocumentSnapshotRef> documents, String netStatus) {
        var results = documents.stream().map(document -> {
            if (document.type().name().equals("PAYSLIP")) {
                var netSalary = "EXTRACTED".equals(netStatus)
                        ? field(document, "netSalary", "8000.00", "BRL")
                        : uncertainField(document, "netSalary", netStatus);
                return new PythonDocumentClient.DocumentResult(document.id(), document.version(), List.of(
                        field(document, "competence", document.period(), null),
                        field(document, "grossSalary", "9000.00", "BRL"),
                        netSalary,
                        field(document, "payrollDeduction", "1000.00", "BRL")));
            }
            return new PythonDocumentClient.DocumentResult(document.id(), document.version(), List.of(
                    field(document, "statementPeriod", document.period(), null)), List.of(
                    transaction(document, "tx" + Integer.parseInt(document.period().substring(5)), "CREDIT", "8000.00",
                            "POSSIBLE_SALARY", "SALARIO DEMO"),
                    transaction(document, "tx" + (Integer.parseInt(document.period().substring(5)) + 10),
                            "DEBIT", "1000.00", "RECURRING_PAYMENT", "PARCELA CONSIGNADO DEMO")));
        }).toList();
        return new PythonDocumentClient.ProcessResponse("2.0", analysisId, attemptId, results);
    }

    private PythonDocumentClient.ExtractedField field(DocumentSnapshotRef document, String name,
            String value, String currency) {
        return new PythonDocumentClient.ExtractedField(document.id(), document.version(), name, value,
                currency, "EXTRACTED", document.id() + ":v" + document.version() + ":p1:" + name,
                1, "Synthetic evidence for " + name);
    }

    private PythonDocumentClient.ExtractedField uncertainField(DocumentSnapshotRef document, String name,
            String status) {
        return new PythonDocumentClient.ExtractedField(document.id(), document.version(), name, null,
                null, status, null, 1, "Synthetic uncertainty for " + name);
    }

    private PythonDocumentClient.ExtractedTransaction transaction(DocumentSnapshotRef document,
            String id, String direction, String amount, String classification, String description) {
        return new PythonDocumentClient.ExtractedTransaction(document.id(), document.version(), id,
                document.period() + "-28", direction, amount, "BRL", description, classification,
                document.id() + ":v" + document.version() + ":p1:" + id, 1, "Synthetic transaction evidence");
    }

    private void installFailureTrigger() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_net_salary_insert() RETURNS trigger AS $$
                BEGIN
                    IF NEW.field_name = 'netSalary' THEN
                        RAISE EXCEPTION 'synthetic persistence failure after prior writes';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_net_salary_insert_trigger
                BEFORE INSERT ON extracted_field
                FOR EACH ROW EXECUTE FUNCTION fail_net_salary_insert()
                """);
    }

    private void removeFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_net_salary_insert_trigger ON extracted_field");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_net_salary_insert()");
    }
}
