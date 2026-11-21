# Design

## Context

See proposal.md. The baseline has an authenticated Spring Boot process, Flyway V1 with applicant/proposal tables, PostgreSQL, and a private Compose network. No business endpoint exists yet.

## Goals / Non-Goals

**Goals:** Separate REST, application, domain, and JPA/storage code; keep document bytes immutable and private; verify behavior against PostgreSQL through Compose.

**Non-Goals:** Analysis, OCR, real S3, Bedrock, and production identity are later changes.

## Decisions

- The proposal application service owns validation beyond request syntax and persists applicant and proposal in one transaction. Generated UUIDs are the public IDs. A synthetic identifier may occur on multiple proposals only through the same applicant record; conflicting applicant names are rejected.
- The document module uses a local storage adapter backed by a private Docker volume. Java stores a generated object key, never a user filename. The adapter can later be replaced by S3 without changing document application logic.
- Apache PDFBox 3.0.8 verifies that the file parses and contains selectable text. This bounds the initial format to digital PDFs and avoids pretending that a `%PDF` prefix alone proves validity.
- A pessimistic lock on the proposal row serializes document version allocation per proposal. Metadata is committed after object storage succeeds. On transaction failure, the service deletes the just-written object as compensation.
- Spring Security's existing demo identity protects these endpoints. HTTP Basic remains local-only; cloud identity is a later gate.

## Risks / Trade-offs

- File storage and PostgreSQL cannot commit atomically → generated object keys and compensating deletion; later work must test orphan cleanup and S3 failure.
- Large hostile PDFs can consume parser resources → 10 MiB hard limit and bounded parser input; stronger malware controls are outside the demo.
- Compose integration tests share a database → use unique synthetic IDs and remove only their own records, or isolate a test database before CI.

## Migration Plan

Flyway V2 adds document metadata and the `(proposal_id, type, document_version)` uniqueness constraint. Existing V1 data remains valid. Local rollback is a code rollback only; migration is additive and must not be reversed destructively.
