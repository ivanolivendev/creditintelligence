package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.document.infrastructure.DocumentRepository;
import com.creditintelligence.proposal.infrastructure.ProposalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProposalDocumentApiIntegrationTest {
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString(
            "analyst:local_demo_only_change_me".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProposalRepository proposals;
    @Autowired DocumentRepository documents;

    @Test
    void createsAndReadsProposal() throws Exception {
        var id = createProposal();
        var response = mvc.perform(get("/api/v1/proposals/{id}", id).header("Authorization", AUTH))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertThat(body.path("id").asText()).isEqualTo(id);
        assertThat(body.path("status").asText()).isEqualTo("DRAFT");
        assertThat(body.path("declaredMonthlyIncome").decimalValue()).isEqualByComparingTo("8000.00");
        assertThat(proposals.findById(UUID.fromString(id))).isPresent();
    }

    @Test
    void rejectsInvalidAndUnauthenticatedRequests() throws Exception {
        var before = proposals.count();
        mvc.perform(post("/api/v1/proposals").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicant\":{\"name\":\"\",\"syntheticIdentifier\":\"BAD\"},\"declaredMonthlyIncome\":-1,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
        assertThat(proposals.count()).isEqualTo(before);
        mvc.perform(get("/api/v1/proposals/{id}", UUID.randomUUID()).header("Authorization", AUTH))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/proposals/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storesImmutableVersionsAndRejectsInvalidFiles() throws Exception {
        var proposalId = createProposal();
        var pdf = pdf(true);
        JsonNode first = upload(proposalId, pdf, "application/pdf", 201);
        JsonNode second = upload(proposalId, pdf, "application/pdf", 201);
        assertThat(first.path("version").asInt()).isEqualTo(1);
        assertThat(second.path("version").asInt()).isEqualTo(2);
        assertThat(first.path("sha256").asText()).hasSize(64).isEqualTo(second.path("sha256").asText());

        var list = mvc.perform(get("/api/v1/proposals/{id}/documents", proposalId)
                .header("Authorization", AUTH)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(list.getContentAsString()).size()).isEqualTo(2);
        var stored = documents.findByProposalIdOrderByTypeAscDocumentVersionAsc(UUID.fromString(proposalId));
        assertThat(stored).hasSize(2);
        for (var document : stored) {
            assertThat(Files.exists(Path.of(System.getenv("DOCUMENT_STORAGE_ROOT"), document.getObjectKey()))).isTrue();
        }

        upload(proposalId, "not a PDF".getBytes(), "application/pdf", 422);
        upload(proposalId, pdf(false), "application/pdf", 422);
        upload(proposalId, new byte[10 * 1024 * 1024 + 1], "application/pdf", 413);
        assertThat(documents.findByProposalIdOrderByTypeAscDocumentVersionAsc(UUID.fromString(proposalId)))
                .hasSize(2);
    }

    @Test
    void keepsSixFinancialPeriodsAndReplacementHistory() throws Exception {
        var proposalId = createProposal();
        for (var month : java.util.List.of("2026-08", "2026-09", "2026-10")) {
            for (var type : java.util.List.of("PAYSLIP", "BANK_STATEMENT")) {
                var prefix = "PAYSLIP".equals(type) ? "payslip" : "statement";
                var bytes = Files.readAllBytes(Path.of("/fixtures/documents/" + prefix + "-" + month
                        + "-demo.pdf"));
                var response = mvc.perform(multipart("/api/v1/proposals/{id}/documents", proposalId)
                        .file(new MockMultipartFile("file", prefix + ".pdf", "application/pdf", bytes))
                        .param("type", type).param("period", month).header("Authorization", AUTH))
                        .andExpect(status().isCreated()).andReturn().getResponse();
                assertThat(json.readTree(response.getContentAsString()).path("period").asText())
                        .isEqualTo(month);
            }
        }
        var all = documents.findByProposalIdOrderByTypeAscDocumentVersionAsc(UUID.fromString(proposalId));
        assertThat(all).hasSize(6);
        assertThat(all.stream().map(d -> d.getType() + d.getPeriodKey()).distinct().count()).isEqualTo(6);
        var replacement = Files.readAllBytes(Path.of("/fixtures/documents/payslip-2026-10-demo.pdf"));
        var replaced = mvc.perform(multipart("/api/v1/proposals/{id}/documents", proposalId)
                .file(new MockMultipartFile("file", "replacement.pdf", "application/pdf", replacement))
                .param("type", "PAYSLIP").param("period", "2026-10").header("Authorization", AUTH))
                .andExpect(status().isCreated()).andReturn().getResponse();
        assertThat(json.readTree(replaced.getContentAsString()).path("version").asInt()).isEqualTo(2);
        assertThat(documents.findByProposalIdOrderByTypeAscDocumentVersionAsc(UUID.fromString(proposalId)))
                .hasSize(7);
        mvc.perform(multipart("/api/v1/proposals/{id}/documents", proposalId)
                .file(new MockMultipartFile("file", "invalid.pdf", "application/pdf", replacement))
                .param("type", "PAYSLIP").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    private String createProposal() throws Exception {
        var payload = "{\"applicant\":{\"name\":\"Maria Silva\",\"syntheticIdentifier\":\"DEMO-"
                + UUID.randomUUID().toString().substring(0, 12)
                + "\"},\"declaredMonthlyIncome\":8000.00,\"currency\":\"BRL\"}";
        var response = mvc.perform(post("/api/v1/proposals").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("id").asText();
    }

    private JsonNode upload(String proposalId, byte[] bytes, String contentType, int expectedStatus) throws Exception {
        var file = new MockMultipartFile("file", "demo.pdf", contentType, bytes);
        var response = mvc.perform(multipart("/api/v1/proposals/{id}/documents", proposalId)
                .file(file).param("type", "INCOME_PROOF").header("Authorization", AUTH))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private byte[] pdf(boolean withText) throws Exception {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            if (withText) {
                try (var content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText("Renda mensal: R$ 7500.00");
                    content.endText();
                }
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }
}
