# Spec Delta

## Purpose

Preserve private, versioned PDF evidence for a synthetic proposal so each later analysis can identify exactly which document bytes and version it used.

## ADDED Requirements

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
The system SHALL assign monotonically increasing versions per proposal and document type and preserve earlier versions.

#### Scenario: Second version
- **WHEN** an analyst uploads another valid PDF of the same type for the same proposal
- **THEN** the new document receives the next version and the earlier version remains listed

### Requirement: List documents
The system SHALL list metadata and checksums of documents for an authenticated proposal request without exposing raw object storage paths.

#### Scenario: List existing documents
- **WHEN** an authenticated analyst lists documents for an existing proposal
- **THEN** the API returns document IDs, types, versions, checksums, and timestamps ordered by type and version

#### Scenario: Unknown proposal
- **WHEN** an authenticated analyst lists documents for an unknown proposal
- **THEN** the API returns 404
