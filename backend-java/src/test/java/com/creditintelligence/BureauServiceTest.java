package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.bureau.application.BureauService;
import com.creditintelligence.bureau.application.CreditBureauPort;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class BureauServiceTest {
    private final CreditBureauPort provider = mock(CreditBureauPort.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final BureauService service = new BureauService(provider, proposals, jdbc, json, transactions);

    @Test
    void rejectsUnknownSyntheticCaseBeforeCallingProvider() {
        var proposalId = UUID.randomUUID();

        assertThatThrownBy(() -> service.consult(proposalId, "UNKNOWN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(provider, proposals);
    }

    @Test
    void rejectsEnrichmentAfterFinalDecision() {
        var proposalId = UUID.randomUUID();
        when(proposals.get(proposalId)).thenReturn(new com.creditintelligence.proposal.application.ProposalView(
                proposalId, null, new BigDecimal("8000.00"), "BRL", "APPROVED", null));

        assertThatThrownBy(() -> service.consult(proposalId, "CLEAN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(provider);
    }

    @Test
    void returnsEmptySnapshotWhenNoConsultationExists() {
        var proposalId = UUID.randomUUID();
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
                org.mockito.ArgumentMatchers.eq(proposalId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        var snapshot = service.latest(proposalId);

        assertThat(snapshot.id()).isNull();
        assertThat(snapshot.report().status()).isEqualTo("NOT_REQUESTED");
    }

    @Test
    void persistsTheScopedReportWithItsOwnReferenceAndContentHash() throws Exception {
        var proposalId = UUID.randomUUID();
        var report = syntheticReport();
        when(proposals.get(proposalId)).thenReturn(proposal(proposalId, "DOCUMENTS_RECEIVED"));
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal(proposalId, "DOCUMENTS_RECEIVED"));
        var query = new CreditBureauPort.BureauQuery(proposalId, "CLEAN", "SYNTHETIC_DOCUMENTARY_PRE_ANALYSIS");
        when(provider.consult(query)).thenReturn(report);
        runTransaction();

        var consultation = service.consult(proposalId, "CLEAN");

        assertThat(consultation.report().referenceId()).isEqualTo(consultation.consultationId().toString());
        assertThat(consultation.report().score()).isEqualTo(report.score());
        assertThat(consultation.report().limitations()).isEqualTo(report.limitations());
        var content = json.writeValueAsString(consultation.report());
        var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        verify(jdbc).update(anyString(), eq(consultation.consultationId()), eq(proposalId), eq(report.provider()),
                eq(report.environment()), eq(report.status()), eq(report.responseVersion()), eq(content), eq(digest),
                any(OffsetDateTime.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED"})
    void rechecksFinalizationUnderTheProposalLock(String finalStatus) {
        var proposalId = UUID.randomUUID();
        when(proposals.get(proposalId)).thenReturn(proposal(proposalId, "DOCUMENTS_RECEIVED"));
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal(proposalId, finalStatus));
        when(provider.consult(any())).thenReturn(syntheticReport());
        runTransaction();

        assertThatThrownBy(() -> service.consult(proposalId, "COMPLETE"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(jdbc);
    }

    @Test
    void returnsTheStoredReportForTheSelectedConsultation() throws Exception {
        var proposalId = UUID.randomUUID();
        var consultationId = UUID.randomUUID();
        var report = syntheticReport();
        storedReport(proposalId, consultationId, json.writeValueAsString(report));

        assertThat(service.latest(proposalId)).isEqualTo(new BureauService.Snapshot(consultationId, report));
    }

    @Test
    void rejectsCorruptPersistedBureauData() throws Exception {
        var proposalId = UUID.randomUUID();
        storedReport(proposalId, UUID.randomUUID(), "{invalid");

        assertThatThrownBy(() -> service.latest(proposalId)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored bureau report is invalid");
    }

    private void runTransaction() {
        doAnswer(call -> {
            call.<java.util.function.Consumer<TransactionStatus>>getArgument(0).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    private void storedReport(UUID proposalId, UUID consultationId, String content) throws Exception {
        var row = mock(ResultSet.class);
        when(row.getObject(1, UUID.class)).thenReturn(consultationId);
        when(row.getString(2)).thenReturn(content);
        doAnswer(call -> call.<RowMapper<BureauService.Snapshot>>getArgument(1).mapRow(row, 0))
                .when(jdbc).queryForObject(anyString(), ArgumentMatchers.<RowMapper<BureauService.Snapshot>>any(),
                        eq(proposalId));
    }

    private static ProposalView proposal(UUID id, String status) {
        return new ProposalView(id, null, new BigDecimal("8000.00"), "BRL", status, null);
    }

    private static BureauReport syntheticReport() {
        return new BureauReport("FIXTURE", "CREDIT_BUREAU", "MOCK", "AVAILABLE", null, "3.0.0", "provider-id",
                new BureauReport.Score(640, 0, 1000, "demo"), null, List.of("Synthetic fixture"));
    }
}
