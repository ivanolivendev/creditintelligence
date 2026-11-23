package com.creditintelligence.analysis.application;

import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class FinancialProfileAssembler {
    FinancialEngine.FinancialProfile assemble(List<DocumentSnapshotRef> documents,
            ExtractionContractValidator.ValidatedExtraction extraction, BureauReport bureauReport) {
        List<FinancialEngine.IncomePeriod> incomes = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        for (var document : documents) {
            if (document.type() != DocumentType.PAYSLIP) continue;
            var own = extraction.fields().stream().filter(field -> field.documentId().equals(document.id())).toList();
            var net = own.stream().filter(field -> "netSalary".equals(field.fieldName())).findFirst().orElse(null);
            var gross = own.stream().filter(field -> "grossSalary".equals(field.fieldName())).findFirst().orElse(null);
            var deduction = own.stream().filter(field -> "payrollDeduction".equals(field.fieldName()))
                    .findFirst().orElse(null);
            if (net == null || !"EXTRACTED".equals(net.extractionStatus())) {
                limitations.add("Net salary not factual for period " + document.period());
            }
            incomes.add(new FinancialEngine.IncomePeriod(document.period(), monetaryValue(gross), monetaryValue(net),
                    monetaryValue(deduction), net == null ? null : net.evidenceRef(),
                    deduction == null ? null : deduction.evidenceRef()));
        }
        for (var document : documents) {
            if (document.type() != DocumentType.BANK_STATEMENT) continue;
            var period = extraction.fields().stream().filter(field -> field.documentId().equals(document.id())
                    && "statementPeriod".equals(field.fieldName())).findFirst().orElse(null);
            if (period == null || !"EXTRACTED".equals(period.extractionStatus())) {
                limitations.add("Statement period not factual for " + document.period());
            }
        }
        return new FinancialEngine.FinancialProfile(incomes, extraction.transactions(), limitations, bureauReport);
    }

    private BigDecimal monetaryValue(FieldView field) {
        return field == null || !"EXTRACTED".equals(field.extractionStatus())
                ? null : new BigDecimal(field.value());
    }
}
