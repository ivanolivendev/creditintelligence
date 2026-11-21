# Tasks

## 1. Proposal API

- [x] 1.1 Add validated proposal request/response contracts and Java domain/application/JPA layers; verify valid POST returns 201 and GET returns the persisted values against Compose PostgreSQL (T01).
- [x] 1.2 Add consistent errors and authentication coverage; verify invalid POST returns 400 without a row, unknown GET returns 404, and missing credentials return 401 (T02).

## 2. Document ingestion

- [x] 2.1 Add Flyway document metadata and private storage adapter; verify migration V2 succeeds and object is stored under a generated key.
- [x] 2.2 Add PDF validation, upload and list APIs; verify valid digital PDF returns version/checksum and invalid or oversized files persist nothing (T03–T04).
- [x] 2.3 Verify a second upload of the same type advances the version while the first version remains listed.

## 3. Integration gate

- [x] 3.1 Run Maven build and relevant tests entirely through Compose; verify all three services healthy and inspect migration, security, and storage behavior.
- [x] 3.2 Update README and gate evidence with the commands and observed results; verify OpenSpec strict validation and review the diff.

## Workflow follow-up

- Archive the change after verification and preserve the resulting capability specs.
