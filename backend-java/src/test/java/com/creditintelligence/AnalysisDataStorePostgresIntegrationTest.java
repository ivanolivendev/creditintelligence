package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditintelligence.analysis.application.FieldView;
import com.creditintelligence.analysis.application.FindingView;
import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.analysis.infrastructure.AnalysisDataStore;
import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AnalysisDataStorePostgresIntegrationTest extends PostgresIntegrationTest {
    private static final UUID POLICY_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired AnalysisDataStore store;
    @Autowired JdbcTemplate jdbc;

    @Test
    void roundTripsAssessmentProfileAndTransactionReferencesThroughPostgresJsonb() {
        var ids = seedAggregate();
        var tx = new FinancialEngine.BankTransaction("tx1", "2026-10", "2026-10-10", "CREDIT",
                new BigDecimal("8000.00"), "POSSIBLE_SALARY", "statement:v1:p1:tx1");
        var profile = new FinancialEngine.FinancialProfile(List.of(
                new FinancialEngine.IncomePeriod("2026-10", new BigDecimal("9000.00"),
                        new BigDecimal("8000.00"), new BigDecimal("500.00"), "payslip:v1:p1:net", null)),
                List.of(tx), List.of("synthetic limitation"));
        var assessment = assessment();

        store.saveFinancialAssessment(ids.analysisId(), POLICY_ID, null, profile, assessment);

        var loaded = store.financialAssessment(ids.analysisId());
        assertThat(loaded.reviewStatus()).isEqualTo("MORE_INFO_REQUIRED");
        assertThat(loaded.findings()).extracting(FinancialEngine.Finding::code)
                .containsExactly("PAYMENT_CAPACITY");
        assertThat(store.transactionReferences(ids.analysisId())).containsExactly("tx1");
    }

    @Test
    void persistsAndReadsSnapshotFieldsFindingsAndEvidenceUsingRealForeignKeys() {
        var ids = seedAggregate();
        var snapshot = new DocumentSnapshotRef(ids.documentId(), ids.proposalId(), DocumentType.INCOME_PROOF,
                1, "proposal/income.pdf", "a".repeat(64), null);

        store.saveSnapshot(ids.analysisId(), List.of(snapshot));
        store.saveField(ids.analysisId(), new FieldView(ids.documentId(), 1, "monthlyIncome", "8000.00", "BRL",
                "EXTRACTED", "doc:v1:p1:monthlyIncome", 1, "Renda mensal sintética"));
        store.saveFinding(ids.analysisId(), new FindingView("INCOME_CONSISTENCY", "1.0", "PASS",
                "Within tolerance", "doc:v1:p1:monthlyIncome"));

        assertThat(store.snapshot(ids.analysisId())).singleElement().satisfies(item -> {
            assertThat(item.documentId()).isEqualTo(ids.documentId());
            assertThat(item.sha256()).isEqualTo("a".repeat(64));
            assertThat(item.type()).isEqualTo("INCOME_PROOF");
        });
        assertThat(store.fields(ids.analysisId())).singleElement().satisfies(field -> {
            assertThat(field.fieldName()).isEqualTo("monthlyIncome");
            assertThat(field.value()).isEqualTo("8000.00");
            assertThat(field.evidenceRef()).isEqualTo("doc:v1:p1:monthlyIncome");
        });
        assertThat(store.findings(ids.analysisId())).singleElement().satisfies(finding -> {
            assertThat(finding.result()).isEqualTo("PASS");
            assertThat(finding.evidenceRef()).isEqualTo("doc:v1:p1:monthlyIncome");
        });
    }

    private SeedIds seedAggregate() {
        var proposalId = UUID.randomUUID();
        var applicantId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var analysisId = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO applicant (id, name, synthetic_identifier) VALUES (?, ?, ?)",
                applicantId, "Synthetic Applicant", "TEST-" + proposalId);
        jdbc.update("""
                INSERT INTO proposal (id, applicant_id, declared_monthly_income, currency, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, proposalId, applicantId, new BigDecimal("8000.00"), "BRL", "DRAFT", now, now);
        jdbc.update("""
                INSERT INTO document_metadata
                (id, proposal_id, type, object_key, sha256, document_version, uploaded_at, status, period_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, documentId, proposalId, "INCOME_PROOF", "proposal/" + documentId + ".pdf",
                "a".repeat(64), 1, now, "AVAILABLE", null);
        jdbc.update("""
                INSERT INTO analysis
                (id, proposal_id, status, attempt, lease_token, input_fingerprint,
                 document_snapshot_hash, policy_version_id, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, analysisId, proposalId, "RUNNING", 1, UUID.randomUUID(), "b".repeat(64),
                "c".repeat(64), POLICY_ID, now);
        return new SeedIds(proposalId, documentId, analysisId);
    }

    private FinancialEngine.Assessment assessment() {
        return new FinancialEngine.Assessment("BRL", "CREDIT_POLICY_DEMO_3.4", "3.4", "MORE_INFO_REQUIRED",
                1, new BigDecimal("8000.00"), new BigDecimal("8000.00"), new BigDecimal("8000.00"),
                BigDecimal.ZERO, List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, "UNKNOWN", null, null,
                null, List.of(new FinancialEngine.Finding("PAYMENT_CAPACITY", "NOT_EVALUATED",
                        "Capacity requires complete income and known obligations", List.of("payslip:v1:p1:net"))),
                List.of("synthetic limitation"), null);
    }

    private record SeedIds(UUID proposalId, UUID documentId, UUID analysisId) {}
}
