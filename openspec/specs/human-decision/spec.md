# human-decision Specification

## Purpose

Record a demonstrative credit outcome only as an authenticated analyst action tied to the exact completed analysis and document snapshot the analyst reviewed.

## Requirements

### Requirement: Record human decision
The system SHALL accept a supported outcome and nonblank reason for a completed analysis, deriving analyst identity from authentication and writing a decision and audit record.

#### Scenario: Authorized decision
- **WHEN** an authenticated analyst submits outcome, reason, analysis ID, and matching snapshot hash
- **THEN** the API returns 201 and persists the actor, outcome, analysis reference, and audit event

### Requirement: Reject stale or unauthorized decisions
The system SHALL reject decisions for an outdated snapshot, unfinished analysis, duplicate final outcome, or unauthenticated request.

#### Scenario: Newer document exists
- **WHEN** a document version changes after the analysis was completed
- **THEN** a decision against the older snapshot returns 409 without a decision row

#### Scenario: Unauthenticated decision
- **WHEN** a request without valid credentials attempts a decision
- **THEN** the API returns 401 and writes no decision
