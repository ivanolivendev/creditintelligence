# Spec Delta

## Purpose

Provide an authenticated, persistent record of synthetic credit proposals so later analysis can bind documents and human decisions to stable proposal identities.

## ADDED Requirements

### Requirement: Create proposal
The system SHALL create a proposal with a generated ID, synthetic applicant identity, nonnegative declared monthly income, explicit currency, and initial DRAFT status.

#### Scenario: Valid proposal
- **WHEN** an authenticated analyst submits valid synthetic applicant data, income, and BRL currency
- **THEN** the API returns 201 with the proposal ID and persists the proposal

#### Scenario: Invalid proposal
- **WHEN** the applicant name or synthetic identifier is blank, income is negative, or currency is unsupported
- **THEN** the API returns a validation error and persists no proposal

### Requirement: Retrieve proposal
The system SHALL return a proposal by ID only to an authenticated analyst.

#### Scenario: Existing proposal
- **WHEN** an authenticated analyst retrieves an existing proposal ID
- **THEN** the API returns its applicant, income, currency, status, and creation timestamp

#### Scenario: Unknown proposal
- **WHEN** an authenticated analyst retrieves an unknown proposal ID
- **THEN** the API returns 404

#### Scenario: Unauthenticated request
- **WHEN** a request without valid credentials accesses a proposal endpoint
- **THEN** the API returns 401 without proposal details
