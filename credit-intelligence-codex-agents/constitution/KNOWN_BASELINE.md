# KNOWN BASELINE FROM PREVIOUS TEST ENGINEERING

Use this only as historical evidence. Reconfirm against the repository before relying on exact counts.

Previously reported:
- 52 Java unit tests
- 18 Java integration tests
- 10 Python tests
- 80 total
- combined line coverage: 88.16%
- combined branch coverage: 66.47%
- integration line coverage: 75.90%
- integration branch coverage: 52.87%
- PIT score: 32%

Previously reported validation:
- Maven clean verify
- integration profile
- PostgreSQL 16.6 via Testcontainers
- Flyway with 10 migrations
- ArchUnit
- Checkstyle
- Ruff
- compileall
- PIT
- git diff --check

Known previous golden flow evidence included:
- six financial documents
- three payslip periods
- three bank periods
- available bureau query
- valid financial extraction
- salary reconciliation
- READY_FOR_REVIEW
- persisted fields/findings/assessment
- AI explanation AVAILABLE
- COMPLETED status
- final attempt 1

Known rollback evidence included:
- real PostgreSQL/Testcontainers
- induced intermediate write failure
- initial snapshot preserved
- partial writes removed
- assessment/findings not persisted
- transaction reverted
- analysis remained RUNNING for lease recovery

Important:
Do not reopen test engineering merely to inflate metrics.
Do not interpret PIT NO_COVERAGE as absolute absence of tests when integration evidence exists.
