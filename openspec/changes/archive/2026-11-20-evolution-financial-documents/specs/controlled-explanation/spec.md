# Spec Delta

## ADDED Requirements

### Requirement: Explain the financial assessment after calculation
The optional explanation provider SHALL receive only the completed Java assessment, deterministic findings, allowed policy references, and allowed evidence references. Its output SHALL NOT change assessment values, findings, or human decisions.

#### Scenario: Provider output
- **WHEN** mock or Bedrock returns a valid explanation for a completed financial assessment
- **THEN** the dossier labels the observed provider and preserves every Java-calculated value

#### Scenario: Provider unavailable
- **WHEN** explanation fails after assessment persistence
- **THEN** assessment and findings remain reviewable with an unavailable explanation status
