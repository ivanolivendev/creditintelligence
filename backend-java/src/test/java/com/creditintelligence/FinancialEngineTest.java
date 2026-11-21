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
        assertEquals(3, result.salaryMatches().stream().filter(m -> "MATCH".equals(m.status())).count());
        assertEquals(1, result.unknownCredits());
        assertEquals(new BigDecimal("0.00"), result.additionalMonthlyPayments());
        assertEquals("VERIFIED", result.obligationStatus());
        assertEquals("READY_FOR_REVIEW", result.reviewStatus());
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
                scenario(null, new BigDecimal("-0.01"))));
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
        assertNull(result.maxNewInstallment());
        assertEquals("MORE_INFO_REQUIRED", result.reviewStatus());
    }

    private FinancialEngine.Scenario scenario(BigDecimal principal, BigDecimal rate) {
        return new FinancialEngine.Scenario(principal, 36, rate, "BRL", "MANUAL_DEMO");
    }
}
