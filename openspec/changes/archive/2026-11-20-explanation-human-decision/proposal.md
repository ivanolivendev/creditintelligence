# Proposal

## Why

The dossier contains validated facts and rules but has no controlled explanation or recorded analyst decision. This change completes the human review path without granting authority to model output.

## What Changes

- Generate a clearly labeled mock explanation from policy and deterministic findings; validate all returned finding, policy, and evidence references in Java.
- Persist AVAILABLE, UNAVAILABLE, or INVALID_OUTPUT explanation status separately from deterministic findings.
- Record an authenticated human decision with reason, analysis ID, snapshot hash, actor, timestamp, and audit event; reject stale or unfinished analyses.

## Capabilities

### New Capabilities

- `controlled-explanation`: Optional explanation with schema/reference validation and explicit provider status.
- `human-decision`: Authenticated, snapshot-bound decision and audit trail.

### Modified Capabilities

None.

## Impact

Python mock explanation endpoint and tests; Java explanation and decision modules, Flyway V4, security and API tests, dossier response, Compose smoke journey.
