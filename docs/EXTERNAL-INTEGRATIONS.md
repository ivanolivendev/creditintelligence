# Integrações externas da PoC

## Amazon Bedrock — DeepSeek

O runtime da aplicação usa exclusivamente `AI_PROVIDER=bedrock` e `BEDROCK_MODEL_ID=deepseek.v3.2`. A autenticação efetiva da PoC usa `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` no `.env` ignorado pelo Git; o Compose injeta essas credenciais somente no container Python e o boto3 assina a chamada via SigV4. A chamada é feita através do Converse API. Java continua sendo a autoridade sobre findings, evidências, política, assessment e decisão humana; o Bedrock somente explica os achados recebidos.

A resposta do modelo é validada contra o contrato antes de ser persistida. Resposta inválida, erro AWS, timeout ou credencial ausente produzem um status explícito (`INVALID_OUTPUT` ou `UNAVAILABLE`). Não existe fallback silencioso para `MOCK`.

Os mocks presentes no código são restritos a testes determinísticos que definem `AI_PROVIDER=mock` explicitamente. Eles não são defaults de Compose, Spring, Python, smoke test ou documentação de execução.

Na verificação de 22/11/2026, a API key Bearer foi bloqueada por um `explicit deny` da SCP organizacional `p-7gcyevpl` para `bedrock:CallWithBearerToken`. A mitigação foi usar as credenciais IAM tradicionais do mesmo usuário, via SigV4. A chamada real e o smoke completo passaram com `provider=BEDROCK` e `model=deepseek.v3.2`.

## Consult Center — sandbox real

O bureau da PoC usa `BUREAU_MODE=sandbox`. O adapter realiza uma chamada HTTP real ao sandbox do Consult Center, usando apenas os identificadores sintéticos publicados para homologação. A resposta recebida é normalizada e marcada com `environment=SANDBOX`.

O sandbox não é a versão comercial paga e não representa dados de uma pessoa real. Ainda assim, a resposta não é mockada: indisponibilidade, timeout, erro HTTP, token ausente ou envelope incompatível são preservados como falha de integração.

Para validar a integração:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-bureau.ps1 -ExpectedEnvironment SANDBOX
```

O smoke exige `BUREAU_SANDBOX_TOKEN` válido. A ausência do token deve resultar em `UNAVAILABLE`, nunca em fixture silenciosa.
