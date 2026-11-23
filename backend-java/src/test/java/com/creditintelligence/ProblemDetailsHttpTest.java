package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.decision.api.DecisionController;
import com.creditintelligence.decision.application.DecisionService;
import com.creditintelligence.decision.domain.DecisionOutcome;
import com.creditintelligence.document.api.DocumentController;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.proposal.api.ProposalController;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.shared.errors.ApiExceptionHandler;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.InvalidDocumentException;
import com.creditintelligence.shared.errors.NotFoundException;
import com.creditintelligence.shared.errors.PayloadTooLargeException;
import com.creditintelligence.shared.observability.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

class ProblemDetailsHttpTest {
    private static final String PROPOSALS = "/api/v1/proposals";
    private static final String DOCUMENTS = PROPOSALS + "/{id}/documents";
    private static final String DECISIONS = PROPOSALS + "/{id}/decisions";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String CORRELATION = "local.case_001";
    private static final UUID PROPOSAL = new UUID(0, 1);
    private static final UUID ANALYSIS = new UUID(0, 2);
    private static final String SNAPSHOT = "a".repeat(64);
    private static final byte[] PDF = "%PDF-synthetic".getBytes(StandardCharsets.UTF_8);
    private final ProposalService proposals = mock(ProposalService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final DecisionService decisions = mock(DecisionService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new ProposalController(proposals), new DocumentController(documents), new DecisionController(decisions))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"unsafe trace / id", ""})
    void generatesCorrelationForMissingOrInvalidHeaderAndIncludesItInProblemDetails(String supplied) throws Exception {
        when(proposals.get(PROPOSAL)).thenThrow(new NotFoundException("Proposal not found"));
        var request = get(PROPOSALS + "/{id}", PROPOSAL);
        if (supplied != null) {
            request.header(CORRELATION_HEADER, supplied);
        }

        var response = expectProblem(mvc.perform(request), HttpStatus.NOT_FOUND, "Proposal not found")
                .andExpect(header().exists(CORRELATION_HEADER)).andReturn().getResponse();

        var generated = response.getHeader(CORRELATION_HEADER);
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
        assertThat(json.readTree(response.getContentAsString()).path("correlationId").asText()).isEqualTo(generated);
    }

    @Test
    void reportsFieldErrorsAndStopsInvalidProposalBeforeApplication() throws Exception {
        var result = mvc.perform(post(PROPOSALS).header(CORRELATION_HEADER, CORRELATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"applicant":{"name":"","syntheticIdentifier":"DEMO-TEST"},
                         "declaredMonthlyIncome":-1,"currency":" "}
                        """));

        expectCorrelatedProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed")
                .andExpect(jsonPath("$.fieldErrors['applicant.name']").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.declaredMonthlyIncome").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.currency").isNotEmpty());
        verifyNoInteractions(proposals);
    }

    @Test
    void reportsMalformedJsonWithSafeDetailBeforeApplication() throws Exception {
        var result = mvc.perform(post(PROPOSALS).header(CORRELATION_HEADER, CORRELATION)
                .contentType(MediaType.APPLICATION_JSON).content("{invalid-json"));

        expectCorrelatedProblem(result, HttpStatus.BAD_REQUEST, "Request body is invalid");
        verifyNoInteractions(proposals);
    }

    @Test
    void hidesPersistenceDetailsOnProposalIdentityConflict() throws Exception {
        when(proposals.create(eq("Synthetic Applicant"), eq("DEMO-TEST"), any(), eq("BRL")))
                .thenThrow(new DataIntegrityViolationException("duplicate key in internal applicant index"));

        var result = mvc.perform(post(PROPOSALS).header(CORRELATION_HEADER, CORRELATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"applicant":{"name":"Synthetic Applicant","syntheticIdentifier":"DEMO-TEST"},
                         "declaredMonthlyIncome":8000,"currency":"BRL"}
                        """));

        expectCorrelatedProblem(result, HttpStatus.CONFLICT, "A conflicting record already exists");
    }

    @ParameterizedTest
    @MethodSource("decisionFailures")
    void exposesSafeDecisionConflictsAndDenials(RuntimeException failure, HttpStatus status, String detail)
            throws Exception {
        when(decisions.decide(PROPOSAL, ANALYSIS, SNAPSHOT, DecisionOutcome.APPROVED, "Synthetic review", CORRELATION))
                .thenThrow(failure);

        var result = mvc.perform(post(DECISIONS, PROPOSAL).header(CORRELATION_HEADER, CORRELATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"analysisId":"%s","documentSnapshotHash":"%s","outcome":"APPROVED",
                         "reason":"Synthetic review"}
                        """.formatted(ANALYSIS, SNAPSHOT)));

        expectCorrelatedProblem(result, status, detail);
    }

    private static Stream<Arguments> decisionFailures() {
        return Stream.of(
                arguments(new ConflictException("Document snapshot no longer current"),
                        HttpStatus.CONFLICT, "Document snapshot no longer current"),
                arguments(new AccessDeniedException("Internal access denial"),
                        HttpStatus.FORBIDDEN, "You are not allowed to perform this action"));
    }

    @ParameterizedTest
    @MethodSource("documentFailures")
    void exposesDocumentFailuresWithTheirSpecificStatus(RuntimeException failure, HttpStatus status, String detail)
            throws Exception {
        when(documents.upload(eq(PROPOSAL), eq(DocumentType.PAYSLIP), eq("2026-10"),
                eq("application/pdf"), any(byte[].class))).thenThrow(failure);
        var file = new MockMultipartFile("file", "synthetic.pdf", "application/pdf", PDF);

        var result = mvc.perform(multipart(DOCUMENTS, PROPOSAL).file(file)
                .param("type", "PAYSLIP").param("period", "2026-10").header(CORRELATION_HEADER, CORRELATION));

        expectCorrelatedProblem(result, status, detail);
    }

    private static Stream<Arguments> documentFailures() {
        return Stream.of(
                arguments(new InvalidDocumentException("PDF must contain text"),
                        HttpStatus.UNPROCESSABLE_ENTITY, "PDF must contain text"),
                arguments(new PayloadTooLargeException("PDF exceeds the upload limit"),
                        HttpStatus.PAYLOAD_TOO_LARGE, "PDF exceeds the upload limit"),
                arguments(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid financial period"),
                        HttpStatus.BAD_REQUEST, "Invalid financial period"));
    }

    @Test
    void reportsMultipartSizeRejectionBeforeDocumentServiceReceivesBytes() throws Exception {
        var oversize = new MockMultipartFile("file", "oversize.pdf", "application/pdf", PDF) {
            @Override
            public byte[] getBytes() {
                throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
            }
        };

        var result = mvc.perform(multipart(DOCUMENTS, PROPOSAL).file(oversize)
                .param("type", "BANK_STATEMENT").param("period", "2026-10").header(CORRELATION_HEADER, CORRELATION));

        expectCorrelatedProblem(result, HttpStatus.PAYLOAD_TOO_LARGE, "Document exceeds the 10 MiB limit");
        verifyNoInteractions(documents);
    }

    private ResultActions expectCorrelatedProblem(ResultActions actions, HttpStatus status, String detail)
            throws Exception {
        return expectProblem(actions, status, detail)
                .andExpect(header().string(CORRELATION_HEADER, CORRELATION))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION));
    }

    private ResultActions expectProblem(ResultActions actions, HttpStatus status, String detail) throws Exception {
        return actions.andExpect(status().is(status.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status.value()))
                .andExpect(jsonPath("$.code").value(status.name()))
                .andExpect(jsonPath("$.detail").value(detail));
    }
}
