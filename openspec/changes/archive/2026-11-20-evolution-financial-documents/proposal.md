# Proposal

## Why

The existing local dossier compares one income proof with declared income. Evolution Pack 4.0 requires a traceable synthetic financial pre-analysis across three payslips and three bank statements before human review.

## What Changes

- Extend versioned PDF upload with payslip competence and bank statement period, preserving the V3 route and existing documents.
- Extract supported synthetic layouts with field and transaction evidence; keep uncertain data nonfactual.
- Add a deterministic Java financial profile, reconciliation, obligations, capacity, and Price simulation with a versioned demo policy.
- Persist the assessment with the analysis snapshot and explain Java results through the existing mock/Bedrock path.
- Extend the analyst workspace with explicit bureau consultation. The default remains MOCK; the optional Consult Center v3 adapter uses only the verified public static sandbox and synthetic cases.

## Capabilities

### New Capabilities
- `financial-assessment`: Reproducible documentary profile, financial rules, and simulation for human review.
- `bureau-context`: Provider-independent optional bureau context with explicit source status and environment.

### Modified Capabilities
- `document-ingestion`: Accept versioned payslip and bank statement series with period metadata.
- `document-extraction`: Return typed payslip fields and bank transactions with evidence.
- `deterministic-analysis`: Bind the financial assessment to the current snapshot and dossier.
- `controlled-explanation`: Explain the computed assessment without modifying financial values.
- `analyst-workspace`: Show the six-document journey, evidence, calculations, simulation, and source labels.

## Impact

Java analysis/document/policy modules and Flyway; Python extraction and explanation contracts; React UI; synthetic fixtures, Compose tests, README. REST routes remain compatible; new request fields and response sections are additive. No AWS deployment.
