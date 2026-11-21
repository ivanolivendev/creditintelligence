package com.creditintelligence.analysis.infrastructure;

import com.creditintelligence.analysis.application.FieldView;
import com.creditintelligence.analysis.application.FindingView;
import com.creditintelligence.analysis.application.SnapshotView;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.analysis.domain.FinancialEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.EmptyResultDataAccessException;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisDataStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AnalysisDataStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void saveFinancialAssessment(UUID analysisId, UUID policyId, UUID bureauConsultationId,
            FinancialEngine.FinancialProfile profile, FinancialEngine.Assessment assessment) {
        try {
            jdbc.update("""
                    INSERT INTO financial_assessment
                    (analysis_id, policy_version_id, bureau_consultation_id, rule_version,
                     profile_json, result_json, created_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                    """, analysisId, policyId, bureauConsultationId, FinancialEngine.RULE_VERSION,
                    json.writeValueAsString(profile), json.writeValueAsString(assessment),
                    OffsetDateTime.now(ZoneOffset.UTC));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Financial assessment serialization failed", ex);
        }
    }

    public FinancialEngine.Assessment financialAssessment(UUID analysisId) {
        try {
            return jdbc.queryForObject("SELECT result_json::text FROM financial_assessment WHERE analysis_id = ?",
                    (rs, row) -> {
                        try {
                            return json.readValue(rs.getString(1), FinancialEngine.Assessment.class);
                        } catch (JsonProcessingException ex) {
                            throw new IllegalStateException("Stored financial assessment is invalid", ex);
                        }
                    }, analysisId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public List<String> transactionReferences(UUID analysisId) {
        try {
            return jdbc.queryForObject("SELECT profile_json::text FROM financial_assessment WHERE analysis_id = ?",
                    (rs, row) -> {
                        try {
                            var root = json.readTree(rs.getString(1));
                            var refs = new ArrayList<String>();
                            for (var tx : root.path("bankTransactions")) refs.add(tx.path("id").asText());
                            return refs;
                        } catch (JsonProcessingException ex) {
                            throw new IllegalStateException("Stored financial profile is invalid", ex);
                        }
                    }, analysisId);
        } catch (EmptyResultDataAccessException ex) {
            return List.of();
        }
    }

    public void saveSnapshot(UUID analysisId, List<DocumentSnapshotRef> documents) {
        for (var document : documents) {
            jdbc.update("""
                    INSERT INTO document_snapshot_item
                    (id, analysis_id, document_id, document_version, sha256, object_key)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), analysisId, document.id(), document.version(),
                    document.sha256(), document.objectKey());
        }
    }

    public void saveField(UUID analysisId, FieldView field) {
        jdbc.update("""
                INSERT INTO extracted_field
                (id, analysis_id, document_id, document_version, field_name, value_text,
                 currency, extraction_status, evidence_ref, page_number, snippet)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), analysisId, field.documentId(), field.documentVersion(),
                field.fieldName(), field.value(), field.currency(), field.extractionStatus(),
                field.evidenceRef(), field.page(), field.snippet());
    }

    public void saveFinding(UUID analysisId, FindingView finding) {
        jdbc.update("""
                INSERT INTO validation_finding
                (id, analysis_id, rule_code, rule_version, result, details, evidence_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), analysisId, finding.code(), finding.ruleVersion(),
                finding.result(), finding.details(), finding.evidenceRef());
    }

    public List<SnapshotView> snapshot(UUID analysisId) {
        return jdbc.query("""
                SELECT s.document_id, d.type, s.document_version, s.sha256, d.period_key
                FROM document_snapshot_item s JOIN document_metadata d ON d.id = s.document_id
                WHERE s.analysis_id = ? ORDER BY d.type
                """, (rs, row) -> new SnapshotView(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getInt(3), rs.getString(4).trim(), rs.getString(5)), analysisId);
    }

    public List<FieldView> fields(UUID analysisId) {
        return jdbc.query("""
                SELECT document_id, document_version, field_name, value_text, currency,
                       extraction_status, evidence_ref, page_number, snippet
                FROM extracted_field WHERE analysis_id = ? ORDER BY field_name, document_id
                """, (rs, row) -> new FieldView(rs.getObject(1, UUID.class), rs.getInt(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), (Integer) rs.getObject(8), rs.getString(9)), analysisId);
    }

    public List<FindingView> findings(UUID analysisId) {
        return jdbc.query("""
                SELECT rule_code, rule_version, result, details, evidence_ref
                FROM validation_finding WHERE analysis_id = ? ORDER BY rule_code
                """, (rs, row) -> new FindingView(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5)), analysisId);
    }
}
