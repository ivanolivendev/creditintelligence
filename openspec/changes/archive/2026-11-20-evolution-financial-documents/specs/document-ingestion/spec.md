# Spec Delta

## ADDED Requirements

### Requirement: Upload period-specific financial PDFs
The existing authenticated upload SHALL accept PAYSLIP with a competence month and BANK_STATEMENT with a statement period, keeping each version and checksum. A new period SHALL coexist with earlier periods; replacement of a period SHALL retain its previous version.

#### Scenario: Three payslips and statements
- **WHEN** an analyst uploads three distinct competence months and three corresponding statement periods
- **THEN** all six PDFs appear in the proposal document list and can be fixed in one analysis snapshot

#### Scenario: Duplicate period replacement
- **WHEN** an analyst uploads another payslip for an existing competence
- **THEN** the new document version is available and the previous version remains in history

#### Scenario: Missing period metadata
- **WHEN** a PAYSLIP or BANK_STATEMENT upload omits its required period
- **THEN** the API rejects the request without retaining a document

## MODIFIED Requirements

### Requirement: Document versions
The system SHALL assign monotonically increasing versions per proposal and document type for legacy documents, and per proposal, document type, and period for period-specific financial documents. It SHALL preserve earlier versions.

#### Scenario: Second version
- **WHEN** an analyst uploads another valid legacy PDF of the same type for the same proposal
- **THEN** the new document receives the next version and the earlier version remains listed

#### Scenario: Financial period replacement
- **WHEN** an analyst uploads another valid financial PDF of the same type and period for the same proposal
- **THEN** that period receives the next version while other periods and earlier versions remain listed
