package com.creditintelligence.policy.application;

import com.creditintelligence.policy.infrastructure.PolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {
    private final PolicyRepository repository;
    private final ObjectMapper json;

    public PolicyService(PolicyRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PolicySnapshot current() {
        var policy = repository.findByVersion("POLICY-1.0")
                .orElseThrow(() -> new IllegalStateException("No legacy policy"));
        return toSnapshot(policy);
    }

    @Transactional(readOnly = true)
    public PolicySnapshot financial() {
        return toSnapshot(repository.findByVersion("CREDIT_POLICY_DEMO_2.0")
                .orElseThrow(() -> new IllegalStateException("No financial policy")));
    }

    @Transactional(readOnly = true)
    public PolicySnapshot get(UUID id) {
        return toSnapshot(repository.findById(id).orElseThrow(() -> new IllegalStateException("Policy not found")));
    }

    private PolicySnapshot toSnapshot(com.creditintelligence.policy.infrastructure.PolicyEntity policy) {
        var checksum = sha256(policy.getConfigJson() + "\n" + policy.getTextContent());
        if (!checksum.equals(policy.getChecksum().trim())) {
            throw new IllegalStateException("Policy checksum mismatch");
        }
        try {
            var config = json.readTree(policy.getConfigJson());
            var tolerance = new BigDecimal(config.path("incomeTolerancePercent").asText());
            var required = config.path("requiredDocumentTypes");
            if (!required.isArray() || required.isEmpty()) {
                throw new IllegalStateException("Policy has no required documents");
            }
            List<String> requiredTypes = StreamSupport.stream(required.spliterator(), false)
                    .map(node -> node.asText()).toList();
            return new PolicySnapshot(policy.getId(), policy.getVersion(), checksum,
                    policy.getTextContent(), tolerance, requiredTypes,
                    decimalOrNull(config, "commitmentLimitRatio"),
                    decimalOrNull(config, "salaryMatchToleranceAbsolute"),
                    decimalOrNull(config, "incomeStabilityWarningRatio"),
                    config.path("simulationTermMonths").asInt(),
                    decimalOrNull(config, "simulationMonthlyInterestRate"));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid policy configuration", ex);
        }
    }

    private BigDecimal decimalOrNull(com.fasterxml.jackson.databind.JsonNode config, String name) {
        return config.has(name) ? new BigDecimal(config.path(name).asText()) : null;
    }

    private String sha256(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
