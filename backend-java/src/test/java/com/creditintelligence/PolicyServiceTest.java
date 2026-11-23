package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.creditintelligence.policy.application.PolicyService;
import com.creditintelligence.policy.application.PolicyDataPort;
import com.creditintelligence.policy.infrastructure.PolicyDataStore;
import com.creditintelligence.policy.infrastructure.PolicyEntity;
import com.creditintelligence.policy.infrastructure.PolicyRepository;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PolicyServiceTest {
    private final PolicyDataPort repository = mock(PolicyDataPort.class);
    private final PolicyService service = new PolicyService(repository, new ObjectMapper());

    @Test
    void mapsAndValidatesPolicyChecksumAndFinancialConfiguration() {
        var policy = policy("CREDIT_POLICY_DEMO_3.4", """
                {"incomeTolerancePercent":"5.00","requiredDocumentTypes":["PAYSLIP","BANK_STATEMENT"],
                 "commitmentLimitRatio":"0.30","salaryMatchTolerancePercent":"10.00",
                 "incomeStabilityWarningRatio":"0.80","simulationTermMonths":36,
                 "simulationMonthlyInterestRate":"0.0199"}
                """, "Financial policy");
        when(repository.findByVersion("CREDIT_POLICY_DEMO_3.4")).thenReturn(Optional.of(policy));

        var snapshot = service.financial();

        assertThat(snapshot.id()).isEqualTo(policy.id());
        assertThat(snapshot.version()).isEqualTo("CREDIT_POLICY_DEMO_3.4");
        assertThat(snapshot.incomeTolerancePercent()).isEqualByComparingTo("5.00");
        assertThat(snapshot.requiredDocumentTypes()).containsExactly("PAYSLIP", "BANK_STATEMENT");
        assertThat(snapshot.commitmentLimitRatio()).isEqualByComparingTo("0.30");
        assertThat(snapshot.salaryMatchTolerancePercent()).isEqualByComparingTo("10.00");
        assertThat(snapshot.incomeStabilityWarningRatio()).isEqualByComparingTo("0.80");
        assertThat(snapshot.simulationTermMonths()).isEqualTo(36);
        assertThat(snapshot.simulationMonthlyInterestRate()).isEqualByComparingTo("0.0199");
    }

    @Test
    void rejectsPolicyWithChecksumMismatchBeforeParsingConfiguration() {
        var config = "{\"incomeTolerancePercent\":\"5.00\",\"requiredDocumentTypes\":[\"INCOME_PROOF\"]}";
        var text = "Legacy policy";

        var invalid = new PolicyDataPort.PolicyRecord(UUID.randomUUID(), "POLICY-1.0", config, text, "0".repeat(64));
        when(repository.findByVersion("POLICY-1.0")).thenReturn(Optional.of(invalid));

        assertThatThrownBy(service::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Policy checksum mismatch");
    }

    @Test
    void rejectsPolicyWithoutRequiredDocumentsOrWithInvalidJson() {
        var noDocuments = policy("POLICY-1.0", "{\"incomeTolerancePercent\":\"5.00\",\"requiredDocumentTypes\":[]}",
                "Legacy policy");
        when(repository.findByVersion("POLICY-1.0")).thenReturn(Optional.of(noDocuments));

        assertThatThrownBy(service::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid policy configuration");

        var invalidJson = policy("POLICY-1.0", "not-json", "Legacy policy");
        when(repository.findByVersion("POLICY-1.0")).thenReturn(Optional.of(invalidJson));
        assertThatThrownBy(service::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid policy configuration");
    }

    @Test
    void reportsMissingPolicyVersion() {
        when(repository.findByVersion("POLICY-1.0")).thenReturn(Optional.empty());

        assertThatThrownBy(service::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No legacy policy");
    }

    @Test
    void reportsMissingFinancialPolicyAndUnknownPolicyId() {
        var unknown = UUID.randomUUID();
        assertThatThrownBy(service::financial).isInstanceOf(IllegalStateException.class)
                .hasMessage("No financial policy");
        assertThatThrownBy(() -> service.get(unknown)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Policy not found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"INCOME_PROOF\"", "null", "{}"})
    void rejectsRequiredDocumentConfigurationThatIsNotAnArray(String required) {
        var config = "{\"incomeTolerancePercent\":\"5.00\",\"requiredDocumentTypes\":" + required + "}";
        when(repository.findByVersion("POLICY-1.0")).thenReturn(Optional.of(policy("POLICY-1.0", config,
                "Legacy synthetic policy")));

        assertThatThrownBy(service::current).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid policy configuration").hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void retrievesTheVersionedLegacyPolicyByIdWithoutInventingFinancialDefaults() {
        var rows = mock(PolicyRepository.class);
        var storage = new PolicyService(new PolicyDataStore(rows), new ObjectMapper());
        var policy = policy("POLICY-1.0", """
                {"incomeTolerancePercent":"5.00","requiredDocumentTypes":["INCOME_PROOF"]}
                """, "Política sintética para comprovantes");
        // Populate the fields that Hibernate hydrates before returning the row to the adapter.
        var entity = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY).convertValue(Map.of(
                "id", policy.id(), "version", policy.version(), "configJson", policy.configJson(),
                "textContent", policy.textContent(), "checksum", policy.checksum() + " "), PolicyEntity.class);
        when(rows.findByVersion(policy.version())).thenReturn(Optional.of(entity));
        when(rows.findById(policy.id())).thenReturn(Optional.of(entity));

        var snapshot = storage.get(policy.id());

        assertThat(snapshot).isEqualTo(storage.current());
        assertThat(snapshot.id()).isEqualTo(policy.id());
        assertThat(snapshot.version()).isEqualTo(policy.version());
        assertThat(snapshot.checksum()).isEqualTo(policy.checksum());
        assertThat(snapshot.text()).isEqualTo(policy.textContent());
        assertThat(snapshot.requiredDocumentTypes()).containsExactly("INCOME_PROOF");
        assertThat(snapshot.incomeTolerancePercent()).isEqualByComparingTo("5.00");
        assertThat(snapshot.commitmentLimitRatio()).isNull();
        assertThat(snapshot.salaryMatchTolerancePercent()).isNull();
        assertThat(snapshot.incomeStabilityWarningRatio()).isNull();
        assertThat(snapshot.simulationMonthlyInterestRate()).isNull();
        assertThat(snapshot.simulationTermMonths()).isZero();
    }

    private PolicyDataPort.PolicyRecord policy(String version, String config, String text) {
        return new PolicyDataPort.PolicyRecord(UUID.randomUUID(), version, config, text,
                checksum(config + "\n" + text));
    }

    private String checksum(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
