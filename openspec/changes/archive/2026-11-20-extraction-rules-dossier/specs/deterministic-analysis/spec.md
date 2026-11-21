# Spec Delta

## Purpose

Produce a persistent, reviewable analysis tied to immutable document versions and a synthetic policy so later human decisions can rely on traceable deterministic findings.

## ADDED Requirements

### Requirement: Snapshot inputs
The system SHALL persist the exact document IDs, versions, checksums, and policy version used by an analysis before processing.

#### Scenario: Analysis begins
- **WHEN** an authenticated analyst requests analysis for an eligible proposal
- **THEN** the stored analysis binds its current document snapshot and policy version

### Requirement: Validate extraction response
Java SHALL reject extracted evidence that references a document or version outside the analysis snapshot.

#### Scenario: Unknown evidence
- **WHEN** Python returns a field referencing an unbound document version
- **THEN** Java marks the analysis failed without persisting that field as fact

### Requirement: Apply synthetic rules
Java SHALL calculate income consistency using decimal arithmetic and the versioned synthetic policy, returning NOT_EVALUATED when required evidence is missing or ambiguous.

#### Scenario: Income mismatch
- **WHEN** declared income is BRL 8000.00, extracted income is BRL 7500.00, and policy tolerance is 5 percent
- **THEN** the analysis records an INCOME_CONSISTENCY warning with the document evidence reference

#### Scenario: Missing income
- **WHEN** no factual income is extracted
- **THEN** income consistency is NOT_EVALUATED and no value is invented

### Requirement: Retrieve dossier
The API SHALL return an authenticated dossier containing separate declared facts, extracted fields, rule findings, policy identity, and document snapshot identity.

#### Scenario: Completed analysis
- **WHEN** an analyst retrieves a completed analysis dossier
- **THEN** the response exposes the fields, findings, references, and versioned inputs used
