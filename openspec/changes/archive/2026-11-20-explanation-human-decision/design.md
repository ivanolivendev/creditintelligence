# Design

## Context

G2 persists a deterministic dossier and policy checksum. Python has a private internal token. No AI explanation or human decision table exists.

## Goals / Non-Goals

**Goals:** Isolate interpretation from rules, validate model references, record analyst identity from Spring Security, and prevent decisions on stale snapshots.

**Non-Goals:** Automated credit approval and real institutional policy. Bedrock access remains a separate cloud/provider gate.

## Decisions

- Python exposes `/internal/v1/explain` with a deterministic MOCK provider for local use. Java sends only normalized field values, immutable findings, evidence IDs, and versioned policy text; it does not send raw PDF instructions.
- Java validates returned schema identifiers against exactly the supplied sets before persisting an explanation. Provider errors map to UNAVAILABLE, invalid references to INVALID_OUTPUT. Neither status changes findings.
- Decision writes use one PostgreSQL transaction with a row lock on the proposal, a unique final-decision constraint, and an audit insert. The request carries analysis ID and document snapshot hash; the actor comes from authentication.
- The current snapshot is recomputed from latest document versions before decision, so a new upload makes the old analysis stale for a new decision. Existing historical decisions remain unchanged.

## Risks / Trade-offs

- MOCK text does not prove Bedrock behavior → provider is exposed as MOCK in API and UI.
- HTTP Basic is local demo identity → AWS requires identity/role design before deployment.
- Physical audit immutability is not claimed → append-only application behavior and DB privileges are the demonstrative control.

## Migration Plan

Flyway V4 adds AI explanation, human decision, and audit tables. Migration is additive; previously completed analyses remain reviewable with NOT_REQUESTED explanation.
