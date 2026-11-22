# document-extraction Specification

## Purpose

Turn synthetic digital PDF contents into typed fields with recoverable evidence while preserving missing, ambiguous, and invalid states as nonfactual results.

## Requirements

### Requirement: Extract income with evidence
The document service SHALL return a normalized monthly income only when a supported income proof contains one unambiguous labeled value, along with document ID, version, page, and source snippet.

#### Scenario: Single income value
- **WHEN** an authorized internal request references a valid income proof containing one labeled monthly income
- **THEN** the service returns EXTRACTED, decimal BRL value, and an evidence reference to its document version and page

### Requirement: Preserve uncertainty
The document service SHALL report MISSING, AMBIGUOUS, or INVALID when a field cannot be established from the document and SHALL NOT infer a value.

#### Scenario: Missing income
- **WHEN** a digital PDF has no labeled monthly income
- **THEN** the result is MISSING with no factual value

#### Scenario: Conflicting incomes
- **WHEN** an income proof contains different labeled monthly income values
- **THEN** the result is AMBIGUOUS with no factual value

### Requirement: Verify document references
The document service SHALL reject an unknown contract version, unsafe object key, or checksum mismatch.

#### Scenario: Tampered bytes
- **WHEN** an internal request supplies a checksum different from the referenced object
- **THEN** the service rejects processing without returning extracted facts

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
