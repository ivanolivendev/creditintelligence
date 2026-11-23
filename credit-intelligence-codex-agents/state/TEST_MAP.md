# TEST MAP

## Executed gates

- Java `mvn verify` in Compose: PASS, 54 tests, 0 failures/errors, Checkstyle 0.
- Financial focused tests: PASS, 15 tests.
- Frontend Vitest: PASS, 6 tests; TypeScript/Vite build, ESLint and Prettier: PASS.
- Python Docker tests: PASS, 10 tests; Ruff and compileall: PASS.
- `docker compose config --quiet`: PASS.
- OpenAPI parsed with `js-yaml`: PASS.
- OpenSpec strict validation: PASS.
- `git diff --check`: PASS.

## Blocked evidence

`ApiContractValidationIntegrationTest` was blocked because the Maven test container cannot access the Docker socket required by Testcontainers. Bedrock real and bureau external paths were not revalidated in this change.

## Regression coverage added

Incomplete factual income, semantic salary duplicates, duplicate financial periods, provider labeling, and accessible upload errors.
