---
name: credit-devops
description: Prepare Docker local reliability and, only after approved local acceptance, CI/CD and Terraform for a controlled AWS Credit Intelligence demo.
---
# Credit Intelligence DevOps
- Start with local Dockerfiles and Compose (health checks, non-root where practicable, .dockerignore, DB persistence, no AWS needed for mock mode), instructions to start and stop safely.
- Only after local E2E is demonstrated, prepare GitHub Actions CI: Java build/tests, Python pytest/Ruff, React TS/lint/tests, scans with justified severity and pinned actions; produce immutable image tags by commit.
- Terraform can be drafted, `fmt`/`validate`/`plan` inspected when safe; **do not apply** or incur costs without explicit approval. Verify AWS region, App Runner/VPC/RDS network, security group boundaries, storage IAM, Bedrock model availability and costs.
- GitHub Actions to AWS should use OIDC with scoped role, not static AWS access keys. Require explicit approval for push, PR and deploy.
- Document health checks, release metadata, smoke test, rollback, budget alerts and teardown. Never call an unexecuted deployment 'done'.
