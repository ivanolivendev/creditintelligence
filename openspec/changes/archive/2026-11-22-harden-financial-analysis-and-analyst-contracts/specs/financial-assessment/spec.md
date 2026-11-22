# Delta for financial assessment

## MODIFIED Requirements

### Requirement: Consolidate valid income periods
The system SHALL calculate a reference monthly net income only from the complete set of required distinct, factual payslip periods. If any required period is missing, ambiguous, or invalid, the aggregate SHALL remain provisional and income consistency SHALL NOT be reported as a positive factual finding.

#### Scenario: Missing period
- **WHEN** one of three required payslips has missing or invalid net income and the declared income equals the average of the other two
- **THEN** the assessment remains `MORE_INFO_REQUIRED` and income consistency is `NOT_EVALUATED` or an equivalent uncertainty result, never `PASS`

#### Scenario: Three valid periods
- **WHEN** three distinct payslips contain BRL 7300.00, 8500.00, and 7400.00 net income
- **THEN** the reference income is BRL 7733.33 with three evidence-linked periods

### Requirement: Reconcile salary and obligations
The system SHALL not sum semantically duplicate eligible salary credits in the same period. A duplicate candidate SHALL be preserved as an uncertainty/finding requiring review, even when its technical transaction identifiers differ.

#### Scenario: Semantic duplicate cannot create a match
- **WHEN** two eligible credits in one statement period have the same normalized date, amount, currency, and salary-like identity but different technical IDs
- **THEN** the reconciliation SHALL not report a salary `MATCH` based on their combined amount and SHALL retain evidence of the duplicate for review

#### Scenario: Unrelated credit
- **WHEN** a bank statement contains an unclassified credit in addition to an eligible salary credit
- **THEN** the unclassified credit does not increase reference income

#### Scenario: Payroll deduction
- **WHEN** a payroll deduction is already included in the net salary and appears in bank evidence
- **THEN** the deduction is not subtracted again from capacity and uncertainty requests review
