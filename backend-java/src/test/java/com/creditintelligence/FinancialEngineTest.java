package com.creditintelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.creditintelligence.analysis.domain.FinancialEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialEngineTest {
    private final FinancialEngine engine = new FinancialEngine();
    private final FinancialEngine.Policy policy = new FinancialEngine.Policy(
            new BigDecimal("0.30"), new BigDecimal("3.00"), new BigDecimal("0.20"),
            "CREDIT_POLICY_DEMO_3.4");

    @Test
    void averagesThreePayslipsReconcilesCreditsAndDeduplicatesPayroll() {
        var months = List.of("2026-08", "2026-09", "2026-10");
        var nets = List.of("7300.00", "8500.00", "7400.00");
        var incomes = new ArrayList<FinancialEngine.IncomePeriod>();
        var transactions = new ArrayList<FinancialEngine.BankTransaction>();
        for (int i = 0; i < 3; i++) {
            incomes.add(new FinancialEngine.IncomePeriod(months.get(i), null,
                    new BigDecimal(nets.get(i)), new BigDecimal("300.00"),
                    "pay-" + i, "deduction-" + i));
            transactions.add(new FinancialEngine.BankTransaction("salary-" + i, months.get(i),
                    months.get(i) + "-28", "CREDIT", new BigDecimal(nets.get(i)),
                    "POSSIBLE_SALARY", "bank-" + i));
            transactions.add(new FinancialEngine.BankTransaction("payment-" + i, months.get(i),
                    months.get(i) + "-29", "DEBIT", new BigDecimal("300.00"),
                    "RECURRING_PAYMENT", "debit-" + i));
        }
        transactions.add(new FinancialEngine.BankTransaction("unknown", months.get(2),
                "2026-10-30", "CREDIT", new BigDecimal("4000.00"), "UNKNOWN", "pix"));
        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, transactions, List.of()),
                policy, scenario(null, new BigDecimal("0.02")));
        assertEquals(new BigDecimal("7733.33"), result.averageNetIncome());
        assertEquals(new BigDecimal("7300.00"), result.minimumNetIncome());
        assertEquals(new BigDecimal("8500.00"), result.maximumNetIncome());
        assertEquals(new BigDecimal("0.1552"), result.variabilityRatio());
        assertEquals(3, result.salaryMatches().stream().filter(m -> "MATCH".equals(m.status())).count());
        assertEquals(1, result.unknownCredits());
        assertEquals(new BigDecimal("300.00"), result.payrollDeductionsIncludedInNet());
        assertEquals(new BigDecimal("0.00"), result.additionalMonthlyPayments());
        assertEquals("VERIFIED", result.obligationStatus());
        assertEquals(List.of("debit-0", "debit-1", "debit-2"), finding(result, "OBLIGATION_DEDUPLICATION").evidenceRefs());
        assertEquals("Three distinct factual net salaries", finding(result, "REQUIRED_PAYSLIPS").details());
        assertEquals("PASS", finding(result, "INCOME_STABILITY").result());
        assertEquals("READY_FOR_REVIEW", result.reviewStatus());
    }

    @Test
    void incompleteFactualIncomePeriodsRemainUnevaluated() {
        var incomes = List.of(
                new FinancialEngine.IncomePeriod("2026-08", null, new BigDecimal("8000.00"), null,
                        "pay-08", null),
                new FinancialEngine.IncomePeriod("2026-09", null, new BigDecimal("8000.00"), null,
                        "pay-09", null),
                new FinancialEngine.IncomePeriod("2026-10", null, null, null, null, null));

        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, List.of(), List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        assertEquals("NOT_EVALUATED", result.findings().stream()
                .filter(finding -> "REQUIRED_PAYSLIPS".equals(finding.code()))
                .findFirst().orElseThrow().result());
        assertEquals("NOT_EVALUATED", result.findings().stream()
                .filter(finding -> "INCOME_STABILITY".equals(finding.code()))
                .findFirst().orElseThrow().result());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
        assertEquals(2, result.validIncomePeriods());
    }

    @Test
    void semanticDuplicateSalaryCreditsRequireReviewInsteadOfBeingSummed() {
        var incomes = List.of("2026-08", "2026-09", "2026-10").stream()
                .map(month -> new FinancialEngine.IncomePeriod(month, null, new BigDecimal("8000.00"),
                        BigDecimal.ZERO, "pay-" + month, month + "-deduction"))
                .toList();
        var transactions = new ArrayList<FinancialEngine.BankTransaction>();
        for (var month : List.of("2026-08", "2026-09", "2026-10")) {
            transactions.add(new FinancialEngine.BankTransaction(month + "-salary", month, month + "-28",
                    "CREDIT", new BigDecimal("8000.00"), "POSSIBLE_SALARY", month + "-salary-ref",
                    "Salário sintético"));
            if ("2026-08".equals(month)) {
                transactions.add(new FinancialEngine.BankTransaction(month + "-duplicate", month, month + "-28",
                        "CREDIT", new BigDecimal("8000.00"), "POSSIBLE_SALARY", month + "-duplicate-ref",
                        "SALARIO SINTETICO"));
            }
        }

        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, transactions, List.of()),
                policy, scenario(null, BigDecimal.ZERO));
        var duplicateMatch = result.salaryMatches().stream()
                .filter(match -> "2026-08".equals(match.period())).findFirst().orElseThrow();

        assertEquals("NEEDS_REVIEW", duplicateMatch.status());
        assertEquals(new BigDecimal("0.00"), duplicateMatch.bankCredit());
        var duplicateFinding = finding(result, "SALARY_CREDIT_DUPLICATES");
        assertEquals("NOT_EVALUATED", duplicateFinding.result());
        assertEquals(List.of("pay-2026-08", "2026-08-salary-ref", "2026-08-duplicate-ref"),
                duplicateFinding.evidenceRefs());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
    }

    @Test
    void calculatesCapacityAndPriceWithZeroAndPositiveInterest() {
        var incomes = List.of("2026-08", "2026-09", "2026-10").stream()
                .map(month -> new FinancialEngine.IncomePeriod(month, null,
                        new BigDecimal("8000.00"), BigDecimal.ZERO, month, month + "-deduction"))
                .toList();
        var transactions = new ArrayList<FinancialEngine.BankTransaction>();
        for (var month : List.of("2026-08", "2026-09", "2026-10")) {
            transactions.add(new FinancialEngine.BankTransaction(month + "-salary", month, month + "-28",
                    "CREDIT", new BigDecimal("8000.00"), "POSSIBLE_SALARY", month + "-salary-ref"));
            transactions.add(new FinancialEngine.BankTransaction(month + "-payment", month, month + "-29",
                    "DEBIT", new BigDecimal("1500.00"), "RECURRING_PAYMENT", month + "-debit-ref"));
        }
        var profile = new FinancialEngine.FinancialProfile(incomes, transactions, List.of());
        var zero = engine.assess(profile, policy, scenario(null, BigDecimal.ZERO));
        assertEquals(new BigDecimal("2400.00"), zero.maxTotalInstallments());
        assertEquals(new BigDecimal("900.00"), zero.maxNewInstallment());
        assertEquals(new BigDecimal("32400.00"), zero.simulation().estimatedPrincipalAtCapacity());
        var priced = engine.assess(profile, policy, scenario(null, new BigDecimal("0.02")));
        assertEquals(new BigDecimal("22939.96"), priced.simulation().estimatedPrincipalAtCapacity());
    }

    @Test
    void incompleteOrDivergentEvidenceNeverBecomesVerifiedCapacity() {
        var incomes = List.of(new FinancialEngine.IncomePeriod("2026-08", null,
                new BigDecimal("7300.00"), null, "pay", null));
        var transactions = List.of(new FinancialEngine.BankTransaction("salary", "2026-08",
                "2026-08-28", "CREDIT", new BigDecimal("6900.00"), "POSSIBLE_SALARY", "bank"));
        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, transactions, List.of()),
                policy, scenario(null, BigDecimal.ZERO));
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
        assertNull(result.maxNewInstallment());
        assertEquals("UNMATCHED", result.salaryMatches().getFirst().status());
        assertEquals(List.of("pay", "bank"), result.salaryMatches().getFirst().evidenceRefs());
    }

    @Test
    void rejectsInvalidRateAndTerm() {
        var profile = new FinancialEngine.FinancialProfile(List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> engine.assess(profile, policy,
                new FinancialEngine.Scenario(null, 0, BigDecimal.ZERO, "BRL", "MANUAL_DEMO")));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(profile, policy,
                new FinancialEngine.Scenario(null, 601, BigDecimal.ZERO, "BRL", "MANUAL_DEMO")));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(profile, policy,
                new FinancialEngine.Scenario(null, 36, BigDecimal.ZERO, "USD", "MANUAL_DEMO")));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(profile, policy, scenario(null, null)));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(profile, policy,
                scenario(null, new BigDecimal("-0.01"))));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(profile, policy,
                scenario(null, new BigDecimal("1.01"))));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(profile, policy,
                new FinancialEngine.Scenario(new BigDecimal("-0.01"), 36, BigDecimal.ZERO,
                        "BRL", "MANUAL_DEMO")));
    }

    @Test
    void zeroIncomeAndZeroSalaryCreditAreMatchedWithoutDivisionByZero() {
        var income = new FinancialEngine.IncomePeriod("2026-10", null, BigDecimal.ZERO,
                BigDecimal.ZERO, "pay-0", "deduction-0");
        var salary = new FinancialEngine.BankTransaction("salary-0", "2026-10", "2026-10-28",
                "CREDIT", BigDecimal.ZERO, "POSSIBLE_SALARY", "bank-0");

        var result = engine.assess(new FinancialEngine.FinancialProfile(List.of(income), List.of(salary), List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        assertEquals("MATCH", result.salaryMatches().getFirst().status());
        assertEquals(BigDecimal.ZERO.setScale(2), result.salaryMatches().getFirst().difference());
        assertEquals(new BigDecimal("0.00"), result.averageNetIncome());
        assertEquals(BigDecimal.ZERO, result.variabilityRatio());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
    }

    @Test
    void uncertainDuplicateDoesNotProduceCapacity() {
        var incomes = List.of("2026-08", "2026-09", "2026-10").stream()
                .map(month -> new FinancialEngine.IncomePeriod(month, null,
                        new BigDecimal("8000.00"), new BigDecimal("300.00"), month, month + "-deduction"))
                .toList();
        var transactions = new ArrayList<FinancialEngine.BankTransaction>();
        for (var month : List.of("2026-08", "2026-09", "2026-10")) {
            transactions.add(new FinancialEngine.BankTransaction(month + "-salary", month, month + "-28",
                    "CREDIT", new BigDecimal("8000.00"), "POSSIBLE_SALARY", month + "-salary-ref"));
            transactions.add(new FinancialEngine.BankTransaction(month + "-debit", month, month + "-29",
                    "DEBIT", new BigDecimal("300.00"), "RECURRING_PAYMENT", month + "-debit-ref",
                    "PARCELA DEMO OUTRO CONTRATO"));
        }
        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, transactions, List.of()),
                policy, scenario(null, BigDecimal.ZERO));
        assertEquals("NEEDS_REVIEW", result.obligationStatus());
        assertEquals("NOT_EVALUATED", finding(result, "OBLIGATION_DEDUPLICATION").result());
        assertTrue(result.limitations().contains("Known obligations are incomplete or uncertain."));
        assertNull(result.maxNewInstallment());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
    }

    @Test
    void emptyProfileReportsMissingEvidenceWithoutInventingCapacity() {
        var result = engine.assess(new FinancialEngine.FinancialProfile(List.of(), List.of(), List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
        assertNull(result.averageNetIncome());
        assertNull(result.maxNewInstallment());
        assertEquals("NOT_EVALUATED", result.findings().stream()
                .filter(finding -> "PAYMENT_CAPACITY".equals(finding.code()))
                .findFirst().orElseThrow().result());
    }

    @Test
    void unstableCompleteIncomeIsFlaggedButStillKeepsEvidenceTrace() {
        var incomes = List.of(
                new FinancialEngine.IncomePeriod("2026-08", null, new BigDecimal("4000.00"), BigDecimal.ZERO,
                        "pay-08", null),
                new FinancialEngine.IncomePeriod("2026-09", null, new BigDecimal("8000.00"), BigDecimal.ZERO,
                        "pay-09", null),
                new FinancialEngine.IncomePeriod("2026-10", null, new BigDecimal("12000.00"), BigDecimal.ZERO,
                        "pay-10", null));

        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, List.of(), List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        var finding = result.findings().stream().filter(x -> "INCOME_STABILITY".equals(x.code())).findFirst()
                .orElseThrow();
        assertEquals("WARNING", finding.result());
        assertEquals(List.of("pay-08", "pay-09", "pay-10"), finding.evidenceRefs());
    }

    @Test
    void missingBankPeriodsPreventReconciliationEvenWhenOneSalaryMatches() {
        var income = new FinancialEngine.IncomePeriod("2026-10", null, new BigDecimal("8000.00"),
                BigDecimal.ZERO, "pay-10", null);
        var salary = new FinancialEngine.BankTransaction("salary-10", "2026-10", "2026-10-28", "CREDIT",
                new BigDecimal("8000.00"), "POSSIBLE_SALARY", "bank-10");

        var result = engine.assess(new FinancialEngine.FinancialProfile(List.of(income), List.of(salary), List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        assertEquals("MATCH", result.salaryMatches().getFirst().status());
        assertEquals("NOT_EVALUATED", result.findings().stream()
                .filter(finding -> "REQUIRED_BANK_STATEMENTS".equals(finding.code()))
                .findFirst().orElseThrow().result());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
    }

    @Test
    void reconcilesSplitSalaryAtToleranceAndDistinguishesOtherCredits() {
        var transactions = List.of(
                transaction("08-a", "2026-08", "CREDIT", "5000.00", "POSSIBLE_SALARY"),
                transaction("08-b", "2026-08", "CREDIT", "2760.00", "POSSIBLE_SALARY"),
                transaction("08-debit", "2026-08", "DEBIT", "8000.00", "POSSIBLE_SALARY"),
                transaction("09-salary", "2026-09", "CREDIT", "7759.99", "POSSIBLE_SALARY"),
                transaction("10-pix", "2026-10", "CREDIT", "8000.00", "UNKNOWN"),
                transaction("10-debit", "2026-10", "DEBIT", "8000.00", "UNKNOWN"));

        var result = engine.assess(new FinancialEngine.FinancialProfile(standardIncomes(), transactions, List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        assertEquals(List.of(
                new FinancialEngine.SalaryMatch("2026-08", new BigDecimal("8000.00"), new BigDecimal("7760.00"),
                        new BigDecimal("240.00"), "MATCH", List.of("pay-08", "08-a", "08-b")),
                new FinancialEngine.SalaryMatch("2026-09", new BigDecimal("8000.00"), new BigDecimal("7759.99"),
                        new BigDecimal("240.01"), "UNMATCHED", List.of("pay-09", "09-salary")),
                new FinancialEngine.SalaryMatch("2026-10", new BigDecimal("8000.00"), new BigDecimal("0.00"),
                        new BigDecimal("8000.00"), "UNKNOWN", List.of("pay-10"))), result.salaryMatches());
        assertEquals(1, result.unknownCredits());
        assertEquals("PASS", finding(result, "REQUIRED_BANK_STATEMENTS").result());
        var reconciliation = finding(result, "SALARY_RECONCILIATION");
        assertEquals("WARNING", reconciliation.result());
        assertEquals("Matched periods=1; expected=3; tolerance=3.00%", reconciliation.details());
        assertEquals(List.of("pay-08", "08-a", "08-b", "pay-09", "09-salary", "pay-10"),
                reconciliation.evidenceRefs());
        assertEquals("UNKNOWN", result.obligationStatus());
        assertNull(result.maxNewInstallment());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
        assertTrue(result.limitations().contains("Salary credits require human reconciliation."));
    }

    @Test
    void duplicatePayslipPeriodCannotReuseSalaryCreditOrEstablishCapacity() {
        var incomes = new ArrayList<>(standardIncomes());
        incomes.add(new FinancialEngine.IncomePeriod("2026-08", null, new BigDecimal("8000.00"), null,
                "pay-08-copy", null));

        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes,
                verifiedTransactions("1200.00"), List.of()), policy, scenario(null, BigDecimal.ZERO));

        assertEquals("NOT_EVALUATED", finding(result, "REQUIRED_PAYSLIPS").result());
        assertEquals("NOT_EVALUATED", finding(result, "INCOME_STABILITY").result());
        assertEquals("VERIFIED", result.obligationStatus());
        assertEquals("MATCH", result.salaryMatches().getFirst().status());
        assertEquals(new FinancialEngine.SalaryMatch("2026-08", new BigDecimal("8000.00"), new BigDecimal("0.00"),
                new BigDecimal("8000.00"), "UNKNOWN", List.of("pay-08-copy")), result.salaryMatches().getLast());
        assertNull(result.maxNewInstallment());
        assertNull(result.simulation());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
        assertTrue(result.limitations().contains("Three factual net salary periods are required for verified capacity."));
    }

    @Test
    void incomeVariabilityExactlyAtPolicyBoundaryRemainsStable() {
        var incomes = List.of(
                new FinancialEngine.IncomePeriod("2026-08", null, new BigDecimal("7200.00"), null, "pay-08", null),
                new FinancialEngine.IncomePeriod("2026-09", null, new BigDecimal("8000.00"), null, "pay-09", null),
                new FinancialEngine.IncomePeriod("2026-10", null, new BigDecimal("8800.00"), null, "pay-10", null));

        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, List.of(), List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        assertEquals(new BigDecimal("8000.00"), result.averageNetIncome());
        assertEquals(new BigDecimal("0.2000"), result.variabilityRatio());
        var stability = finding(result, "INCOME_STABILITY");
        assertEquals("PASS", stability.result());
        assertEquals("Net income variability ratio=0.2000; policy=0.20", stability.details());
        assertEquals(List.of("pay-08", "pay-09", "pay-10"), stability.evidenceRefs());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
    }

    @Test
    void doesNotDeduplicatePaymentsAgainstPayrollFromAnotherPeriod() {
        var incomes = new ArrayList<>(standardIncomes());
        incomes.set(0, new FinancialEngine.IncomePeriod("2026-08", null, new BigDecimal("8000.00"),
                new BigDecimal("300.00"), "pay-08", "deduction-08"));
        var transactions = List.of(
                transaction("08-salary", "2026-08", "CREDIT", "8000.00", "POSSIBLE_SALARY"),
                transaction("09-salary", "2026-09", "CREDIT", "8000.00", "POSSIBLE_SALARY"),
                transaction("10-salary", "2026-10", "CREDIT", "8000.00", "POSSIBLE_SALARY"),
                transaction("09-payment", "2026-09", "DEBIT", "300.00", "RECURRING_PAYMENT"),
                new FinancialEngine.BankTransaction("10-payment", "2026-10", "2026-10-28", "DEBIT",
                        new BigDecimal("300.00"), "RECURRING_PAYMENT", "10-payment", "PARCELA DEMO CONSIGNADO"));

        var result = engine.assess(new FinancialEngine.FinancialProfile(incomes, transactions, List.of()),
                policy, scenario(null, BigDecimal.ZERO));

        assertEquals("READY_FOR_REVIEW", result.reviewStatus());
        assertEquals("VERIFIED", result.obligationStatus());
        assertEquals(new BigDecimal("100.00"), result.payrollDeductionsIncludedInNet());
        assertEquals(new BigDecimal("200.00"), result.additionalMonthlyPayments());
        assertEquals(new BigDecimal("2200.00"), result.maxNewInstallment());
        var obligations = finding(result, "OBLIGATION_DEDUPLICATION");
        assertEquals("PASS", obligations.result());
        assertEquals(List.of("09-payment", "10-payment"), obligations.evidenceRefs());
    }

    @Test
    void sourceLimitationsOverrideOtherwiseVerifiedIncomeAndObligations() {
        var limitation = "Statement period not factual for 2026-09";
        var result = engine.assess(new FinancialEngine.FinancialProfile(standardIncomes(),
                verifiedTransactions("1200.00"), List.of(limitation)), policy, scenario(null, BigDecimal.ZERO));

        assertEquals("PASS", finding(result, "SALARY_RECONCILIATION").result());
        assertEquals("VERIFIED", result.obligationStatus());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
        assertEquals(limitation, result.limitations().getFirst());
        assertNull(result.maxTotalInstallments());
        assertNull(result.simulation());
        assertEquals("NOT_EVALUATED", finding(result, "PAYMENT_CAPACITY").result());
    }

    @Test
    void pricesRequestedPrincipalAtInclusiveTermAndInterestBoundaries() {
        var profile = new FinancialEngine.FinancialProfile(standardIncomes(), verifiedTransactions("7500.00"), List.of());
        var maxInterest = engine.assess(profile, policy, new FinancialEngine.Scenario(
                new BigDecimal("100.00"), 1, BigDecimal.ONE, "BRL", "SYNTHETIC_RATE"));
        var maxTerm = engine.assess(profile, policy, new FinancialEngine.Scenario(
                BigDecimal.ZERO, 600, BigDecimal.ZERO, "BRL", "SYNTHETIC_RATE"));

        assertEquals("VERIFIED", maxInterest.obligationStatus());
        assertEquals(new BigDecimal("2500.00"), maxInterest.additionalMonthlyPayments());
        assertEquals(new BigDecimal("2400.00"), maxInterest.maxTotalInstallments());
        assertEquals(new BigDecimal("0.00"), maxInterest.maxNewInstallment());
        assertEquals(new BigDecimal("200.00"), maxInterest.simulation().requestedPayment());
        assertEquals(new BigDecimal("100.00"), maxInterest.simulation().requestedPrincipal());
        assertEquals(new BigDecimal("0.00"), maxInterest.simulation().estimatedPrincipalAtCapacity());
        assertEquals("SYNTHETIC_RATE", maxInterest.simulation().pricingSource());
        assertEquals("Maximum new installment=0.00 BRL", finding(maxInterest, "PAYMENT_CAPACITY").details());
        assertEquals(600, maxTerm.simulation().termMonths());
        assertEquals(new BigDecimal("0.00"), maxTerm.simulation().requestedPayment());
    }

    private FinancialEngine.Finding finding(FinancialEngine.Assessment result, String code) {
        return result.findings().stream().filter(item -> code.equals(item.code())).findFirst().orElseThrow();
    }

    private List<FinancialEngine.IncomePeriod> standardIncomes() {
        return List.of("2026-08", "2026-09", "2026-10").stream()
                .map(month -> new FinancialEngine.IncomePeriod(month, null, new BigDecimal("8000.00"),
                        null, "pay-" + month.substring(5), null)).toList();
    }

    private List<FinancialEngine.BankTransaction> verifiedTransactions(String additionalPayment) {
        return List.of(
                transaction("08-salary", "2026-08", "CREDIT", "8000.00", "POSSIBLE_SALARY"),
                transaction("09-salary", "2026-09", "CREDIT", "8000.00", "POSSIBLE_SALARY"),
                transaction("10-salary", "2026-10", "CREDIT", "8000.00", "POSSIBLE_SALARY"),
                transaction("08-payment", "2026-08", "DEBIT", additionalPayment, "RECURRING_PAYMENT"));
    }

    private FinancialEngine.BankTransaction transaction(String id, String period, String direction, String amount,
            String classification) {
        return new FinancialEngine.BankTransaction(id, period, period + "-28", direction, new BigDecimal(amount),
                classification, id, "Movimento sintético " + id);
    }

    private FinancialEngine.Scenario scenario(BigDecimal principal, BigDecimal rate) {
        return new FinancialEngine.Scenario(principal, 36, rate, "BRL", "MANUAL_DEMO");
    }
}
