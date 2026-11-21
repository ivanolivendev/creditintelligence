# analyst-workspace Specification

## Purpose

Give analysts a synthetic local workflow to review proposals, document evidence, deterministic findings, optional explanations, and human decisions.

## Requirements

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

### Requirement: Review a six-document financial dossier
The analyst workspace SHALL allow a demo analyst to upload three payslips and three statements, start analysis, inspect financial indicators, source evidence, simulation, bureau status, explanation provider, and record a human decision.

#### Scenario: Synthetic journey
- **WHEN** an authenticated analyst completes the synthetic six-document flow
- **THEN** the workspace shows separate extracted facts, Java calculations, MOCK or verified external context, optional explanation, and human decision controls

#### Scenario: Incomplete evidence
- **WHEN** a required period is missing or ambiguous
- **THEN** the workspace explains what needs review and does not present a verified capacity

### Requirement: Actionable deterministic findings
The workspace SHALL explain deterministic findings in plain Portuguese using the policy and evidence attached to the analyzed snapshot, without exposing raw internal finding codes as the primary title.

#### Scenario: Income differs from proposal
- **WHEN** extracted monthly income exceeds the policy tolerance relative to the declared income
- **THEN** the workspace shows both amounts, the difference, the policy tolerance, the source document and page, and a human-review next step
- **AND** it SHALL NOT recommend automatic approval or rejection

#### Scenario: Required document set is complete
- **WHEN** every policy-required document type is present in the analyzed snapshot
- **THEN** the workspace says the required set is complete and names the required document types in Portuguese

#### Scenario: Required documents are missing
- **WHEN** one or more policy-required document types are absent
- **THEN** the workspace lists each missing type in Portuguese and tells the analyst to upload it before completing review

#### Scenario: Income cannot be evaluated
- **WHEN** income extraction is missing or ambiguous
- **THEN** the workspace explains that the comparison could not be made, preserves the uncertainty, and gives a corrective next step
