# Design

## Context

See proposal.md. G1 stores PDFs privately and versions their metadata. Python can read the Compose volume but has no extraction contract. Java owns proposal state and PostgreSQL.

## Goals / Non-Goals

**Goals:** Synchronous analysis with persisted CREATED/RUNNING/COMPLETED/FAILED state; typed Java/Python contract; deterministic mock fixtures and policy.

**Non-Goals:** Durable asynchronous queue, OCR, real Bedrock, and human decision are later changes. The synchronous API does not claim 202 durability.

## Decisions

- Java snapshots metadata and policy in one transaction, then calls Python over private HTTP outside the transaction; completion is a second transaction. A failed call leaves a persistent FAILED_RETRYABLE state and sanitized error.
- Python accepts only generated relative object keys under the read-only volume, verifies SHA-256, and parses each page with pypdf 7.1.1. A shared internal token protects the endpoint in local mode; AWS will replace it with service identity/private routing.
- Extraction fields carry document ID, version, status, decimal string, page, snippet, and evidence reference. Java checks all IDs, versions, evidence page, and statuses before persistence.
- Java loads one seeded fictional policy row and verifies its checksum over config JSON and text. Rule evaluation uses BigDecimal with explicit scale/rounding. Findings are persisted separately from any future AI explanation.
- The API is synchronous and returns a completed analysis or a failure status; it does not return 202. A later reliability change will add idempotency and fencing for retries.

## Risks / Trade-offs

- Synchronous calls can time out → bounded HTTP timeout, persisted failure state, no claim of background durability.
- Shared local volume does not exist in AWS → storage adapter and Python document-access strategy must be replaced before cloud deployment.
- Demo regex recognizes only controlled fixtures → ambiguity and missing states are explicit; no claim of general PDF understanding.

## Migration Plan

Flyway V3 adds policy, analysis, snapshot, extracted field, and finding tables. It seeds only fictitious policy data. The migration is additive.
