# Harden Financial Analysis and Analyst Contracts

## Why

The adjudicated audit found two P1 financial integrity defects and several P2 contract and analyst-workspace defects. The current PoC must not present a positive financial finding from incomplete evidence, accept semantically duplicated salary credits, or claim readiness and provider behavior that differ from the backend and runtime configuration.

## What changes

- Treat incomplete or invalid income periods as not evaluated for factual income consistency.
- Detect equivalent salary-credit duplicates within a statement period and prevent a false salary match.
- Make frontend readiness use the same matching-period rule as the backend.
- Label mock and Bedrock explanation providers accurately and announce UI errors accessibly.
- Complete the checked-in OpenAPI contract for implemented read and analysis routes.
- Introduce a minimal application port for analysis persistence so the main analysis use case no longer owns infrastructure SQL details.

## Out of scope

- OCR, durable queues, AWS deployment, production identity, multi-tenant isolation, and enterprise performance work.
- Automatic credit approval or changes to the demo financial policy.
- Deleting or rewriting the local `.env`; credential rotation remains an operational security action.

## Success criteria

- All P1 regression scenarios have automated tests and no longer produce false positive findings.
- The six-document journey cannot claim readiness for mismatched periods.
- Mock output is visibly identified as mock.
- OpenAPI describes the routes consumed by the UI and the analysis request body.
- Relevant Java, Python, and frontend checks pass, or failures are explicitly classified.
