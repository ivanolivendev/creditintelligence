# Tasks

## 1. Documents and extraction

- [x] 1.1 Add period-aware PAYSLIP and BANK_STATEMENT upload/snapshot with additive migration and API tests for 3+3 documents, duplicate periods, and legacy compatibility.
- [x] 1.2 Add synthetic six-PDF fixtures and Python typed extraction with evidence, missing/ambiguous/unknown tests; verify pytest and Ruff in Compose.
- [x] 1.3 Validate the V2 extraction contract in Java, persist fields/transactions with source references, and verify contract tests in Compose.

## 2. Financial analysis

- [x] 2.1 Implement pure Java profile, income/reconciliation/obligation/capacity/Price engine with independent BigDecimal tests for Pack cases A–D/G.
- [x] 2.2 Add versioned demo financial policy, persist assessment atomically with analysis, and verify snapshot/fingerprint/history via Compose integration tests.
- [x] 2.3 Extend the dossier and existing explanation request to use Java assessment; verify mock and invalid-provider behavior without paid calls.

## 3. Review and optional external context

- [x] 3.1 Add provider-independent bureau report/port, explicit consultation endpoint, versioned snapshot, and one verified Consult Center v3 sandbox adapter; verify 200, error, MOCK, and unknown restriction cases.
- [x] 3.2 Extend React review flow for six documents, evidence, assessment/simulation, bureau status, and decision; verify build/lint/format and local UI journey.
- [x] 3.3 Document commands, compatibility, architecture decision, sandbox access evidence, and limitations in README and execution report; verify all claimed gates with Compose smoke.

## Workflow follow-up

- Review and archive the OpenSpec change only after verified tasks and project gates are complete.
