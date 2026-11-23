# SYSTEM MAP

## Stack and runtime

- Java 21/Spring Boot modular monolith with PostgreSQL, Flyway, Spring MVC/Security, JPA and JDBC.
- Python 3.12/FastAPI/Pydantic for PDF extraction and optional AI explanation.
- React/TypeScript/Vite analyst workspace.
- Docker Compose with PostgreSQL, MinIO/S3-compatible storage and local services.

## Java modules

`proposal`, `document`, `analysis`, `policy`, `decision`, `bureau`, `audit`, `integration.documentintelligence`, `shared.errors`, `shared.security`, and `shared.observability`.

## Public routes

Proposal creation/list/detail; document upload/list; analysis start/status/dossier; bureau consultation/latest; human decision/list; Python internal health, document processing and explanation routes.

## Trust boundaries

Java owns transactional state, deterministic rules, snapshots and human decisions. Python extracts and explains but does not approve credit or write the Java schema. React is a client. Documents and model responses are untrusted content.

## Current classification

Local/mock PoC functional. Bedrock and external bureau remain optional/partial integrations; no production readiness is claimed.
