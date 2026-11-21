---
name: credit-verification
description: Independently verify Credit Intelligence changes against Blueprint V3.0 invariants, quality gates, concurrency, security and end-to-end behaviors; report evidence-based PASS/PARTIAL/FAIL/BLOCKED.
---
# Credit Intelligence verification
- Read Blueprint V3.0 test matrix T01-T20 and root AGENTS.md. Inspect code and OpenSpec artifacts. Do not assume completed features.
- Run available builds, static checks and tests; distinguish skipped, failed and blocked checks explicitly.
- Prioritize transaction boundaries, PostgreSQL uniqueness/locking, stale attempt lease tokens, snapshot/policy version, document evidence origin, IA schema/reference validation, no authorization bypass.
- Verify local Compose startup and E2E journey when Docker is accessible. Negative tests must include invalid upload, duplicate requests, conflicting payload, concurrent calls, Python/Bedrock failures and unauthorized decisions.
- Produce a brief table: requirement/test ID, command, result, evidence, defects; rate PASS/PARTIAL/FAIL/BLOCKED.
- Do not silently weaken assertions or disable static analysis to get a green result. Recommend fixes; do not declare release ready without evidence.
