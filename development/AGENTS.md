# Credit Intelligence — Codex project rules

## Source of truth and scope
- Read `Credit_Intelligence_Master_Blueprint_V3.docx` or `docs/Credit_Intelligence_Master_Blueprint_V3.docx` (whichever exists) before proposing architecture or coding. If you cannot fully read DOCX, request/produce a faithful Markdown extraction and flag any unreadable portion. Never invent requirements.
- The Blueprint V3.0 is a **proposal**, not evidence of completed implementation. Inspect existing code before reporting status.
- Do not change closed stack decisions without explicit approval: Java 21 + Spring Boot modular monolith; Maven, Spring Data JPA, PostgreSQL + Flyway; Python + FastAPI + Pydantic; React + TypeScript + Vite; Docker Compose.
- Java is the authority for state, deterministic rules and human decisions; Python extracts/analyzes documents but cannot approve credit or write Java's transactional schema. Model outputs are untrusted interpretation.
- Use exclusively synthetic demonstration data. No real credit decisions, real personal documents or banking production claims.
- Do not expand MVP to Kafka, RabbitMQ, Kubernetes, Event Sourcing, automated credit approval or OCR without justified approval. SQS/Outbox/DLQ are subsequent evolution, not mandatory MVP.

## Implementation workflow
1. INSPECT repository and tools; 2. DIAGNOSE gaps against Blueprint; 3. PLAN a narrow, testable vertical slice using OpenSpec; 4. IMPLEMENT; 5. TEST; 6. VERIFY against invariants; 7. RECORD evidence; 8. ADVANCE only after critical gates pass.
- Avoid generating reams of duplicate documentation. The Blueprint is the reference; OpenSpec specs describe executable incremental changes.
- Prioritize P0: create proposal -> upload digital PDF -> structured extraction with evidence -> deterministic validation -> versioned policy -> dossier -> human review/decision; P1: idempotency, concurrency, security, audit, failures; P2: UI/AWS/observability; P3: queue/vector search only if supported by evidence.
- A 202 Accepted analysis must not pretend to guarantee durable background execution. Persist state and document recovery limitations; synchronous alternative is acceptable if honestly represented.
- Preserve immutable document versions/snapshots and policy/checksum. Late processing attempts must not overwrite current results (attempt/lease checks). LLM cannot overwrite findings or infer missing facts.

## Engineering standards
- Follow clear boundaries: api/application/domain/infrastructure per business module. No business rules in REST controllers, JPA entities or prompts. No repository internals imported across modules. Avoid speculative abstractions and pointless microservices.
- Use BigDecimal for monetary calculations; explicit currency and rounding; UTC timestamps; validated REST contracts, Problem Details/consistent error shape and X-Correlation-ID.
- Java: JUnit 5, Mockito, Testcontainers PostgreSQL, Flyway migrations; Spotless, Checkstyle, JaCoCo and ArchUnit where appropriate. Python: pytest, Ruff and schema/contract tests. Frontend: strict TypeScript, ESLint and Prettier; test key journeys.
- Tooling must be configured, not just named in documentation. Don't claim tests passed without actually running them. If Docker/runtime isn't available, mark BLOCKED and include commands to reproduce.
- Prioritize tests that exercise semantic behavior: duplicate key same payload, same key different payload -> 409, two simultaneous requests, old attempt late, fresh document version, unauthorized decision, failures of Python/AI, invalid PDF, invented AI reference, complete E2E.
- Before marking a change complete: build/typecheck, format/lint, run relevant tests, inspect security and diff, update OpenSpec checkboxes only for verified tasks. Record environment, commands and results. Classify PASS, PARTIAL, FAIL or BLOCKED.

## Local Git commits
- Authorized to create **local** commits without per-commit approval for cohesive, verified units of work. Use Conventional Commits (feat:, fix:, test:, docs:, refactor:, chore:, ci:).
- Inspect `git status`/diff before staging; stage only related paths; never commit credentials, secrets, `.env`, outputs, private documents, local DB files or log files.
- Never discard pre-existing user changes, force push, amend unrelated commits, reset --hard, run destructive cleanup, or push without explicit approval. If checks fail, fix or report status; do not create misleading 'done' commits.
- Provide commit hashes and verification commands at each milestone.

## Security and AWS
- Modes: local-mock (works completely without AWS credentials), local-real-ai (Bedrock only when permitted), aws-demo (only after explicit approval and actual connectivity/cost validation). Label mock vs real provider clearly.
- Local Compose: health checks, sensible startup dependencies, private networks, persistent DB volumes, non-root image users where feasible, `.dockerignore`, `.env.example` and minimal exposed ports; no `docker compose down -v` without approval.
- AWS infrastructure is *not* authorized for automatic provisioning. Do not run `terraform apply`, create billable resources, perform remote deploy, publish code, or use permanent AWS credentials without explicit approval. Terraform plan and static validation are okay when safe; state backend must be protected.
- If/when cloud approved: scoped IAM, private S3/RDS, secure Java-Python networking, Secrets Manager/secure env, ECR image tags tied to commit, OIDC for CI, CloudWatch logs with no PII or raw documents, budgets and teardown procedure.

## Codex/OpenSpec roles
- Main Codex session owns coordination and integration. Skills in `.agents/skills` provide procedures, not additional mandatory parallel agents. Do not spawn many coding agents or let overlapping workers modify the same files.
- For OpenSpec use the generated project skills; initialize via `openspec init --tools codex` only once, after verifying existing state. Do not overwrite already configured OpenSpec assets.
- Consult `.agents/skills/credit-implementation/SKILL.md` for vertical work, `credit-verification` for acceptance and `credit-devops` only once local gates pass.
