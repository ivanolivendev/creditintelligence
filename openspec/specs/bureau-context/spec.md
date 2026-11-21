# bureau-context Specification

## Purpose

Keep optional external credit bureau context separate from documentary facts and identify whether its source is a verified sandbox or a local synthetic fixture.

## Requirements

### Requirement: Label optional bureau context
The dossier SHALL identify bureau provider, environment, source status, consultation version, and limitations. Missing, unavailable, or invalid external data SHALL NOT be represented as zero restrictions or a favorable score.

#### Scenario: No verified sandbox
- **WHEN** no official sandbox contract and test credentials are available
- **THEN** the dossier shows NOT_REQUESTED or an explicitly MOCK fixture, with no claim of a remote consultation

#### Scenario: External failure
- **WHEN** an enabled sandbox call times out or returns an authentication or server error
- **THEN** the bureau status is UNAVAILABLE and the documentary assessment remains reviewable

### Requirement: Use provider-independent report
The financial assessment SHALL consume a normalized report whose score, restrictions, and coverage remain optional and whose provider-specific fields do not cross the bureau boundary.

#### Scenario: Incomplete report
- **WHEN** a provider omits restrictions or score scale
- **THEN** those fields remain unknown and no probability of default is derived
