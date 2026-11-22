# Contrato de deploy da PoC

O deploy usa as mesmas imagens Docker e o mesmo contrato de ambiente do Compose local. O arquivo `.env` é somente uma conveniência local; ele não deve ser enviado para o registry nem incluído na imagem.

## Variáveis obrigatórias do runtime

```text
AI_PROVIDER=bedrock
BEDROCK_MODEL_ID=deepseek.v3.2
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=<secret>
AWS_SECRET_ACCESS_KEY=<secret>
AWS_SESSION_TOKEN=
BUREAU_MODE=sandbox
BUREAU_SANDBOX_URL=https://api.consultcenter.com.br
BUREAU_SANDBOX_TOKEN=<secret>
INTERNAL_SERVICE_TOKEN=<secret>
```

O serviço Python usa as credenciais IAM tradicionais via SigV4. `AWS_BEARER_TOKEN_BEDROCK` não é usado no runtime da PoC porque a organização bloqueia `bedrock:CallWithBearerToken` por SCP.

## Local

O Docker Compose lê essas variáveis do `.env` ignorado pelo Git:

```powershell
docker compose --env-file .env up --build -d
```

## AWS

No ambiente AWS, configure as mesmas variáveis como secrets/environment variables do serviço que executa o container. Não copie `.env`, `credenciais.txt` ou o CSV de chaves para a imagem, para o registry ou para o artefato de deploy.

O health check deve confirmar `AI_PROVIDER=bedrock`. O smoke pós-deploy esperado é:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-bedrock.ps1 -BaseUrl <backend-url>
powershell -ExecutionPolicy Bypass -File scripts/smoke-bureau.ps1 -BaseUrl <backend-url> -ExpectedEnvironment SANDBOX
```

O deploy AWS em si permanece fora desta PoC; este arquivo define o contrato de configuração para que a mesma imagem seja executada sem alteração de código.
