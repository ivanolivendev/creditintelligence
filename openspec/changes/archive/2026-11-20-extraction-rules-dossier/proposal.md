# Proposal

## Why

Versioned documents are stored, but the analyst cannot see their contents as evidence or evaluate the synthetic policy. This change makes a document snapshot produce a reviewable deterministic dossier.

## What Changes

- Python extracts income from a digital PDF and returns a typed result with document/version/page/snippet evidence or a nonfactual missing/ambiguous/invalid state.
- Java snapshots document versions and a policy version, validates the Python response, calculates deterministic findings, and persists the result.
- An authenticated API returns analysis status and a dossier that separates declared data, extracted fields, findings, and policy references.

## Capabilities

### New Capabilities

- `document-extraction`: Typed extraction with source evidence and explicit uncertainty.
- `deterministic-analysis`: Snapshot-bound policy evaluation and reviewable dossier.

### Modified Capabilities

None.

## Impact

Python FastAPI/pypdf schemas and tests; Java analysis/policy/integration modules, Flyway tables, Compose internal token and shared read-only document volume, integration tests and API documentation.
