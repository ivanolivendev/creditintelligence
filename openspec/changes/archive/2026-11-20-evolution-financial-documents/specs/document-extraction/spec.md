# Spec Delta

## ADDED Requirements

### Requirement: Extract supported payslip fields
For a supported synthetic digital payslip, the document service SHALL return competence, gross and net salary, and visible discounts with document/version/page/snippet evidence. An unsupported or uncertain field SHALL remain nonfactual.

#### Scenario: Supported payslip
- **WHEN** a valid synthetic payslip has one labeled net salary and competence
- **THEN** the service returns normalized BRL amount and evidence tied to that document version

#### Scenario: Ambiguous net salary
- **WHEN** conflicting labeled net salary amounts appear
- **THEN** net salary is AMBIGUOUS with no factual value

### Requirement: Extract supported bank transactions
For a supported synthetic digital statement, the service SHALL return statement period and original transaction date, direction, amount, description, and evidence. Unknown transaction purpose SHALL remain UNKNOWN.

#### Scenario: Unknown credit
- **WHEN** a statement contains an unlabeled credit
- **THEN** it is returned as a credit with UNKNOWN classification, not as salary

#### Scenario: Unsupported layout
- **WHEN** the statement layout cannot be parsed safely
- **THEN** extraction reports INVALID or UNSUPPORTED_LAYOUT without fabricated transactions
