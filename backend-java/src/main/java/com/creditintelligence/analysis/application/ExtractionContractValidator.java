package com.creditintelligence.analysis.application;

import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.analysis.domain.FinancialEngine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ExtractionContractValidator {
    ValidatedExtraction validate(PythonDocumentClient.ProcessResponse response, UUID analysisId,
            UUID attemptId, boolean financial, List<DocumentSnapshotRef> snapshot) {
        if (response == null || !(financial ? "2.0" : "1.0").equals(response.contractVersion())
                || !analysisId.equals(response.analysisId()) || !attemptId.equals(response.attemptId())
                || response.documentResults() == null
                || response.documentResults().size() != snapshot.size()) {
            throw new InvalidExtractionException("Invalid extraction contract");
        }
        Set<UUID> seen = new HashSet<>();
        List<FieldView> fields = new ArrayList<>();
        List<FinancialEngine.BankTransaction> transactions = new ArrayList<>();
        for (var result : response.documentResults()) {
            if (result == null || result.fields() == null || !seen.add(result.documentId())) {
                throw new InvalidExtractionException("Duplicate or missing document result");
            }
            var bound = snapshot.stream()
                    .filter(document -> document.id().equals(result.documentId())
                            && document.version() == result.version())
                    .findFirst().orElseThrow(() -> new InvalidExtractionException("Document outside snapshot"));
            for (var field : result.fields()) fields.add(validateField(bound, field));
            if (result.transactions() != null) {
                for (var item : result.transactions()) transactions.add(validateTransaction(bound, item));
            }
        }
        if (transactions.stream().map(FinancialEngine.BankTransaction::id).distinct().count()
                != transactions.size()) {
            throw new InvalidExtractionException("Duplicate transaction ID");
        }
        return new ValidatedExtraction(fields, transactions);
    }

    private FieldView validateField(DocumentSnapshotRef bound, PythonDocumentClient.ExtractedField field) {
        if (field == null || !bound.id().equals(field.documentId()) || bound.version() != field.version()
                || field.fieldName() == null || !allowedField(bound, field.fieldName())
                || field.extractionStatus() == null
                || !Set.of("EXTRACTED", "MISSING", "AMBIGUOUS", "INVALID").contains(field.extractionStatus())) {
            throw new InvalidExtractionException("Field outside snapshot or schema");
        }
        if ("EXTRACTED".equals(field.extractionStatus())) {
            var expectedRef = bound.id() + ":v" + bound.version() + ":p" + field.page()
                    + ":" + field.fieldName();
            var monetary = Set.of("monthlyIncome", "grossSalary", "netSalary",
                    "payrollDeduction").contains(field.fieldName());
            if (field.value() == null || (monetary && !"BRL".equals(field.currency()))
                    || (!monetary && field.currency() != null) || field.page() == null
                    || field.page() < 1 || field.snippet() == null || field.snippet().isBlank()
                    || !expectedRef.equals(field.evidenceRef())) {
                throw new InvalidExtractionException("Invalid income evidence");
            }
            try {
                if (monetary && new BigDecimal(field.value()).signum() < 0) throw new NumberFormatException();
                if (!monetary && !field.value().equals(bound.period())) {
                    throw new InvalidExtractionException("Document period differs from metadata");
                }
            } catch (NumberFormatException ex) {
                throw new InvalidExtractionException("Invalid monetary value");
            }
        } else if (field.value() != null || field.evidenceRef() != null) {
            throw new InvalidExtractionException("Uncertain field cannot contain fact or evidence");
        }
        return new FieldView(field.documentId(), field.version(), field.fieldName(), field.value(),
                field.currency(), field.extractionStatus(), field.evidenceRef(), field.page(), field.snippet());
    }

    private boolean allowedField(DocumentSnapshotRef bound, String name) {
        return switch (bound.type()) {
            case INCOME_PROOF -> "monthlyIncome".equals(name);
            case PAYSLIP -> Set.of("competence", "grossSalary", "netSalary", "payrollDeduction").contains(name);
            case BANK_STATEMENT -> "statementPeriod".equals(name);
            default -> false;
        };
    }

    private FinancialEngine.BankTransaction validateTransaction(DocumentSnapshotRef bound,
            PythonDocumentClient.ExtractedTransaction item) {
        if (item == null || bound.type() != DocumentType.BANK_STATEMENT
                || !bound.id().equals(item.documentId()) || bound.version() != item.version()
                || item.page() == null || item.page() < 1 || item.transactionId() == null
                || !item.transactionId().matches("tx[1-9][0-9]*") || item.snippet() == null
                || item.snippet().isBlank() || item.descriptionOriginal() == null
                || item.descriptionOriginal().isBlank() || !"BRL".equals(item.currency())
                || item.direction() == null || !Set.of("CREDIT", "DEBIT").contains(item.direction())
                || item.classification() == null
                || !Set.of("POSSIBLE_SALARY", "RECURRING_PAYMENT", "UNKNOWN").contains(item.classification())
                || !(bound.id() + ":v" + bound.version() + ":p" + item.page() + ":"
                        + item.transactionId()).equals(item.evidenceRef())) {
            throw new InvalidExtractionException("Invalid bank transaction evidence");
        }
        if (item.amount() == null || item.postedAt() == null || bound.period() == null) {
            throw new InvalidExtractionException("Missing bank transaction value or period");
        }
        try {
            var amount = new BigDecimal(item.amount());
            var date = LocalDate.parse(item.postedAt());
            if (amount.signum() < 0 || !item.postedAt().startsWith(bound.period())
                    || !date.toString().equals(item.postedAt())) {
                throw new InvalidExtractionException("Invalid bank transaction value or period");
            }
            return new FinancialEngine.BankTransaction(bound.id() + ":" + item.transactionId(),
                    bound.period(), item.postedAt(), item.direction(), amount,
                    item.classification(), item.evidenceRef(), item.descriptionOriginal());
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw new InvalidExtractionException("Invalid bank transaction value or date");
        }
    }

    record ValidatedExtraction(List<FieldView> fields, List<FinancialEngine.BankTransaction> transactions) {}
}
