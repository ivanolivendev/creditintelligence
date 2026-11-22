# document-ingestion Specification

## Purpose

Preserve private, versioned PDF evidence for a synthetic proposal so each later analysis can identify exactly which document bytes and version it used.

## Requirements

### Requirement: Upload digital PDF
The system SHALL accept an authenticated multipart PDF upload of at most 10 MiB for an existing proposal, verifying its PDF signature and readable digital text before storing it privately.

#### Scenario: Valid PDF
- **WHEN** an analyst uploads a valid digital PDF with a supported document type
- **THEN** the API returns 201 with a generated document ID, version, SHA-256 checksum, and upload timestamp

#### Scenario: Invalid content
- **WHEN** an analyst uploads a non-PDF, unreadable PDF, empty file, or PDF without selectable text
- **THEN** the API rejects the upload and creates neither document metadata nor a retained object

#### Scenario: Oversized file
- **WHEN** an analyst uploads a file larger than 10 MiB
- **THEN** the API returns 413 and does not retain the file

### Requirement: Document versions
The system SHALL assign monotonically increasing versions per proposal and document type for legacy documents, and per proposal, document type, and period for period-specific financial documents. It SHALL preserve earlier versions.

#### Scenario: Second version
- **WHEN** an analyst uploads another valid legacy PDF of the same type for the same proposal
- **THEN** the new document receives the next version and the earlier version remains listed

#### Scenario: Financial period replacement
- **WHEN** an analyst uploads another valid financial PDF of the same type and period for the same proposal
- **THEN** that period receives the next version while other periods and earlier versions remain listed

### Requirement: List documents
The system SHALL list metadata and checksums of documents for an authenticated proposal request without exposing raw object storage paths.

#### Scenario: List existing documents
- **WHEN** an authenticated analyst lists documents for an existing proposal
- **THEN** the API returns document IDs, types, versions, checksums, and timestamps ordered by type and version

#### Scenario: Unknown proposal
- **WHEN** an authenticated analyst lists documents for an unknown proposal
- **THEN** the API returns 404

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
