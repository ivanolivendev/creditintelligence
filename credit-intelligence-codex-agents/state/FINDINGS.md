# FINDINGS

## Adjudicated and resolved

| ID | Severity | Result |
|---|---:|---|
| FIN-FUNC-001 | P1 | Resolved: incomplete factual income produces non-evaluated income consistency/stability findings. |
| FIN-FUNC-002 | P1 | Resolved: semantic salary-credit duplicates require review and are not summed. |
| UX-001 | P1 | Resolved: frontend requires three distinct matching financial periods. |
| SEC-001 | P1 | Local exposure mitigated: AWS key deactivated and external credential values cleared from local `.env`; bureau-provider revocation was not independently verified. |
| UX-002 | P2 | Resolved: MOCK and BEDROCK are labeled distinctly. |
| UX-003 | P2 | Resolved: global and upload-queue errors use alert semantics. |
| ARCH-001 | P2 | Resolved for the analysis data boundary with `AnalysisDataPort`; broader legacy coupling remains documented. |
| ARCH-002 | P2 | Resolved: OpenAPI covers implemented public routes and analysis schemas. |
| PERF-004 | P3 | Deferred/accepted for PoC: item-level writes remain small-scope and functionally correct. |

## Downgraded or deferred

Retry stacking, repeated PDF materialization, per-document S3 client creation and Python reference-validation asymmetry remain P3/defense-in-depth concerns, not P1 blockers for the local PoC.

## Open limitations

Testcontainers contract integration is blocked by Docker socket isolation inside the Maven test container. Bedrock real remains partial. The PoC remains synthetic, synchronous and demo-authenticated.
