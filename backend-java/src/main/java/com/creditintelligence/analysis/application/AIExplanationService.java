package com.creditintelligence.analysis.application;

import com.creditintelligence.analysis.infrastructure.AnalysisDataStore;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplainRequest;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.ExplainResponse;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.FindingContext;
import com.creditintelligence.policy.application.PolicySnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AIExplanationService {
    private final PythonExplanationClient python;
    private final AnalysisDataStore data;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String expectedProvider;

    @Autowired
    public AIExplanationService(PythonExplanationClient python, AnalysisDataStore data,
            JdbcTemplate jdbc, ObjectMapper json) {
        this(python, data, jdbc, json, "mock");
    }

    public AIExplanationService(PythonExplanationClient python, AnalysisDataStore data,
            JdbcTemplate jdbc, ObjectMapper json, @Value("${ai.provider:mock}") String expectedProvider) {
        this.python = python;
        this.data = data;
        this.jdbc = jdbc;
        this.json = json;
        this.expectedProvider = expectedProvider.toUpperCase();
    }

    public void generate(UUID analysisId, PolicySnapshot policy, String correlationId) {
        var findings = data.findings(analysisId);
        var assessment = data.financialAssessment(analysisId);
        var evidence = java.util.stream.Stream.concat(
                data.fields(analysisId).stream().map(FieldView::evidenceRef),
                assessment == null ? java.util.stream.Stream.<String>empty()
                        : assessment.findings().stream().flatMap(f -> f.evidenceRefs().stream()))
                .filter(Objects::nonNull).distinct().toList();
        var policyRefs = findings.stream().map(finding -> policy.version() + ":" + finding.code()).toList();
        var request = new ExplainRequest("1.0", analysisId, policy.version(), policy.checksum(), policy.text(),
                findings.stream().map(finding -> new FindingContext(finding.code(), finding.result(),
                        finding.details(), finding.evidenceRef())).toList(),
                evidence, policyRefs, correlationId, assessment,
                data.transactionReferences(analysisId));
        try {
            var response = python.explain(request);
            validate(request, response);
            save(analysisId, "AVAILABLE", response);
        } catch (InvalidExtractionException ex) {
            save(analysisId, "INVALID_OUTPUT", null);
        } catch (RuntimeException ex) {
            save(analysisId, "UNAVAILABLE", null);
        }
    }

    @Transactional(readOnly = true)
    public AIExplanationView get(UUID analysisId) {
        try {
            return jdbc.queryForObject("""
                    SELECT status, provider, model_id, prompt_version, content_json
                    FROM ai_explanation WHERE analysis_id = ?
                    """, (rs, row) -> {
                var status = rs.getString(1);
                if (!"AVAILABLE".equals(status)) return AIExplanationView.withoutContent(status);
                try {
                    var response = json.readValue(rs.getString(5), ExplainResponse.class);
                    return new AIExplanationView(status, rs.getString(2), rs.getString(3),
                            rs.getString(4), response.summary(), response.explanations(),
                            response.hypotheses(), response.limitations());
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Stored explanation cannot be read", ex);
                }
            }, analysisId);
        } catch (EmptyResultDataAccessException ex) {
            return AIExplanationView.withoutContent("NOT_REQUESTED");
        }
    }

    private void validate(ExplainRequest request, ExplainResponse response) {
        if (response == null || !"1.0".equals(response.contractVersion())
                || !request.analysisId().equals(response.analysisId())
                || !Set.of("MOCK", "BEDROCK").contains(response.provider())
                || (!"MOCK".equals(expectedProvider) && !expectedProvider.equals(response.provider()))
                || blank(response.modelId())
                || blank(response.promptVersion()) || blank(response.summary())
                || response.explanations() == null || response.limitations() == null
                || response.explanations().size() != request.findings().size()
                || response.hypotheses() == null) {
            throw new InvalidExtractionException("Invalid explanation schema");
        }
        for (var hypothesis : response.hypotheses()) {
            if (hypothesis == null || blank(hypothesis.type()) || blank(hypothesis.description())
                    || blank(hypothesis.reason()) || !"PENDING_HUMAN_REVIEW".equals(hypothesis.reviewStatus())
                    || hypothesis.transactionRefs() == null || hypothesis.evidenceRefs() == null
                    || !request.allowedTransactionRefs().containsAll(hypothesis.transactionRefs())
                    || !request.allowedEvidenceRefs().containsAll(hypothesis.evidenceRefs())) {
                throw new InvalidExtractionException("Hypothesis contains unsupported reference");
            }
        }
        Set<String> findingCodes = new HashSet<>();
        for (var item : response.explanations()) {
            if (item == null || blank(item.findingCode()) || blank(item.message())
                    || item.policyReferences() == null || item.evidenceReferences() == null
                    || !findingCodes.add(item.findingCode())
                    || request.findings().stream().noneMatch(finding -> finding.code().equals(item.findingCode()))
                    || !request.allowedPolicyRefs().containsAll(item.policyReferences())
                    || !request.allowedEvidenceRefs().containsAll(item.evidenceReferences())) {
                throw new InvalidExtractionException("Explanation contains unsupported reference");
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void save(UUID analysisId, String status, ExplainResponse response) {
        try {
            jdbc.update("""
                    INSERT INTO ai_explanation
                    (analysis_id, status, provider, model_id, prompt_version, content_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (analysis_id) DO NOTHING
                    """, analysisId, status, response == null ? null : response.provider(),
                    response == null ? null : response.modelId(),
                    response == null ? null : response.promptVersion(),
                    response == null ? null : json.writeValueAsString(response), OffsetDateTime.now(ZoneOffset.UTC));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Explanation could not be serialized", ex);
        }
    }
}
