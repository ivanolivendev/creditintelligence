package com.creditintelligence.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditintelligence.analysis.application.ExtractionContractValidator.ValidatedExtraction;
import com.creditintelligence.analysis.domain.FinancialEngine.BankTransaction;
import com.creditintelligence.analysis.domain.FinancialEngine.IncomePeriod;
import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancialProfileAssemblerTest {
    private static final UUID PROPOSAL = new UUID(0, 1);
    private static final DocumentSnapshotRef FIRST_PAYSLIP = document(2, DocumentType.PAYSLIP, "2026-08");
    private static final DocumentSnapshotRef SECOND_PAYSLIP = document(3, DocumentType.PAYSLIP, "2026-09");
    private static final DocumentSnapshotRef THIRD_PAYSLIP = document(4, DocumentType.PAYSLIP, "2026-10");
    private static final DocumentSnapshotRef FIRST_BANK = document(5, DocumentType.BANK_STATEMENT, "2026-08");
    private static final DocumentSnapshotRef SECOND_BANK = document(6, DocumentType.BANK_STATEMENT, "2026-09");
    private static final DocumentSnapshotRef THIRD_BANK = document(7, DocumentType.BANK_STATEMENT, "2026-10");
    private static final DocumentSnapshotRef PROOF = document(8, DocumentType.INCOME_PROOF, null);

    private final FinancialProfileAssembler assembler = new FinancialProfileAssembler();

    @Test
    void preservesFactualAmountsAndEvidenceFromEachPayslip() {
        var firstNet = extracted(FIRST_PAYSLIP, "netSalary", "8000.00", "BRL");
        var firstDebt = extracted(FIRST_PAYSLIP, "payrollDeduction", "300.00", "BRL");
        var secondNet = extracted(SECOND_PAYSLIP, "netSalary", "8500.50", "BRL");
        var secondDebt = extracted(SECOND_PAYSLIP, "payrollDeduction", "200.00", "BRL");
        var thirdNet = extracted(THIRD_PAYSLIP, "netSalary", "9000.00", "BRL");
        var fields = List.of(secondNet, firstDebt, firstNet, secondDebt, thirdNet,
                extracted(SECOND_PAYSLIP, "grossSalary", "9200.00", "BRL"),
                extracted(FIRST_PAYSLIP, "grossSalary", "8700.00", "BRL"),
                extracted(THIRD_PAYSLIP, "grossSalary", "9800.00", "BRL"),
                extracted(PROOF, "monthlyIncome", "9999.00", "BRL"),
                extracted(FIRST_BANK, "statementPeriod", "2026-08", null),
                extracted(SECOND_BANK, "statementPeriod", "2026-09", null),
                extracted(THIRD_BANK, "statementPeriod", "2026-10", null));
        var credit = new BankTransaction(FIRST_BANK.id() + ":tx1", "2026-08", "2026-08-28", "CREDIT",
                new BigDecimal("8000.00"), "POSSIBLE_SALARY", "bank-credit", "Crédito sintético");
        var bureau = new BureauReport("SYNTHETIC", "CREDIT", "TEST", "AVAILABLE",
                OffsetDateTime.parse("2026-11-22T12:00:00Z"), "1.0", "demo-ref",
                new BureauReport.Score(700, 0, 1000, "DEMO"), new BureauReport.Restrictions(0),
                List.of("Bureau fictício de teste"));

        var profile = assembler.assemble(List.of(FIRST_BANK, FIRST_PAYSLIP, SECOND_PAYSLIP, PROOF,
                SECOND_BANK, THIRD_PAYSLIP, THIRD_BANK), new ValidatedExtraction(fields, List.of(credit)), bureau);

        assertThat(profile.incomePeriods()).containsExactly(
                new IncomePeriod("2026-08", new BigDecimal("8700.00"), new BigDecimal("8000.00"),
                        new BigDecimal("300.00"), firstNet.evidenceRef(), firstDebt.evidenceRef()),
                new IncomePeriod("2026-09", new BigDecimal("9200.00"), new BigDecimal("8500.50"),
                        new BigDecimal("200.00"), secondNet.evidenceRef(), secondDebt.evidenceRef()),
                new IncomePeriod("2026-10", new BigDecimal("9800.00"), new BigDecimal("9000.00"),
                        null, thirdNet.evidenceRef(), null));
        assertThat(profile.bankTransactions()).containsExactly(credit);
        assertThat(profile.limitations()).isEmpty();
        assertThat(profile.bureau()).isEqualTo(bureau);
    }

    @Test
    void keepsAbsentAndNonfactualAmountsUncertainAndRecordsTheirPeriods() {
        var fields = List.of(
                extracted(FIRST_PAYSLIP, "competence", "2026-08", null),
                uncertain(FIRST_PAYSLIP, "netSalary", "AMBIGUOUS"),
                uncertain(FIRST_PAYSLIP, "payrollDeduction", "INVALID"),
                uncertain(SECOND_PAYSLIP, "grossSalary", "MISSING"),
                extracted(SECOND_PAYSLIP, "payrollDeduction", "250.00", "BRL"),
                extracted(FIRST_BANK, "statementPeriod", "2026-08", null),
                uncertain(SECOND_BANK, "statementPeriod", "INVALID"));
        var extraction = new ValidatedExtraction(fields, List.of());

        var profile = assembler.assemble(List.of(FIRST_PAYSLIP, SECOND_PAYSLIP, FIRST_BANK, SECOND_BANK,
                THIRD_BANK), extraction, null);

        assertThat(profile.incomePeriods()).containsExactly(
                new IncomePeriod("2026-08", null, null, null, null, null),
                new IncomePeriod("2026-09", null, null, new BigDecimal("250.00"), null,
                        reference(SECOND_PAYSLIP, "payrollDeduction")));
        assertThat(profile.limitations()).containsExactly(
                "Net salary not factual for period 2026-08",
                "Net salary not factual for period 2026-09",
                "Statement period not factual for 2026-09",
                "Statement period not factual for 2026-10");
        assertThat(profile.bankTransactions()).isEmpty();
        assertThat(profile.bureau()).isNull();
    }

    private static DocumentSnapshotRef document(long number, DocumentType type, String period) {
        return new DocumentSnapshotRef(new UUID(0, number), PROPOSAL, type, 1,
                "fixture.pdf", "synthetic-checksum", period);
    }

    private static FieldView extracted(DocumentSnapshotRef document, String field, String value, String currency) {
        return new FieldView(document.id(), document.version(), field, value, currency, "EXTRACTED",
                reference(document, field), 1, "Valor sintético: " + value);
    }

    private static FieldView uncertain(DocumentSnapshotRef document, String field, String status) {
        return new FieldView(document.id(), document.version(), field, null, null, status, null, null, null);
    }

    private static String reference(DocumentSnapshotRef document, String field) {
        return document.id() + ":v" + document.version() + ":p1:" + field;
    }
}
