# Spec Delta

## ADDED Requirements

### Requirement: Bind financial assessment to analysis inputs
The analysis SHALL persist a financial assessment tied to its exact document snapshot, policy version, input fingerprint, and rule version. New document versions SHALL create a new assessment without rewriting the earlier dossier.

#### Scenario: New payslip version
- **WHEN** a payslip is replaced after an analysis completes
- **THEN** the next analysis has a new fingerprint and the earlier assessment remains retrievable

### Requirement: Expose financial findings separately
The dossier SHALL expose extracted facts, calculated metrics, simulation, limitations, and deterministic findings as separate sections, with evidence references where available.

#### Scenario: Salary mismatch
- **WHEN** a payslip's net salary differs from eligible bank credits beyond policy tolerance
- **THEN** the dossier includes a salary-reconciliation warning linked to both source documents
