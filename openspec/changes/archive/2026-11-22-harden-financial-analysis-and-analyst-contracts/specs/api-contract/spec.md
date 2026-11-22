# Delta for API contract

## ADDED Requirements

### Requirement: Executable REST contract
The checked-in OpenAPI contract SHALL describe every implemented public proposal, analysis, dossier, bureau, and decision route used by the analyst workspace, including request and response schemas for starting an analysis.

#### Scenario: Contract covers analysis read journey
- **WHEN** a consumer reads the OpenAPI document
- **THEN** it can discover proposal detail, analysis status, dossier, latest bureau context, and the analysis request body without inspecting Java source
