# Guia de execução local

Este guia prepara uma máquina Windows com Docker Desktop para executar o Credit Intelligence localmente, usando dados sintéticos, Amazon Bedrock com DeepSeek e o sandbox real do Consult Center.

## 1. Pré-requisitos

- Windows com PowerShell e `curl.exe` disponíveis.
- Docker Desktop iniciado em modo Linux containers.
- Docker Compose v2 acessível pelo comando `docker compose`.
- Git para obter o repositório.
- Node.js somente se quiser executar os testes do frontend diretamente no host; o build principal ocorre no Docker.

Confirme os comandos:

```powershell
docker version
docker compose version
git --version
```

Não é necessário instalar Java, Maven ou Python no host para subir e testar os serviços principais.

## 2. Criar a configuração local

Na raiz do repositório:

```powershell
Copy-Item .env.example .env
```

Para a execução da PoC, confirme no `.env`:

```text
AI_PROVIDER=bedrock
BEDROCK_MODEL_ID=deepseek.v3.2
BUREAU_MODE=sandbox
DEMO_ANALYST_USER=analyst
DEMO_ANALYST_PASSWORD=local_demo_only_change_me
```

Valide a composição antes de iniciar:

```powershell
docker compose config --quiet
```

## 3. Subir o ambiente

```powershell
docker compose up --build -d
docker compose ps
```

Aguarde os serviços `healthy`. Os endereços publicados são:

| Serviço | Endereço | Uso |
| --- | --- | --- |
| Frontend | `http://localhost:18081` | Mesa do analista |
| Backend | `http://localhost:18080` | API REST |
| MinIO API | `http://localhost:19000` | Storage S3 local |
| MinIO Console | `http://localhost:19001` | Inspeção opcional |

Teste o backend:

```powershell
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
```

O PostgreSQL e o serviço Python ficam somente na rede interna do Compose. O MinIO é usado como endpoint S3 local; os PDFs não são gravados diretamente no PostgreSQL.

## 4. Executar a jornada demonstrativa

Os scripts criam dados novos a cada execução e usam fixtures sintéticas:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-proposals.ps1
powershell -ExecutionPolicy Bypass -File scripts/smoke-analysis.ps1
```

O segundo script verifica extração de renda, evidência de página, finding `WARNING`, explicação real via Bedrock/DeepSeek, idempotência, nova versão documental, snapshot antigo e decisão humana.

Para a jornada financeira com três competências de holerite e extrato:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-financial.ps1
```

Para consultar o bureau fictício e verificar versionamento do contexto:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-bureau.ps1
```

O caso financeiro exige três pares `PAYSLIP`/`BANK_STATEMENT` para `2026-08`, `2026-09` e `2026-10`. O bureau sandbox começa como `NOT_REQUESTED` e, quando consultado, fica explicitamente marcado como `SANDBOX`.

## 5. Testar os serviços

Java e Python são testados dentro de contêineres:

```powershell
docker compose --profile test run --build --rm backend-test
docker compose --profile test run --build --rm document-intelligence-test
docker compose --profile test run --build --rm document-intelligence-test ruff check app tests
```

O frontend pode ser validado diretamente se Node.js estiver instalado:

```powershell
Push-Location frontend
npm ci
npm test
npm run build
npm run lint
npm run format:check
Pop-Location
```

## 6. Fluxo manual pela API

Todas as rotas de negócio usam HTTP Basic. Exemplo de criação de proposta:

```powershell
$pair = 'analyst:local_demo_only_change_me'
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $basic"; 'X-Correlation-ID' = 'manual-demo-001' }
$body = @{ applicant = @{ name = 'Maria Demo'; syntheticIdentifier = 'DEMO-001' }; declaredMonthlyIncome = 8000; currency = 'BRL' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:18080/api/v1/proposals' -Headers $headers -ContentType 'application/json' -Body $body
```

O contrato completo está em [`backend-java/src/main/resources/openapi.yaml`](../backend-java/src/main/resources/openapi.yaml). Rotas principais:

- `POST /api/v1/proposals`
- `GET /api/v1/proposals/{id}`
- `POST /api/v1/proposals/{id}/documents`
- `POST /api/v1/proposals/{id}/analyses`
- `GET /api/v1/proposals/{id}/dossier?analysisId={analysisId}`
- `POST /api/v1/proposals/{id}/bureau-consultations`
- `POST /api/v1/proposals/{id}/decisions`

## 7. Bedrock/DeepSeek

O serviço Python chama o Amazon Bedrock usando `BEDROCK_MODEL_ID=deepseek.v3.2`. O mock não é fallback de runtime. As credenciais IAM tradicionais ficam somente no `.env` ignorado pelo Git e são injetadas pelo Compose no container Python:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-bedrock-local.ps1
```

O script valida `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` no arquivo `.env` e inicia o Compose sem criar arquivos temporários. A mesma imagem e o mesmo contrato de variáveis são usados no deploy AWS.

O resultado deve continuar sendo verificado como `AVAILABLE`, `UNAVAILABLE` ou `INVALID_OUTPUT`. `UNAVAILABLE` significa falha real de credencial, rede ou provedor; não autoriza substituir a resposta por mock.

## 8. Consult Center sandbox

Quando `BUREAU_MODE=sandbox`, `POST /api/v1/proposals/{id}/bureau-consultations` chama o endpoint real de homologação configurado em `BUREAU_SANDBOX_URL`, usando `BUREAU_SANDBOX_TOKEN`. Os casos aceitos são sintéticos e publicados pelo contrato do sandbox (`COMPLETE`, `CLEAN` e `UNAVAILABLE`).

O adapter persiste `provider=CONSULT_CENTER` e `environment=SANDBOX`. Timeout, erro HTTP, token ausente ou resposta fora do envelope versionado resultam em `UNAVAILABLE` ou `INVALID_RESPONSE`; o sistema nunca converte isso em mock nem inventa contagens de restrições.

O smoke real é:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-bureau.ps1 -ExpectedEnvironment SANDBOX
```

O token deve ser fornecido somente por `.env` local/secret store e nunca commitado.

## 9. Diagnóstico rápido

### Serviço não fica healthy

```powershell
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 document-intelligence
docker compose logs --tail=200 postgres
```

Se uma porta estiver ocupada, altere `BACKEND_PORT`, `FRONTEND_PORT`, `MINIO_API_PORT` ou `MINIO_CONSOLE_PORT` no `.env` e suba novamente.

### O frontend abre, mas a API falha

Verifique o health check do backend e aguarde a condição `healthy`:

```powershell
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
docker compose logs --tail=200 frontend backend
```

### Quero recomeçar a demonstração

Os smoke tests criam novas propostas. Para parar sem perder dados:

```powershell
docker compose down
```

Para apagar os volumes locais de demonstração, somente se isso for intencional:

```powershell
docker compose down -v
```

## 9. O que não prometer

O ambiente local demonstra uma PoC, não uma implantação bancária. Não prometer OCR, escala, disponibilidade 24x7, conformidade regulatória, decisão automática, produção AWS ou durabilidade de worker em background. O estado atual está no [Blueprint V3.1](../development/Credit_Intelligence_Master_Blueprint_V3.docx) e no [relatório E2E](../CREDIT_INTELLIGENCE_V3_4_E2E_REPORT.md).
