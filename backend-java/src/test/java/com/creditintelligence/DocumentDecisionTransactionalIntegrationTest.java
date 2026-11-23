package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.decision.application.DecisionService;
import com.creditintelligence.decision.domain.DecisionOutcome;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.proposal.application.ProposalService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "document.storage.mode=local")
class DocumentDecisionTransactionalIntegrationTest extends PostgresIntegrationTest {
    @Autowired ProposalService proposals;
    @Autowired DocumentService documents;
    @Autowired AnalysisService analyses;
    @Autowired DecisionService decisions;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PythonDocumentClient python;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rolledBackUploadLosesBothMetadataAndObject() throws Exception {
        var proposalId = createProposal();
        var bytes = Files.readAllBytes(sharedFixture("income-proof-demo.pdf"));

        var storedPath = transactions.execute(transaction -> {
            var uploaded = documents.upload(proposalId, DocumentType.INCOME_PROOF, "application/pdf", bytes);
            assertThat(documents.list(proposalId)).containsExactly(uploaded);
            var snapshot = documents.snapshot(proposalId).getFirst();
            var path = Path.of("target", "test-documents").resolve(snapshot.objectKey());
            assertThat(path).hasBinaryContent(bytes);
            transaction.setRollbackOnly();
            return path;
        });

        assertThat(storedPath).doesNotExist();
        assertThat(documents.list(proposalId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_metadata WHERE proposal_id = ?",
                Integer.class, proposalId)).isZero();
    }

    @Test
    void auditFailureRollsBackDecisionAndProposalStatusTogether() throws Exception {
        var proposalId = createProposal();
        var document = documents.upload(proposalId, DocumentType.INCOME_PROOF, "application/pdf",
                Files.readAllBytes(sharedFixture("income-proof-demo.pdf")));
        when(python.process(any(UUID.class), any(UUID.class), anyList(), anyString(), anyString()))
                .thenAnswer(call -> {
                    List<DocumentSnapshotRef> snapshot = call.getArgument(2);
                    var referenced = snapshot.getFirst();
                    var field = new PythonDocumentClient.ExtractedField(referenced.id(), referenced.version(),
                            "monthlyIncome", "7500.00", "BRL", "EXTRACTED",
                            referenced.id() + ":v" + referenced.version() + ":p1:monthlyIncome", 1,
                            "Synthetic monthly income: 7500.00");
                    return new PythonDocumentClient.ProcessResponse("1.0", call.getArgument(0), call.getArgument(1),
                            List.of(new PythonDocumentClient.DocumentResult(referenced.id(), referenced.version(),
                                    List.of(field))));
                });
        var analysis = analyses.analyze(proposalId, "corr-analysis", "decision-transaction");
        assertThat(analysis.status()).isEqualTo("COMPLETED");
        assertThat(analysis.documents()).singleElement().satisfies(ref ->
                assertThat(ref.documentId()).isEqualTo(document.id()));
        var statusBeforeDecision = proposals.get(proposalId).status();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "synthetic-analyst", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ANALYST"))));

        assertThatThrownBy(() -> decisions.decide(proposalId, analysis.analysisId(), analysis.documentSnapshotHash(),
                DecisionOutcome.APPROVED, "Após revisão", "x".repeat(65)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(decisions.list(proposalId)).isEmpty();
        assertThat(proposals.get(proposalId).status()).isEqualTo(statusBeforeDecision);
        assertThat(decisionAuditCount(proposalId)).isZero();

        var committed = decisions.decide(proposalId, analysis.analysisId(), analysis.documentSnapshotHash(),
                DecisionOutcome.APPROVED, "  Após revisão  ", "corr-decision");

        assertThat(proposals.get(proposalId).status()).isEqualTo("APPROVED");
        assertThat(decisions.list(proposalId)).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isEqualTo(committed.id());
            assertThat(saved.analysisId()).isEqualTo(analysis.analysisId());
            assertThat(saved.documentSnapshotHash()).isEqualTo(analysis.documentSnapshotHash());
            assertThat(saved.analystId()).isEqualTo("synthetic-analyst");
            assertThat(saved.reason()).isEqualTo("Após revisão");
        });
        assertThat(jdbc.queryForMap("""
                SELECT actor_id, correlation_id, metadata_json FROM audit_record
                WHERE aggregate_id = ? AND action = 'DECISION_RECORDED'
                """, proposalId))
                .containsEntry("actor_id", "synthetic-analyst")
                .containsEntry("correlation_id", "corr-decision")
                .containsEntry("metadata_json", "{\"analysisId\":\"" + analysis.analysisId() + "\",\"outcome\":\"APPROVED\"}");
    }

    private int decisionAuditCount(UUID proposalId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_record WHERE aggregate_id = ? AND action = 'DECISION_RECORDED'",
                Integer.class, proposalId);
    }

    private UUID createProposal() {
        return proposals.create("Synthetic Applicant", null, new BigDecimal("8000.00"), "BRL").id();
    }
}
