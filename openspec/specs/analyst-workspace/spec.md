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
The UI SHALL identify `MOCK` explanations as mock output and SHALL not label them as Bedrock inference. Actionable failures SHALL be announced with accessible status semantics.

#### Scenario: Mock provider
- **WHEN** the persisted explanation provider is `MOCK`
- **THEN** the workspace SHALL display `MOCK` and explain that it is deterministic demonstration output

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

### Requirement: Guided financial document intake
The workspace SHALL label the financial flow ready only when the required payslip and bank-statement period sets match. It SHALL identify missing and mismatched periods before analysis.

#### Scenario: Mismatched periods
- **WHEN** three payslips and three statements are present but their period sets differ
- **THEN** the workspace SHALL not display a ready state and SHALL identify the periods that need correction

#### Scenario: Complete six-document batch
- **WHEN** the analyst selects three payslips and three statements whose filenames identify three matching periods
- **THEN** the workspace pre-fills type and period for every file, allows review before upload, and shows `3/3` payslips, `3/3` statements, and `3/3` matched periods after upload

#### Scenario: Conflicting batch metadata
- **WHEN** two selected files target the same type-period slot or a selected type or period conflicts with detected filename metadata
- **THEN** the workspace identifies each affected file and prevents batch submission until the conflict is corrected

#### Scenario: Partial dossier preflight
- **WHEN** fewer than three matching payslip and statement periods are current
- **THEN** the workspace labels the expected result as partial and explains that missing financial indicators remain not evaluated

### Requirement: Operational analysis failure messages
The workspace SHALL translate analysis failure states into corrective Portuguese guidance and SHALL keep internal error codes in optional technical details rather than using them as the primary message.

#### Scenario: Permanent extraction contract failure
- **WHEN** analysis ends as `FAILED_PERMANENT` with `INVALID_EXTRACTION_RESPONSE`
- **THEN** the workspace explains that a document type or period must be corrected and does not describe the failure as retryable

#### Scenario: Temporary document service failure
- **WHEN** analysis ends as `FAILED_RETRYABLE` because document intelligence is unavailable
- **THEN** the workspace explains that uploaded files were preserved and that the analyst can retry later

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
