# Analyst workspace

## ADDED Requirements

### Requirement: Complete review journey
An authenticated analyst SHALL be able to create or load a synthetic proposal, upload a PDF, start an analysis, inspect document and policy evidence, and record a human decision.

#### Scenario: Synthetic demonstration
- **WHEN** an analyst completes the workflow with a synthetic income document
- **THEN** the UI shows the persisted analysis, evidence, mock explanation and recorded decision

### Requirement: Explicit uncertainty
The UI SHALL distinguish missing or ambiguous extracted facts, deterministic findings, and the optional AI explanation. It SHALL not describe mock output as real Bedrock inference or an automated credit approval.

#### Scenario: Ambiguous document
- **WHEN** a PDF contains two conflicting income amounts
- **THEN** the UI displays `AMBIGUOUS`, no factual income, and a `NOT_EVALUATED` income finding
