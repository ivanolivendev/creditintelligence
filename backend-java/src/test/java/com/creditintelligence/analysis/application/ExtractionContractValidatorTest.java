package com.creditintelligence.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.creditintelligence.analysis.domain.FinancialEngine.BankTransaction;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.DocumentResult;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ExtractedField;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ExtractedTransaction;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient.ProcessResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractionContractValidatorTest {
    private static final UUID PROPOSAL = new UUID(0, 1);
    private static final UUID ANALYSIS = new UUID(0, 2);
    private static final UUID ATTEMPT = new UUID(0, 3);
    private static final UUID OTHER = new UUID(0, 4);
    private static final DocumentSnapshotRef PAYSLIP = document(5, DocumentType.PAYSLIP, "2026-09");
    private static final DocumentSnapshotRef BANK = document(6, DocumentType.BANK_STATEMENT, "2026-09");
    private static final DocumentSnapshotRef ANOTHER_BANK = document(7, DocumentType.BANK_STATEMENT, "2026-08");
    private static final DocumentSnapshotRef PROOF = document(8, DocumentType.INCOME_PROOF, null);
    private static final DocumentSnapshotRef IDENTITY = document(9, DocumentType.IDENTIFICATION, null);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExtractionContractValidator validator = new ExtractionContractValidator();

    @Test
    void preservesLegacyIncomeEvidenceAndAllowsIdentificationWithoutFields() {
        var income = field(PROOF, "monthlyIncome", "7000.00", "BRL", 1);
        var result = validator.validate(response("1.0", List.of(
                new DocumentResult(IDENTITY.id(), IDENTITY.version(), List.of()),
                new DocumentResult(PROOF.id(), PROOF.version(), List.of(income), null))),
                ANALYSIS, ATTEMPT, false, List.of(PROOF, IDENTITY));

        assertThat(result.fields()).containsExactly(new FieldView(PROOF.id(), 2, "monthlyIncome",
                "7000.00", "BRL", "EXTRACTED", reference(PROOF, 1, "monthlyIncome"), 1,
                "Sintético: 7000.00"));
        assertThat(result.transactions()).isEmpty();
    }

    @Test
    void bindsFinancialEvidenceAndQualifiesTransactionIdentifiersByDocument() {
        var payable = field(PAYSLIP, "netSalary", "8000.00", "BRL", 1);
        var competence = field(PAYSLIP, "competence", "2026-09", null, 1);
        var period = field(BANK, "statementPeriod", "2026-09", null, 1);
        var first = transaction(BANK, "tx1", "CREDIT", "0.00", "UNKNOWN");
        var second = transaction(ANOTHER_BANK, "tx1", "DEBIT", "300.00", "RECURRING_PAYMENT");

        var result = validate(List.of(
                new DocumentResult(BANK.id(), BANK.version(), List.of(period), List.of(first)),
                new DocumentResult(PAYSLIP.id(), PAYSLIP.version(), List.of(payable, competence)),
                new DocumentResult(ANOTHER_BANK.id(), ANOTHER_BANK.version(), List.of(), List.of(second))),
                List.of(PAYSLIP, BANK, ANOTHER_BANK));

        assertThat(result.fields()).containsExactly(
                new FieldView(BANK.id(), BANK.version(), "statementPeriod", "2026-09", null,
                        "EXTRACTED", reference(BANK, 1, "statementPeriod"), 1, "Sintético: 2026-09"),
                new FieldView(PAYSLIP.id(), PAYSLIP.version(), "netSalary", "8000.00", "BRL",
                        "EXTRACTED", reference(PAYSLIP, 1, "netSalary"), 1, "Sintético: 8000.00"),
                new FieldView(PAYSLIP.id(), PAYSLIP.version(), "competence", "2026-09", null,
                        "EXTRACTED", reference(PAYSLIP, 1, "competence"), 1, "Sintético: 2026-09"));
        assertThat(result.transactions()).containsExactly(
                new BankTransaction(BANK.id() + ":tx1", "2026-09", "2026-09-28", "CREDIT",
                        new BigDecimal("0.00"), "UNKNOWN", reference(BANK, 1, "tx1"), "Lançamento sintético"),
                new BankTransaction(ANOTHER_BANK.id() + ":tx1", "2026-08", "2026-08-28", "DEBIT",
                        new BigDecimal("300.00"), "RECURRING_PAYMENT", reference(ANOTHER_BANK, 1, "tx1"),
                        "Lançamento sintético"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "AMBIGUOUS", "INVALID"})
    void preservesUncertainFieldsWithoutFactOrEvidence(String status) {
        var uncertain = new ExtractedField(PAYSLIP.id(), PAYSLIP.version(), "netSalary", null,
                null, status, null, null, null);

        var result = validateField(PAYSLIP, uncertain);

        assertThat(result.fields()).containsExactly(new FieldView(PAYSLIP.id(), PAYSLIP.version(),
                "netSalary", null, null, status, null, null, null));
    }

    @Test
    void retainsExplicitZeroPayrollDeductionAsFactualEvidence() {
        var deduction = field(PAYSLIP, "payrollDeduction", "0.00", "BRL", 1);

        var result = validateField(PAYSLIP, deduction);

        assertThat(result.fields()).containsExactly(new FieldView(PAYSLIP.id(), PAYSLIP.version(),
                "payrollDeduction", "0.00", "BRL", "EXTRACTED", reference(PAYSLIP, 1, "payrollDeduction"),
                1, "Sintético: 0.00"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContracts")
    void rejectsResponseThatDoesNotBelongToTheActiveContract(String scenario, ProcessResponse response) {
        assertThatThrownBy(() -> validator.validate(response, ANALYSIS, ATTEMPT, true, List.of(PAYSLIP)))
                .isInstanceOf(InvalidExtractionException.class)
                .hasMessage("Invalid extraction contract");
    }

    private static Stream<Arguments> invalidContracts() {
        var result = new DocumentResult(PAYSLIP.id(), PAYSLIP.version(), List.of());
        return Stream.of(
                arguments("missing response", null),
                arguments("legacy version for a financial analysis", response("1.0", List.of(result))),
                arguments("different analysis", new ProcessResponse("2.0", OTHER, ATTEMPT, List.of(result))),
                arguments("different attempt", new ProcessResponse("2.0", ANALYSIS, OTHER, List.of(result))),
                arguments("missing document results", response("2.0", null)),
                arguments("incomplete document results", response("2.0", List.of())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDocumentResults")
    void rejectsInvalidSnapshotDocumentResults(String scenario, List<DocumentResult> results, String message) {
        assertThatThrownBy(() -> validate(results, List.of(PAYSLIP, BANK)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage(message);
    }

    private static Stream<Arguments> invalidDocumentResults() {
        var valid = new DocumentResult(PAYSLIP.id(), PAYSLIP.version(), List.of());
        return Stream.of(
                arguments("missing field list",
                        List.of(valid, new DocumentResult(BANK.id(), BANK.version(), null)),
                        "Duplicate or missing document result"),
                arguments("duplicate result", List.of(valid, valid), "Duplicate or missing document result"),
                arguments("unbound document",
                        List.of(valid, new DocumentResult(OTHER, BANK.version(), List.of())),
                        "Document outside snapshot"),
                arguments("unbound version",
                        List.of(valid, new DocumentResult(BANK.id(), BANK.version() + 1, List.of())),
                        "Document outside snapshot"));
    }

    @Test
    void rejectsNullDocumentAndFieldEntries() {
        assertThatThrownBy(() -> validate(Collections.singletonList(null), List.of(PAYSLIP)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Duplicate or missing document result");
        assertThatThrownBy(() -> validateField(PAYSLIP, null))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Field outside snapshot or schema");
    }

    @ParameterizedTest(name = "invalid extracted field {0}={1}")
    @MethodSource("invalidFieldProperties")
    void rejectsUnboundAndMalformedExtractedFields(String property, Object replacement, String message) {
        var valid = field(PAYSLIP, "netSalary", "8000.00", "BRL", 1);
        var invalid = replace(valid, ExtractedField.class, property, replacement);

        assertThatThrownBy(() -> validateField(PAYSLIP, invalid))
                .isInstanceOf(InvalidExtractionException.class).hasMessage(message);
    }

    private static Stream<Arguments> invalidFieldProperties() {
        var schema = "Field outside snapshot or schema";
        var evidence = "Invalid income evidence";
        return Stream.of(
                arguments("documentId", OTHER, schema),
                arguments("version", 3, schema),
                arguments("fieldName", "monthlyIncome", schema),
                arguments("fieldName", null, schema),
                arguments("extractionStatus", "GUESSED", schema),
                arguments("extractionStatus", null, schema),
                arguments("value", null, evidence),
                arguments("currency", "USD", evidence),
                arguments("page", null, evidence),
                arguments("page", 0, evidence),
                arguments("snippet", null, evidence),
                arguments("snippet", " ", evidence),
                arguments("evidenceRef", reference(PAYSLIP, 2, "netSalary"), evidence),
                arguments("value", "-0.01", "Invalid monetary value"),
                arguments("value", "unknown", "Invalid monetary value"));
    }

    @Test
    void rejectsPeriodWithCurrencyOrValueContradictingSnapshot() {
        var wrongCurrency = field(PAYSLIP, "competence", "2026-09", "BRL", 1);
        var wrongPeriod = field(BANK, "statementPeriod", "2026-08", null, 1);

        assertThatThrownBy(() -> validateField(PAYSLIP, wrongCurrency))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Invalid income evidence");
        assertThatThrownBy(() -> validateField(BANK, wrongPeriod))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Document period differs from metadata");
    }

    @Test
    void rejectsFieldsUnsupportedByTheDocumentType() {
        assertThatThrownBy(() -> validateField(IDENTITY, field(IDENTITY, "monthlyIncome", "8000", "BRL", 1)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Field outside snapshot or schema");
        assertThatThrownBy(() -> validateField(PROOF, field(PROOF, "netSalary", "8000", "BRL", 1)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Field outside snapshot or schema");
        assertThatThrownBy(() -> validateField(BANK, field(BANK, "competence", "2026-09", null, 1)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Field outside snapshot or schema");
    }

    @Test
    void rejectsFactOrEvidenceOnAnUncertainField() {
        var value = new ExtractedField(PAYSLIP.id(), 2, "netSalary", "8000.00", "BRL", "AMBIGUOUS",
                null, null, null);
        var evidence = new ExtractedField(PAYSLIP.id(), 2, "netSalary", null, null, "MISSING",
                reference(PAYSLIP, 1, "netSalary"), 1, "Sintético");

        assertThatThrownBy(() -> validateField(PAYSLIP, value))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Uncertain field cannot contain fact or evidence");
        assertThatThrownBy(() -> validateField(PAYSLIP, evidence))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Uncertain field cannot contain fact or evidence");
    }

    @ParameterizedTest(name = "invalid bank transaction {0}={1}")
    @MethodSource("invalidTransactionProperties")
    void rejectsUnboundAndMalformedBankTransactions(String property, Object replacement, String message) {
        var valid = transaction(BANK, "tx1", "CREDIT", "8000.00", "POSSIBLE_SALARY");
        var invalid = replace(valid, ExtractedTransaction.class, property, replacement);

        assertThatThrownBy(() -> validateTransactions(BANK, List.of(invalid)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage(message);
    }

    private static Stream<Arguments> invalidTransactionProperties() {
        var evidence = "Invalid bank transaction evidence";
        var invalidValue = "Invalid bank transaction value or period";
        var invalidFormat = "Invalid bank transaction value or date";
        var missingValue = "Missing bank transaction value or period";
        return Stream.of(
                arguments("documentId", OTHER, evidence),
                arguments("version", 3, evidence),
                arguments("page", null, evidence),
                arguments("page", 0, evidence),
                arguments("transactionId", null, evidence),
                arguments("transactionId", "tx0", evidence),
                arguments("snippet", null, evidence),
                arguments("snippet", " ", evidence),
                arguments("descriptionOriginal", null, evidence),
                arguments("descriptionOriginal", " ", evidence),
                arguments("currency", "USD", evidence),
                arguments("direction", null, evidence),
                arguments("direction", "REVERSAL", evidence),
                arguments("classification", null, evidence),
                arguments("classification", "CERTAIN_SALARY", evidence),
                arguments("evidenceRef", reference(BANK, 2, "tx1"), evidence),
                arguments("amount", null, missingValue),
                arguments("postedAt", null, missingValue),
                arguments("amount", "-0.01", invalidValue),
                arguments("postedAt", "2026-08-28", invalidValue),
                arguments("amount", "not money", invalidFormat),
                arguments("postedAt", "2026-09-31", invalidFormat));
    }

    @Test
    void rejectsBankTransactionsFromNonBankDocumentsOrWithoutPeriodMetadata() {
        var nonBankTransaction = transaction(PAYSLIP, "tx1", "CREDIT", "8000.00", "POSSIBLE_SALARY");
        var noPeriod = document(10, DocumentType.BANK_STATEMENT, null);
        var missingPeriod = replace(transaction(noPeriod, "tx1", "CREDIT", "8000.00", "POSSIBLE_SALARY"),
                ExtractedTransaction.class, "postedAt", "2026-09-28");

        assertThatThrownBy(() -> validateTransactions(PAYSLIP, List.of(nonBankTransaction)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Invalid bank transaction evidence");
        assertThatThrownBy(() -> validateTransactions(noPeriod, List.of(missingPeriod)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Missing bank transaction value or period");
        assertThatThrownBy(() -> validateTransactions(BANK, Collections.singletonList(null)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Invalid bank transaction evidence");
    }

    @Test
    void rejectsRepeatedTransactionIdentifiersWithinOneDocument() {
        var transaction = transaction(BANK, "tx1", "CREDIT", "8000.00", "POSSIBLE_SALARY");

        assertThatThrownBy(() -> validateTransactions(BANK, List.of(transaction, transaction)))
                .isInstanceOf(InvalidExtractionException.class).hasMessage("Duplicate transaction ID");
    }

    private ExtractionContractValidator.ValidatedExtraction validateField(DocumentSnapshotRef document,
            ExtractedField field) {
        return validate(List.of(new DocumentResult(document.id(), document.version(),
                Collections.singletonList(field))), List.of(document));
    }

    private void validateTransactions(DocumentSnapshotRef document, List<ExtractedTransaction> transactions) {
        validate(List.of(new DocumentResult(document.id(), document.version(), List.of(), transactions)), List.of(document));
    }

    private ExtractionContractValidator.ValidatedExtraction validate(List<DocumentResult> results,
            List<DocumentSnapshotRef> documents) {
        return validator.validate(response("2.0", results), ANALYSIS, ATTEMPT, true, documents);
    }

    private static ProcessResponse response(String version, List<DocumentResult> results) {
        return new ProcessResponse(version, ANALYSIS, ATTEMPT, results);
    }

    private static DocumentSnapshotRef document(long number, DocumentType type, String period) {
        return new DocumentSnapshotRef(new UUID(0, number), PROPOSAL, type, 2,
                "fixtures/document-" + number + ".pdf", "synthetic-sha256-" + number, period);
    }

    private static ExtractedField field(DocumentSnapshotRef document, String name, String value, String currency,
            int page) {
        return new ExtractedField(document.id(), document.version(), name, value, currency, "EXTRACTED",
                reference(document, page, name), page, "Sintético: " + value);
    }

    private static ExtractedTransaction transaction(DocumentSnapshotRef document, String id, String direction,
            String amount, String classification) {
        return new ExtractedTransaction(document.id(), document.version(), id, document.period() + "-28",
                direction, amount, "BRL", "Lançamento sintético", classification, reference(document, 1, id),
                1, "Linha sintética do extrato");
    }

    private static String reference(DocumentSnapshotRef document, int page, String field) {
        return document.id() + ":v" + document.version() + ":p" + page + ":" + field;
    }

    private static <T> T replace(T fixture, Class<T> type, String property, Object value) {
        ObjectNode payload = JSON.valueToTree(fixture);
        payload.set(property, JSON.valueToTree(value));
        return JSON.convertValue(payload, type);
    }
}
