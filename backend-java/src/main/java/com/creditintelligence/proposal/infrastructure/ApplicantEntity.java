package com.creditintelligence.proposal.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "applicant")
public class ApplicantEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "synthetic_identifier", nullable = false, length = 80)
    private String syntheticIdentifier;

    protected ApplicantEntity() {}

    public ApplicantEntity(UUID id, String name, String syntheticIdentifier) {
        this.id = id;
        this.name = name;
        this.syntheticIdentifier = syntheticIdentifier;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSyntheticIdentifier() { return syntheticIdentifier; }
}
