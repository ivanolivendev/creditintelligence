---
name: credit-implementation
description: Implement a small, verifiable Credit Intelligence vertical slice under the Master Blueprint V3.0 with OpenSpec, Java/Python/React boundaries, tests and local atomic commits.
---
# Credit Intelligence implementation
1. Inspect repository and read the Blueprint and root AGENTS.md; identify actual existing state.
2. Locate active OpenSpec change and verify requirements, acceptance criteria, architecture decision and dependency contracts; propose smallest viable slice if missing.
3. Implement with the closed stack, preserve domain invariants, add Flyway migrations and contract tests as appropriate.
4. Run focused format/lint, tests and relevant integration tests; record actual command/exit codes and blockers.
5. Review diff for unnecessary abstractions, data leaks, business logic placement and unhandled failures.
6. Update verified OpenSpec tasks and make a small Conventional Commit, local only. Summarize hash, tests, risks.

Prefer an end-to-end working path over speculative platform expansion. Never claim an AWS or Bedrock real integration based on mocks.
