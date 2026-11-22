# Reliable analysis execution

## Why

The synchronous analysis can be requested twice and a retryable failure currently stays stuck. The next slice needs a persistent request key, serialized snapshot creation, and a new lease for safe retries.

## What changes

- Bind an optional `Idempotency-Key` to an immutable analysis input fingerprint and analysis ID. Reuse the same key/input and reject a key with changed input.
- Serialize analysis start with document upload and human decision using the proposal row lock.
- Retry `FAILED_RETRYABLE` analyses, and recover an expired `RUNNING` lease, with a new attempt token. Reject stale completions.
- Keep Python and AI failures explicit without rewriting deterministic findings.
