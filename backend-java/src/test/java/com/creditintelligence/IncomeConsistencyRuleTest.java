package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;

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
}
