package com.creditintelligence.analysis.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import com.creditintelligence.bureau.application.BureauReport;

/** Deterministic calculations for synthetic documentary pre-analysis only. */
public final class FinancialEngine {
    public static final String RULE_VERSION = "3.4";
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public record IncomePeriod(String period, BigDecimal gross, BigDecimal net,
            BigDecimal payrollDeduction, String netEvidenceRef, String deductionEvidenceRef) {}
    public record BankTransaction(String id, String period, String postedAt, String direction,
            BigDecimal amount, String classification, String evidenceRef,
            String descriptionOriginal) {
        public BankTransaction(String id, String period, String postedAt, String direction,
                BigDecimal amount, String classification, String evidenceRef) {
            this(id, period, postedAt, direction, amount, classification, evidenceRef,
                    "RECURRING_PAYMENT".equals(classification) ? "PARCELA DEMO CONSIGNADO" : "SYNTHETIC DEMO");
        }
    }
    public record FinancialProfile(List<IncomePeriod> incomePeriods,
            List<BankTransaction> bankTransactions, List<String> limitations,
            BureauReport bureau) {
        public FinancialProfile(List<IncomePeriod> incomePeriods,
                List<BankTransaction> bankTransactions, List<String> limitations) {
            this(incomePeriods, bankTransactions, limitations, null);
        }
    }
    public record Scenario(BigDecimal principalRequested, int termMonths,
            BigDecimal monthlyInterestRate, String currency, String pricingSource) {}
    public record Policy(BigDecimal commitmentLimitRatio, BigDecimal salaryTolerancePercent,
            BigDecimal stabilityWarningRatio, String version) {}
    public record SalaryMatch(String period, BigDecimal payslipNet, BigDecimal bankCredit,
            BigDecimal difference, String status, List<String> evidenceRefs) {}
    public record Finding(String code, String result, String details, List<String> evidenceRefs) {}
    public record Simulation(int termMonths, BigDecimal monthlyInterestRate,
            BigDecimal requestedPrincipal, BigDecimal requestedPayment,
            BigDecimal estimatedPrincipalAtCapacity, String pricingSource,
            String limitation) {}
    public record Assessment(String currency, String policyVersion, String ruleVersion,
            String reviewStatus, int validIncomePeriods, BigDecimal averageNetIncome,
            BigDecimal minimumNetIncome, BigDecimal maximumNetIncome,
            BigDecimal variabilityRatio, List<SalaryMatch> salaryMatches,
            int unknownCredits, BigDecimal payrollDeductionsIncludedInNet,
            BigDecimal additionalMonthlyPayments, String obligationStatus,
            BigDecimal maxTotalInstallments, BigDecimal maxNewInstallment,
            Simulation simulation, List<Finding> findings, List<String> limitations,
            BureauReport bureau) {}

