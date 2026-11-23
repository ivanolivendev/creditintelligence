package com.creditintelligence.bureau.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.bureau.application.CreditBureauPort.BureauQuery;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FixtureBureauAdapterTest {
    private final FixtureBureauAdapter adapter = new FixtureBureauAdapter();

    @ParameterizedTest
    @MethodSource("availableScenarios")
    void identifiesSyntheticContextAndPreservesOptionalScore(String scenario, BureauReport.Score score,
            int restrictionCount) {
        var report = adapter.consult(query(scenario));

        assertThat(report.provider()).isEqualTo("FIXTURE");
        assertThat(report.product()).isEqualTo("CREDIT_BUREAU_PF_DEMO");
        assertThat(report.environment()).isEqualTo("MOCK");
        assertThat(report.status()).isEqualTo("AVAILABLE");
        assertThat(report.consultedAt()).isNotNull();
        assertThat(report.consultedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(report.responseVersion()).isEqualTo("1.0");
        assertThat(report.referenceId()).isNull();
        assertThat(report.score()).isEqualTo(score);
        assertThat(report.restrictions()).isEqualTo(new BureauReport.Restrictions(restrictionCount));
        assertThat(report.limitations()).containsExactly(
                "Fixture inteiramente sintética; nenhuma consulta remota foi realizada.");
    }

    private static Stream<Arguments> availableScenarios() {
        return Stream.of(
                arguments("COMPLETE", new BureauReport.Score(640, 0, 1000, "SYNTHETIC_DEMO"), 1),
                arguments("CLEAN", null, 0));
    }

    @Test
    void unavailableFixtureKeepsRestrictionAndScoreUnknown() {
        var report = adapter.consult(query("UNAVAILABLE"));

        assertThat(report.provider()).isEqualTo("FIXTURE");
        assertThat(report.environment()).isEqualTo("MOCK");
        assertThat(report.status()).isEqualTo("UNAVAILABLE");
        assertThat(report.consultedAt()).isNull();
        assertThat(report.score()).isNull();
        assertThat(report.restrictions()).isNull();
        assertThat(report.limitations()).containsExactly(
                "Falha sintética da fixture; nenhuma consulta remota foi realizada.");
    }

    private BureauQuery query(String scenario) {
        return new BureauQuery(new UUID(0, 1), scenario, "SYNTHETIC_DOCUMENTARY_PRE_ANALYSIS");
    }
}
