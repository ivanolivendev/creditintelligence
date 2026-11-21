# controlled-explanation Specification

## Purpose

Offer analysts a clearly labeled interpretation of validated findings while preventing generated text or references from changing factual extraction, rule outcomes, or human authority.

## Requirements

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

### Requirement: Optional Bedrock explanation provider
The document service SHALL support an optional Amazon Bedrock provider for explaining Java-validated findings. The default provider SHALL remain `mock` and SHALL run without AWS credentials.

#### Scenario: Default local mode
- **WHEN** the local Compose stack starts without an explicit provider override
- **THEN** explanations use `MOCK` and no AWS credentials are required

#### Scenario: Bedrock provider selected
- **WHEN** `AI_PROVIDER=bedrock` is selected and temporary credentials and account model access are valid
- **THEN** Python requests an explanation from the configured Bedrock model and labels the response `BEDROCK`

### Requirement: Use temporary, scoped AWS credentials locally
The local Bedrock activation workflow SHALL use temporary credentials restricted to the required model invocation and SHALL NOT persist permanent AWS credentials in the repository or mount the host AWS credential directory into containers.

#### Scenario: Temporary credentials expire
- **WHEN** the local STS session expires
- **THEN** Bedrock requests fail safely without changing deterministic findings, and the operator must recreate the containers with a renewed session

### Requirement: Explain the financial assessment after calculation
The optional explanation provider SHALL receive only the completed Java assessment, deterministic findings, allowed policy references, and allowed evidence references. Its output SHALL NOT change assessment values, findings, or human decisions.

#### Scenario: Provider output
- **WHEN** mock or Bedrock returns a valid explanation for a completed financial assessment
- **THEN** the dossier labels the observed provider and preserves every Java-calculated value

#### Scenario: Provider unavailable
- **WHEN** explanation fails after assessment persistence
- **THEN** assessment and findings remain reviewable with an unavailable explanation status
