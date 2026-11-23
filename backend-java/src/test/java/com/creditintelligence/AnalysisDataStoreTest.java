package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.creditintelligence.analysis.application.FieldView;
import com.creditintelligence.analysis.application.FindingView;
import com.creditintelligence.analysis.application.SnapshotView;
import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.analysis.infrastructure.AnalysisDataStore;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AnalysisDataStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AnalysisDataStore store = new AnalysisDataStore(jdbc, new ObjectMapper());

    @Test
    void missingFinancialAssessmentAndProfileReturnSafeEmptyValues() {
        var analysisId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(analysisId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(store.financialAssessment(analysisId)).isNull();
        assertThat(store.transactionReferences(analysisId)).isEmpty();
    }

    @Test
    void savesImmutableSnapshotReferenceWithItsVersionAndDigest() {
        var analysisId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var document = new DocumentSnapshotRef(documentId, UUID.randomUUID(), DocumentType.INCOME_PROOF, 2,
                "proposal/document.pdf", "abc123", null);

        store.saveSnapshot(analysisId, List.of(document));

        verify(jdbc).update(anyString(), any(UUID.class), eq(analysisId), eq(documentId), eq(2),
                eq("abc123"), eq("proposal/document.pdf"));
    }

    @Test
    void persistsFindingContractIncludingEvidenceReference() {
        var analysisId = UUID.randomUUID();
        var finding = new FindingView("INCOME_CONSISTENCY", "1.0", "WARNING", "Variance detected", "doc:v1:p1");

        store.saveFinding(analysisId, finding);

        verify(jdbc).update(anyString(), any(UUID.class), eq(analysisId), eq("INCOME_CONSISTENCY"), eq("1.0"),
                eq("WARNING"), eq("Variance detected"), eq("doc:v1:p1"));
    }

    @Test
    void reconstructsSnapshotFieldsAndFindingsFromStoredEvidence() throws Exception {
        var analysisId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var snapshot = mock(ResultSet.class);
        when(snapshot.getObject(1, UUID.class)).thenReturn(documentId);
        when(snapshot.getString(2)).thenReturn("PAYSLIP");
        when(snapshot.getInt(3)).thenReturn(2);
        when(snapshot.getString(4)).thenReturn("abc123 ");
        when(snapshot.getString(5)).thenReturn("2026-10");
        stubQueryRow("SELECT s.document_id", analysisId, snapshot);

        var evidence = documentId + ":v2:p1:netSalary";
        var field = mock(ResultSet.class);
        when(field.getObject(1, UUID.class)).thenReturn(documentId);
        when(field.getInt(2)).thenReturn(2);
        when(field.getString(3)).thenReturn("netSalary");
        when(field.getString(4)).thenReturn("8000.00");
        when(field.getString(5)).thenReturn("BRL");
        when(field.getString(6)).thenReturn("EXTRACTED");
        when(field.getString(7)).thenReturn(evidence);
        when(field.getObject(8)).thenReturn(1);
        when(field.getString(9)).thenReturn("Synthetic salary evidence");
        stubQueryRow("SELECT document_id, document_version", analysisId, field);

        var finding = mock(ResultSet.class);
        when(finding.getString(1)).thenReturn("INCOME_CONSISTENCY");
        when(finding.getString(2)).thenReturn("1.0");
        when(finding.getString(3)).thenReturn("PASS");
        when(finding.getString(4)).thenReturn("Within tolerance");
        when(finding.getString(5)).thenReturn(evidence);
        stubQueryRow("SELECT rule_code, rule_version", analysisId, finding);

        assertThat(store.snapshot(analysisId)).containsExactly(new SnapshotView(
                documentId, "PAYSLIP", 2, "abc123", "2026-10"));
        assertThat(store.fields(analysisId)).containsExactly(new FieldView(documentId, 2, "netSalary", "8000.00",
                "BRL", "EXTRACTED", evidence, 1, "Synthetic salary evidence"));
        assertThat(store.findings(analysisId)).containsExactly(new FindingView("INCOME_CONSISTENCY", "1.0", "PASS",
                "Within tolerance", evidence));
    }

    @Test
    void reconstructsStoredFinancialAssessmentTransactionReferencesAndBureauLink() throws Exception {
        var analysisId = UUID.randomUUID();
        var bureauId = UUID.randomUUID();
        var profile = new FinancialEngine.FinancialProfile(List.of(), List.of(), List.of("Synthetic missing income"));
        var assessment = new FinancialEngine().assess(profile, new FinancialEngine.Policy(
                new BigDecimal("0.30"), new BigDecimal("3.00"), new BigDecimal("0.10"), "SYNTHETIC"),
                new FinancialEngine.Scenario(null, 12, BigDecimal.ZERO, "BRL", "SYNTHETIC"));

        var assessmentRow = mock(ResultSet.class);
        when(assessmentRow.getString(1)).thenReturn(new ObjectMapper().writeValueAsString(assessment));
        stubSingleRow("SELECT result_json", analysisId, assessmentRow);
        var profileRow = mock(ResultSet.class);
        when(profileRow.getString(1)).thenReturn("""
                {"bankTransactions":[{"id":"synthetic-statement:tx1"},{"id":"synthetic-statement:tx2"}]}
                """);
        stubSingleRow("SELECT profile_json", analysisId, profileRow);
        var bureauRow = mock(ResultSet.class);
        when(bureauRow.getObject(1, UUID.class)).thenReturn(bureauId);
        stubSingleRow("SELECT bureau_consultation_id", analysisId, bureauRow);

        assertThat(store.financialAssessment(analysisId)).isEqualTo(assessment);
        assertThat(store.transactionReferences(analysisId)).containsExactly(
                "synthetic-statement:tx1", "synthetic-statement:tx2");
        assertThat(store.bureauConsultationId(analysisId)).isEqualTo(bureauId);
    }

    @Test
    void rejectsMalformedStoredFinancialJsonWithSpecificErrors() throws Exception {
        var analysisId = UUID.randomUUID();
        var corrupt = mock(ResultSet.class);
        when(corrupt.getString(1)).thenReturn("{");
        stubSingleRow("SELECT result_json", analysisId, corrupt);
        stubSingleRow("SELECT profile_json", analysisId, corrupt);

        assertThatThrownBy(() -> store.financialAssessment(analysisId)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored financial assessment is invalid");
        assertThatThrownBy(() -> store.transactionReferences(analysisId)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored financial profile is invalid");
    }

    @SuppressWarnings("unchecked")
    private void stubQueryRow(String sqlPrefix, UUID analysisId, ResultSet row) {
        when(jdbc.query(startsWith(sqlPrefix), any(RowMapper.class), eq(analysisId))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
    }

    @SuppressWarnings("unchecked")
    private void stubSingleRow(String sqlPrefix, UUID analysisId, ResultSet row) {
        when(jdbc.queryForObject(startsWith(sqlPrefix), any(RowMapper.class), eq(analysisId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return mapper.mapRow(row, 0);
                });
    }
}
