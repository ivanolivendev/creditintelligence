package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditintelligence.analysis.domain.IncomeConsistencyRule;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class IncomeConsistencyRuleTest {
    private final IncomeConsistencyRule rule = new IncomeConsistencyRule();

    @Test
    void warnsAbovePolicyTolerance() {
        var finding = rule.evaluate(new BigDecimal("8000.00"), new BigDecimal("7500.00"),
                new BigDecimal("5.00"), "document:v1:p1:monthlyIncome");
        assertThat(finding.result()).isEqualTo("WARNING");
        assertThat(finding.details()).contains("6.6667%", "tolerance=5.00%");
        assertThat(finding.evidenceRef()).isEqualTo("document:v1:p1:monthlyIncome");
    }

    @Test
    void doesNotEvaluateMissingIncome() {
        var finding = rule.evaluate(new BigDecimal("8000.00"), null, new BigDecimal("5.00"), null);
        assertThat(finding.result()).isEqualTo("NOT_EVALUATED");
        assertThat(finding.evidenceRef()).isNull();
    }

    @Test
    void passesWhenDifferenceIsExactlyAtToleranceBoundary() {
        var finding = rule.evaluate(new BigDecimal("105.00"), new BigDecimal("100.00"),
                new BigDecimal("5.00"), "income:v1:p1");

        assertThat(finding.result()).isEqualTo("PASS");
        assertThat(finding.details()).contains("5.0000%", "tolerance=5.00%");
    }

    @Test
    void warnsWhenDifferenceExceedsToleranceBySmallestRepresentableAmount() {
        var finding = rule.evaluate(new BigDecimal("105.01"), new BigDecimal("100.00"),
                new BigDecimal("5.00"), "income:v1:p1");

        assertThat(finding.result()).isEqualTo("WARNING");
        assertThat(finding.details()).contains("5.0100%");
    }

    @Test
    void handlesZeroDocumentIncomeWithoutDivisionByZero() {
        var exact = rule.evaluate(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00"), null);
        var inconsistent = rule.evaluate(new BigDecimal("1.00"), BigDecimal.ZERO,
                new BigDecimal("5.00"), "income:v1:p1");

        assertThat(exact.result()).isEqualTo("PASS");
        assertThat(inconsistent.result()).isEqualTo("WARNING");
        assertThat(inconsistent.details()).contains("1000000%");
    }

    @Test
    void comparesAbsoluteDifferenceWhenDocumentIncomeExceedsDeclaredIncome() {
        var finding = rule.evaluate(new BigDecimal("95.00"), new BigDecimal("100.00"),
                new BigDecimal("5.00"), null);

        assertThat(finding.result()).isEqualTo("PASS");
        assertThat(finding.details()).contains("5.0000%");
    }

    @Test
    void rejectsNegativeExtractedIncome() {
        assertThatThrownBy(() -> rule.evaluate(new BigDecimal("8000.00"), new BigDecimal("-1.00"),
                new BigDecimal("5.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Negative extracted income");
    }
}
