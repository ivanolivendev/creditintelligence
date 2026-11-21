package com.creditintelligence.policy.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "policy_version")
public class PolicyEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 40)
    private String version;
    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;
    @Column(name = "config_json", nullable = false, columnDefinition = "text")
    private String configJson;
    @Column(name = "text_content", nullable = false, columnDefinition = "text")
    private String textContent;
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String checksum;

    protected PolicyEntity() {}

    public UUID getId() { return id; }
    public String getVersion() { return version; }
    public String getConfigJson() { return configJson; }
    public String getTextContent() { return textContent; }
    public String getChecksum() { return checksum; }
}
