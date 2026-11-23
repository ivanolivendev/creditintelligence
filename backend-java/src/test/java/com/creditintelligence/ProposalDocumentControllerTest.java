package com.creditintelligence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.document.api.DocumentController;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentView;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.proposal.api.ProposalController;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalView;
import com.creditintelligence.shared.errors.ApiExceptionHandler;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.InvalidDocumentException;
import com.creditintelligence.shared.errors.NotFoundException;
import com.creditintelligence.shared.errors.PayloadTooLargeException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ProposalDocumentControllerTest {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-22T12:00:00Z");
    private static final String PROPOSALS = "/api/v1/proposals";
    private static final String DOCUMENTS = PROPOSALS + "/{proposalId}/documents";
    private final ProposalService proposals = mock(ProposalService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProposalController(proposals),
            new DocumentController(documents)).setControllerAdvice(new ApiExceptionHandler()).build();
    private final UUID proposalId = UUID.randomUUID();

    @Test
    void createsProposalAndReturnsTheSameIdentityInReadsAndHistory() throws Exception {
        var applicant = new ProposalView.ApplicantView(UUID.randomUUID(), "Synthetic Applicant", "DEMO-44");
        var income = new BigDecimal("8000.00");
        var view = new ProposalView(proposalId, applicant, income, "BRL", "DRAFT", CREATED_AT);
        when(proposals.create(applicant.name(), applicant.syntheticIdentifier(), income, "BRL")).thenReturn(view);
        when(proposals.get(proposalId)).thenReturn(view);
        when(proposals.list()).thenReturn(List.of(view));

        mvc.perform(post(PROPOSALS).contentType("application/json").content("""
                {"applicant":{"name":"Synthetic Applicant","syntheticIdentifier":"DEMO-44"},
                 "declaredMonthlyIncome":8000.00,"currency":"BRL"}
                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PROPOSALS + "/" + proposalId))
                .andExpect(jsonPath("$.id").value(proposalId.toString()))
                .andExpect(jsonPath("$.applicant.syntheticIdentifier").value("DEMO-44"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get(PROPOSALS + "/{id}", proposalId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.applicant.name").value("Synthetic Applicant"))
                .andExpect(jsonPath("$.declaredMonthlyIncome").value(8000.00));
        mvc.perform(get(PROPOSALS)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(proposalId.toString()));
    }

    @Test
    void receivesMultipartPeriodAndListsThePublicDocumentMetadata() throws Exception {
        var bytes = "synthetic multipart body".getBytes(StandardCharsets.US_ASCII);
        var hash = "b".repeat(64);
        var view = new DocumentView(UUID.randomUUID(), proposalId, DocumentType.PAYSLIP, 2, hash,
                CREATED_AT, "AVAILABLE", "2026-10");
        when(documents.upload(proposalId, DocumentType.PAYSLIP, "2026-10", "application/pdf", bytes)).thenReturn(view);
        when(documents.list(proposalId)).thenReturn(List.of(view));

        mvc.perform(multipart(DOCUMENTS, proposalId)
                .file(new MockMultipartFile("file", "demo.pdf", "application/pdf", bytes))
                .param("type", "PAYSLIP").param("period", "2026-10"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PROPOSALS + "/" + proposalId + "/documents/" + view.id()))
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.period").value("2026-10"));
        mvc.perform(get(DOCUMENTS, proposalId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(view.id().toString()))
                .andExpect(jsonPath("$[0].sha256").value(hash))
                .andExpect(jsonPath("$[0].objectKey").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("documentFailures")
    void exposesUploadFailuresAsCorrelatedProblemDetails(RuntimeException failure, int response, String detail)
            throws Exception {
        var bytes = new byte[0];
        when(documents.upload(proposalId, DocumentType.INCOME_PROOF, null, "application/pdf", bytes))
                .thenThrow(failure);

        mvc.perform(multipart(DOCUMENTS, proposalId)
                .file(new MockMultipartFile("file", "demo.pdf", "application/pdf", bytes))
                .param("type", "INCOME_PROOF").requestAttr("correlationId", "corr-upload"))
                .andExpect(status().is(response))
                .andExpect(jsonPath("$.status").value(response))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.correlationId").value("corr-upload"));
    }

    static Stream<Arguments> documentFailures() {
        return Stream.of(
                Arguments.of(new InvalidDocumentException("Invalid PDF"), 422, "Invalid PDF"),
                Arguments.of(new PayloadTooLargeException("PDF too large"), 413, "PDF too large"),
                Arguments.of(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period"), 400, "Invalid period"),
                Arguments.of(new ConflictException("Finalized proposal"), 409, "Finalized proposal"),
                Arguments.of(new DataIntegrityViolationException("private database detail"), 409,
                        "A conflicting record already exists"));
    }

    @Test
    void returnsCorrelatedNotFoundForAnUnknownProposal() throws Exception {
        when(proposals.get(proposalId)).thenThrow(new NotFoundException("Proposal not found"));

        mvc.perform(get(PROPOSALS + "/{id}", proposalId).requestAttr("correlationId", "corr-read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proposal not found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("corr-read"));
    }
}
