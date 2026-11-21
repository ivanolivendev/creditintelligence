package com.creditintelligence.proposal.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "proposal")
public class ProposalEntity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private ApplicantEntity applicant;
    @Column(name = "declared_monthly_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal declaredMonthlyIncome;
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;
    @Column(nullable = false, length = 40)
    private String status;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProposalEntity() {}

    public ProposalEntity(UUID id, ApplicantEntity applicant, BigDecimal income, String currency, String status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.applicant = applicant;
        this.declaredMonthlyIncome = income;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public ApplicantEntity getApplicant() { return applicant; }
    public BigDecimal getDeclaredMonthlyIncome() { return declaredMonthlyIncome; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setStatus(String status, OffsetDateTime updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }
}
