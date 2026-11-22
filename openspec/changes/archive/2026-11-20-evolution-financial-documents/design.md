# Design

## Context

The running V3 implementation has versioned PDF storage, a single latest document per type in snapshots, a one-field Python extraction contract, Java income-consistency findings, persistent analysis leases/idempotency, and post-analysis mock/Bedrock explanation. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:** Add six-document analysis with no change to legacy route behavior; preserve historical inputs; keep calculations pure in Java and optional external context clearly labeled.

**Non-Goals:** Universal OCR/layout recognition, live CPF queries, automated approval, cloud deployment.

## Decisions

1. **Period metadata and snapshot:** Add nullable `period_key`, `period_start`, `period_end` to document metadata. Existing types retain per-type versioning; financial uploads version per type and period. Select the latest version for each financial period and the latest per legacy type. This extends the snapshot without changing stored V3 analyses. Alternative of treating each document as a new type would not scale to periods.
2. **Extraction contract:** Use `contractVersion=2.0` when the snapshot contains financial types; retain V1 processing for legacy snapshots. Python returns structured fields and transactions, each bound to a snapshot document and page. Java validates identities, amounts, and evidence before persistence. Alternative of passing freeform JSON into Java would weaken the trust boundary.
3. **Assessment:** Assemble typed periods in Java and invoke a pure Java engine with `BigDecimal`. Persist `profile_json` and `result_json` in a new table keyed by analysis ID, with policy and rule version. An additive policy version activates only for financial snapshots so legacy demonstrations remain stable. Alternative of replacing V3 policy would break historical behavior.
4. **Explanation:** Extend the existing explanation request with an optional assessment summary, preserving allowed references and fallback. Python's existing Bedrock client remains the single model gateway.
5. **Bureau:** The official Consult Center v3 OpenAPI published a free static sandbox token and synthetic test identifiers; a single remote HTTP 200 was observed on 20/11/2026. Add an explicit authenticated `POST /bureau-consultations` for named test cases, never arbitrary CPF input. Persist the normalized report and local consultation ID before analysis. `Analysis.start` snapshots the latest consultation ID in its fingerprint and the report in the assessment. A new consultation therefore creates a new analysis. Default `mock` uses a labeled fixture; `sandbox` requires a token from environment. No remote call occurs on GET or implicit analysis. Unknown restrictions remain null because the inspected Credit Bureau response has no canonical restriction count.

## Risks / Trade-offs

- Strict controlled-layout parsing misses other PDFs → return INVALID/MISSING with explicit limitation.
- An older analysis completing after new upload → existing lease/snapshot checks still apply; new upload produces another fingerprint.
- Decimal math or duplicate obligations may overstate capacity → test independent fixtures and mark uncertain obligations for review.
- Bedrock may be unavailable → mock/default and separate explanation status; no financial data is recomputed by Python.
- Vendor schema or sandbox access can change → validate the v3 envelope and fields, preserve `INVALID_RESPONSE`/`UNAVAILABLE`, and never infer zero restrictions.

## Migration Plan

Add Flyway migrations only. Old document rows retain null period metadata and old dossiers remain readable. Rebuild Compose images, run mock tests and smoke, then verify current fixtures. No volume deletion.
