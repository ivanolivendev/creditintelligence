# Spec Delta

## Purpose

Offer analysts a clearly labeled interpretation of validated findings while preventing generated text or references from changing factual extraction, rule outcomes, or human authority.

## ADDED Requirements

### Requirement: Explain validated findings
The system SHALL provide an explanation labeled with provider, model, and prompt version, using only the analysis findings and matching policy context.

#### Scenario: Valid mock response
- **WHEN** the mock provider returns a schema-valid explanation with references present in the supplied findings, evidence, and policy
- **THEN** Java stores AVAILABLE without changing extracted fields or findings

### Requirement: Reject unsupported references
Java SHALL reject explanation references not present in the supplied context.

#### Scenario: Invented evidence
- **WHEN** a provider returns an evidence or policy reference that was not supplied
- **THEN** the explanation is INVALID_OUTPUT and deterministic results remain available

### Requirement: Preserve dossier on provider failure
The system SHALL show UNAVAILABLE when explanation generation times out or fails while keeping the deterministic dossier reviewable.

#### Scenario: Provider unavailable
- **WHEN** Python or the configured model cannot produce an explanation
- **THEN** the analysis remains completed, findings remain unchanged, and explanation status is UNAVAILABLE
