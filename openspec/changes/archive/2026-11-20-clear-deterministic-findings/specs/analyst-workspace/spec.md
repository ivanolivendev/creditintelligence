# Analyst workspace finding clarity

## ADDED Requirements

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
