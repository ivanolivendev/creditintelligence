# Delta for analyst workspace

## MODIFIED Requirements

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

### Requirement: Explicit uncertainty
The UI SHALL identify `MOCK` explanations as mock output and SHALL not label them as Bedrock inference. Actionable failures SHALL be announced with accessible status semantics.

#### Scenario: Mock provider
- **WHEN** the persisted explanation provider is `MOCK`
- **THEN** the workspace SHALL display `MOCK` and explain that it is deterministic demonstration output

#### Scenario: Ambiguous document
- **WHEN** a PDF contains two conflicting income amounts
- **THEN** the UI displays `AMBIGUOUS`, no factual income, and a `NOT_EVALUATED` income finding