    public Assessment assess(FinancialProfile profile, Policy policy, Scenario scenario) {
        if (!"BRL".equals(scenario.currency()) || scenario.termMonths() < 1
                || scenario.termMonths() > 600 || scenario.monthlyInterestRate() == null
                || scenario.monthlyInterestRate().signum() < 0
                || scenario.monthlyInterestRate().compareTo(BigDecimal.ONE) > 0
                || scenario.principalRequested() != null && scenario.principalRequested().signum() < 0) {
            throw new IllegalArgumentException("Invalid synthetic loan scenario");
        }
        var incomes = profile.incomePeriods().stream().filter(p -> p.net() != null).toList();
        var validPeriods = incomes.stream().map(IncomePeriod::period).distinct().count();
        var distinctIncomePeriods = profile.incomePeriods().stream().map(IncomePeriod::period).distinct().count();
        var complete = validPeriods == 3 && distinctIncomePeriods == 3
                && profile.incomePeriods().size() == 3;
        BigDecimal average = null;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        BigDecimal variability = null;
        if (!incomes.isEmpty()) {
            average = incomes.stream().map(IncomePeriod::net).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(incomes.size()), 2, RoundingMode.HALF_UP);
            minimum = incomes.stream().map(IncomePeriod::net).min(BigDecimal::compareTo).orElseThrow();
            maximum = incomes.stream().map(IncomePeriod::net).max(BigDecimal::compareTo).orElseThrow();
            variability = average.signum() == 0 ? BigDecimal.ZERO
                    : maximum.subtract(minimum).divide(average, 4, RoundingMode.HALF_UP);
        }
        List<Finding> findings = new ArrayList<>();
        List<String> limitations = new ArrayList<>(profile.limitations());
        findings.add(new Finding("REQUIRED_PAYSLIPS", complete ? "PASS" : "NOT_EVALUATED",
                complete ? "Three distinct factual net salaries" : "Three factual payslip periods required",
                incomes.stream().map(IncomePeriod::netEvidenceRef).toList()));
        if (variability != null) {
            findings.add(new Finding("INCOME_STABILITY",
                    !complete ? "NOT_EVALUATED"
                            : variability.compareTo(policy.stabilityWarningRatio()) > 0 ? "WARNING" : "PASS",
                    !complete ? "Three factual payslip periods required"
                            : "Net income variability ratio=" + variability + "; policy="
                                    + policy.stabilityWarningRatio(),
                    incomes.stream().map(IncomePeriod::netEvidenceRef).toList()));
        }
        Set<String> usedTransactions = new HashSet<>();
        List<SalaryMatch> matches = new ArrayList<>();
        for (var income : incomes) {
            var candidates = profile.bankTransactions().stream()
                    .filter(tx -> tx.period().equals(income.period()) && "CREDIT".equals(tx.direction())
                            && "POSSIBLE_SALARY".equals(tx.classification())
                            && !usedTransactions.contains(tx.id())).toList();
            Map<String, List<BankTransaction>> semanticCandidates = new HashMap<>();
            candidates.forEach(tx -> semanticCandidates.computeIfAbsent(salaryCreditKey(tx), ignored -> new ArrayList<>())
                    .add(tx));
            var duplicateCandidates = semanticCandidates.values().stream()
                    .filter(group -> group.size() > 1).flatMap(List::stream).toList();
            var usableCandidates = semanticCandidates.values().stream()
                    .filter(group -> group.size() == 1).flatMap(List::stream).toList();
            var credit = usableCandidates.stream().map(BankTransaction::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var difference = income.net().subtract(credit).abs();
            var differencePercent = income.net().signum() == 0 ? BigDecimal.ZERO
                    : difference.divide(income.net(), 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
            var status = !duplicateCandidates.isEmpty() ? "NEEDS_REVIEW" : candidates.isEmpty() ? "UNKNOWN"
                    : differencePercent.compareTo(policy.salaryTolerancePercent()) <= 0 ? "MATCH" : "UNMATCHED";
            candidates.forEach(tx -> usedTransactions.add(tx.id()));
            var refs = new ArrayList<String>();
            refs.add(income.netEvidenceRef());
            candidates.forEach(tx -> refs.add(tx.evidenceRef()));
            matches.add(new SalaryMatch(income.period(), income.net(), money(credit),
                    money(difference), status, refs));
        }
        var duplicateEvidenceRefs = matches.stream()
                .filter(match -> "NEEDS_REVIEW".equals(match.status()))
                .flatMap(match -> match.evidenceRefs().stream()).distinct().toList();
        if (!duplicateEvidenceRefs.isEmpty()) {
            findings.add(new Finding("SALARY_CREDIT_DUPLICATES", "NOT_EVALUATED",
                    "Semantically duplicate salary credits require human review", duplicateEvidenceRefs));
        }
        var bankPeriods = profile.bankTransactions().stream().map(BankTransaction::period).distinct().count();
        findings.add(new Finding("REQUIRED_BANK_STATEMENTS", bankPeriods == 3 ? "PASS" : "NOT_EVALUATED",
                "Statement periods=" + bankPeriods + "; expected=3", List.of()));
        var allMatch = complete && bankPeriods == 3 && matches.stream()
                .allMatch(match -> "MATCH".equals(match.status()));
        findings.add(new Finding("SALARY_RECONCILIATION", allMatch ? "PASS" : "WARNING",
                "Matched periods=" + matches.stream().filter(m -> "MATCH".equals(m.status())).count()
                        + "; expected=3; tolerance=" + policy.salaryTolerancePercent() + "%",
                matches.stream().flatMap(m -> m.evidenceRefs().stream()).distinct().toList()));
        var unknownCredits = (int) profile.bankTransactions().stream()
                .filter(tx -> "CREDIT".equals(tx.direction()) && "UNKNOWN".equals(tx.classification())).count();
        var payrollDeduction = incomes.stream().map(IncomePeriod::payrollDeduction)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, incomes.size())), 2, RoundingMode.HALF_UP);
        var recurring = profile.bankTransactions().stream().filter(tx -> "DEBIT".equals(tx.direction())
                && "RECURRING_PAYMENT".equals(tx.classification())).toList();
        var uncertainDuplicate = recurring.stream().anyMatch(tx ->
                !tx.descriptionOriginal().toUpperCase(java.util.Locale.ROOT).contains("CONSIGNADO")
                        && incomes.stream().anyMatch(income -> income.period().equals(tx.period())
                                && income.payrollDeduction() != null
                                && income.payrollDeduction().compareTo(tx.amount()) == 0));
        var additional = recurring.stream().filter(tx -> incomes.stream().noneMatch(income ->
                income.period().equals(tx.period()) && income.payrollDeduction() != null
                        && income.payrollDeduction().compareTo(tx.amount()) == 0
                        && tx.descriptionOriginal().toUpperCase(java.util.Locale.ROOT)
                                .contains("CONSIGNADO")))
                .map(BankTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, bankPeriods)), 2, RoundingMode.HALF_UP);
        var obligationStatus = recurring.isEmpty() ? "UNKNOWN"
                : bankPeriods == 3 && !uncertainDuplicate ? "VERIFIED" : "NEEDS_REVIEW";
        findings.add(new Finding("OBLIGATION_DEDUPLICATION",
                "VERIFIED".equals(obligationStatus) ? "PASS" : "NOT_EVALUATED",
                "Payroll deductions are already reflected in net income; status=" + obligationStatus,
                recurring.stream().map(BankTransaction::evidenceRef).toList()));
        if (!complete) limitations.add("Three factual net salary periods are required for verified capacity.");
        if (!allMatch) limitations.add("Salary credits require human reconciliation.");
        if (!"VERIFIED".equals(obligationStatus)) limitations.add("Known obligations are incomplete or uncertain.");
        limitations.add("Partial Price simulation excludes CET, IOF, insurance and fees; not a credit offer.");
        BigDecimal total = null;
        BigDecimal capacity = null;
        Simulation simulation = null;
        if (complete && average != null && "VERIFIED".equals(obligationStatus)
                && profile.limitations().isEmpty()) {
            total = money(average.multiply(policy.commitmentLimitRatio()));
            capacity = money(total.subtract(additional).max(BigDecimal.ZERO));
            var rate = scenario.monthlyInterestRate();
            var term = scenario.termMonths();
            var factor = rate.signum() == 0 ? BigDecimal.valueOf(term)
                    : BigDecimal.ONE.subtract(BigDecimal.ONE.divide(
                            BigDecimal.ONE.add(rate).pow(term, PRECISION), PRECISION))
                            .divide(rate, PRECISION);
            var requestedPayment = scenario.principalRequested() == null ? null
                    : money(scenario.principalRequested().divide(factor, PRECISION));
            simulation = new Simulation(term, rate, scenario.principalRequested(), requestedPayment,
                    money(capacity.multiply(factor, PRECISION)), scenario.pricingSource(),
                    "Partial simulation: excludes CET, IOF, insurance and fees; not an offer.");
        }
        findings.add(new Finding("PAYMENT_CAPACITY", capacity == null ? "NOT_EVALUATED" : "PASS",
                capacity == null ? "Capacity requires complete income and known obligations"
                        : "Maximum new installment=" + capacity + " BRL",
                incomes.stream().map(IncomePeriod::netEvidenceRef).toList()));
        return new Assessment("BRL", policy.version(), RULE_VERSION,
                complete && allMatch && "VERIFIED".equals(obligationStatus)
                        && profile.limitations().isEmpty()
                        ? "READY_FOR_REVIEW" : "MORE_INFO_REQUIRED",
                incomes.size(), average, minimum, maximum, variability, matches,
                unknownCredits, money(payrollDeduction), additional, obligationStatus,
                total, capacity, simulation, findings, limitations, profile.bureau());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String salaryCreditKey(BankTransaction transaction) {
        var normalizedDescription = Normalizer.normalize(transaction.descriptionOriginal(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim();
        return transaction.period() + "|" + transaction.postedAt() + "|"
                + transaction.amount().stripTrailingZeros().toPlainString() + "|BRL|"
                + normalizedDescription;
    }
}
