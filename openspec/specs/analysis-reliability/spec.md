# analysis-reliability Specification

## Purpose

Protect analysis requests from duplicate work and ensure only the current attempt can persist results after retries or concurrent submissions.

## Requirements

### Requirement: Request idempotency
The API SHALL persist an optional idempotency key scoped to the proposal and bind it to the complete input fingerprint. A repeated key with the same fingerprint SHALL return the same analysis ID. A repeated key after the input changes SHALL return 409.

#### Scenario: Identical concurrent requests
- **WHEN** two callers submit the same key and unchanged input
- **THEN** at most one analysis row is created and both receive its ID

#### Scenario: Key reuse after document version
- **WHEN** a document version changes and the caller reuses a key
- **THEN** the API returns 409 and does not create an analysis

### Requirement: Fenced retry
A retryable or expired analysis SHALL acquire a new attempt number and lease token. Completion and failure SHALL require the current lease token, so a late prior attempt cannot modify the result.

#### Scenario: Late prior attempt
- **WHEN** a prior attempt returns after another lease has been acquired
- **THEN** its response is ignored and the current attempt's result remains authoritative
