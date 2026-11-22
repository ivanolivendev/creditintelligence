# Tasks

## Financial integrity

- [x] Add completeness-aware income aggregation and prevent positive income consistency findings from incomplete periods.
- [x] Add semantic salary-credit duplicate detection without changing the documented policy thresholds.
- [x] Add unit and integration regression tests for missing income, ambiguous income, semantic duplicates, and valid three-period matching.

## Analyst workspace

- [x] Make frontend readiness require matching payslip and statement period sets and show actionable mismatch details.
- [x] Correct mock/Bedrock provider copy and add accessible error announcements.
- [x] Add frontend regression tests for mismatched periods, provider labeling, and error semantics where test infrastructure permits.

## Contract and architecture

- [x] Complete the OpenAPI paths and schemas for implemented proposal, analysis, dossier, bureau, and analysis-request behavior.
- [x] Add an analysis persistence port and route `AnalysisService` through it without changing transaction or lease behavior.

## Verification and security

- [x] Run targeted Java, Python, frontend, Compose, formatting, lint, and contract checks.
- [x] Run the adversarial review scenarios and record any unresolved credential-rotation limitation without exposing secrets.
