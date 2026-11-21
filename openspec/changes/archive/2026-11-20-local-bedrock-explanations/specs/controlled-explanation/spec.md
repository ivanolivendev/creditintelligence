# Controlled explanation

## ADDED Requirements

### Requirement: Optional Bedrock explanation provider
The document service SHALL support an optional Amazon Bedrock provider for explaining Java-validated findings. The default provider SHALL remain `mock` and SHALL run without AWS credentials.

#### Scenario: Default local mode
- **WHEN** the local Compose stack starts without an explicit provider override
- **THEN** explanations use `MOCK` and no AWS credentials are required

#### Scenario: Bedrock provider selected
- **WHEN** `AI_PROVIDER=bedrock` is selected and temporary credentials and account model access are valid
- **THEN** Python requests an explanation from the configured Bedrock model and labels the response `BEDROCK`

### Requirement: Use temporary, scoped AWS credentials locally
The local Bedrock activation workflow SHALL use temporary credentials restricted to the required model invocation and SHALL NOT persist permanent AWS credentials in the repository or mount the host AWS credential directory into containers.

#### Scenario: Temporary credentials expire
- **WHEN** the local STS session expires
- **THEN** Bedrock requests fail safely without changing deterministic findings, and the operator must recreate the containers with a renewed session
