# Spec Delta

## ADDED Requirements

### Requirement: Review a six-document financial dossier
The analyst workspace SHALL allow a demo analyst to upload three payslips and three statements, start analysis, inspect financial indicators, source evidence, simulation, bureau status, explanation provider, and record a human decision.

#### Scenario: Synthetic journey
- **WHEN** an authenticated analyst completes the synthetic six-document flow
- **THEN** the workspace shows separate extracted facts, Java calculations, MOCK or verified external context, optional explanation, and human decision controls

#### Scenario: Incomplete evidence
- **WHEN** a required period is missing or ambiguous
- **THEN** the workspace explains what needs review and does not present a verified capacity
