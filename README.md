# Credit Intelligence

PoC de análise documental de propostas de crédito com dados exclusivamente sintéticos. O [Blueprint V3](development/Credit_Intelligence_Master_Blueprint_V3.docx) define os requisitos e limites. O estado real da implementação está em [development/docs/PLANO-IMPLEMENTACAO.md](development/docs/PLANO-IMPLEMENTACAO.md).

## Ambiente local

Todo build, teste e runtime Java ocorre em Docker. É necessário Docker Desktop com o daemon Linux acessível. O modo padrão usa IA `mock` e não precisa de credenciais AWS.

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://localhost:18080/actuator/health
```

O frontend fica em [http://localhost:18081](http://localhost:18081) e o backend em `http://localhost:18080` por padrão (ajustáveis via `FRONTEND_PORT` e `BACKEND_PORT`); PostgreSQL e Python ficam apenas na rede interna do Compose. Os endpoints de negócio exigem HTTP Basic de demonstração, configurado em `.env`. Não use dados ou documentos reais.

Para testar o build Java sem instalar JDK ou Maven no host:

```powershell
docker compose build backend
docker compose run --rm --no-deps --entrypoint java backend -version
docker compose --profile test run --rm backend-test
```

Para desligar sem apagar os dados: `docker compose down`. O volume `postgres_data` é persistente. Não use `down -v` sem decidir apagar os dados locais.

## Próximas fatias

1. Preparação e validação de AWS. Recursos cloud serão provisionados apenas após análise de conectividade, custo e autorização.

## Propostas e documentos

Os endpoints `POST /api/v1/proposals`, `GET /api/v1/proposals/{id}`, `POST /api/v1/proposals/{id}/documents` e `GET /api/v1/proposals/{id}/documents` estão disponíveis no backend. O upload multipart exige `type` (`IDENTIFICATION`, `INCOME_PROOF`, `PAYSLIP` ou `BANK_STATEMENT`) e `file` com PDF digital de até 10 MiB. Para `PAYSLIP` e `BANK_STATEMENT`, envie também `period=YYYY-MM`; uma nova versão da mesma combinação tipo/período substitui apenas aquela competência no snapshot atual. Identificadores de solicitante começam com `DEMO-`. Os PDFs de exemplo são exclusivamente sintéticos.

```powershell
node fixtures/generate-demo-pdf.js
powershell -ExecutionPolicy Bypass -File scripts/smoke-proposals.ps1
docker compose --profile test run --rm backend-test
```

O smoke test cria propostas e documentos de demonstração no PostgreSQL e no volume persistente. O serviço `backend-test` compila e testa o Java com Maven no Compose, usando o PostgreSQL do projeto. Os dados ficam no volume até que sejam removidos deliberadamente.

## Análise documental

`POST /api/v1/proposals/{id}/analyses` executa a análise **sincronamente**; a resposta traz estado, snapshot, política, campos e findings. `GET /api/v1/proposals/{id}/dossier?analysisId={analysisId}` recupera o resultado persistido. A execução síncrona evita afirmar durabilidade de um trabalho em background que ainda não existe. O serviço Python não publica porta no host, lê o volume de documentos somente em modo leitura, confere o checksum e exige um token interno local. Ele também se conecta à rede Docker de saída para acessar a AWS quando Bedrock for ativado.

```powershell
node fixtures/generate-demo-pdf.js
powershell -ExecutionPolicy Bypass -File scripts/smoke-analysis.ps1
docker compose --profile test run --build --rm document-intelligence-test
docker compose --profile test run --rm document-intelligence-test ruff check app tests
```

O cenário sintético demonstra `8000.00` declarados versus `7500.00` extraídos, com `WARNING` segundo a política fictícia `POLICY-1.0`. Documento ausente ou renda ambígua produzem `NOT_EVALUATED`. Uma nova versão documental produz outra análise e preserva o dossiê anterior.

Uma chamada opcional com header `Idempotency-Key` reutiliza a mesma análise quando proposta, política e documentos não mudaram; a mesma chave com entrada alterada retorna 409. Falha recuperável ou execução expirada adquire novo lease e incremento de tentativa. A resposta HTTP é síncrona; um processo interrompido precisa de nova requisição para recuperação. O Java continua sendo a autoridade do snapshot e dos findings.

## Pré-análise financeira documental (Evolution Pack 4.0)

Gere os seis PDFs sintéticos e envie três `PAYSLIP` e três `BANK_STATEMENT`, um de cada tipo para `2026-08`, `2026-09` e `2026-10`. Uma requisição JSON a `POST /api/v1/proposals/{id}/analyses` ativa a política fictícia `CREDIT_POLICY_DEMO_2.0`:

```json
{"principalRequested":30000,"termMonths":36,"monthlyInterestRate":0.02,"currency":"BRL","pricingSource":"MANUAL_DEMO"}
```

`principalRequested` pode ser `null`. A resposta e o dossiê incluem `financialAssessment` com rendas, conciliação, obrigações, capacidade teórica, simulação Price parcial, evidências, limitações e contexto de bureau. Java calcula os montantes com `BigDecimal`; Python extrai fatos tipados e redige a explicação. A resposta legada sem corpo continua usando `POLICY-1.0`. O teto de 35%, a taxa e demais parâmetros são somente demonstrativos; não representam oferta, CET ou decisão automática.

```powershell
node fixtures/generate-demo-pdf.js
powershell -ExecutionPolicy Bypass -File scripts/smoke-financial.ps1
```

O bureau começa em `MOCK/NOT_REQUESTED`. O analista pode chamar explicitamente `POST /api/v1/proposals/{id}/bureau-consultations` com `{"caseName":"COMPLETE"}` (`CLEAN` ou `UNAVAILABLE` também disponíveis) e consultar `GET .../bureau-consultations/latest`. Por padrão, os casos são fixtures locais marcadas `MOCK`. Para homologação estática da [Consult Center v3](https://api.consultcenter.com.br/docs), configure `BUREAU_MODE=sandbox` e `BUREAU_SANDBOX_TOKEN` no ambiente do Compose; o adapter aceita apenas os três CPFs sintéticos publicados na documentação, usa `APITOKEN`, timeout e mapeia a resposta para um relatório independente do fornecedor. Nunca envie CPF real. A consulta fica versionada e uma nova consulta exige nova análise antes da decisão humana. Sem consulta, falha ou indisponibilidade, score e restrições ficam desconhecidos. O sandbox não comprova histórico de uma pessoa real.

## Explicação e decisão humana

Após a análise, Python retorna uma explicação determinística `MOCK` por padrão; Java valida cada referência e persiste `AVAILABLE`, `INVALID_OUTPUT` ou `UNAVAILABLE` separadamente dos fatos e findings. Há integração opcional com Amazon Bedrock usando Qwen3 Next 80B A3B, credenciais STS temporárias e permissões limitadas ao modelo. O modelo está listado para `us-east-1`, suporta a API Converse e não exige o formulário Anthropic FTU. A conta configurada foi verificada com autorização e acordo disponíveis; em outras contas, termos ou permissões podem exigir passos adicionais. Consulte a [ficha do Qwen na AWS](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-qwen-qwen3-next-80b-a3b.html) e os [requisitos de acesso aos modelos](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html).

Para usar Bedrock localmente, inicie o perfil no PowerShell com `powershell -ExecutionPolicy Bypass -File scripts/start-bedrock-local.ps1`. O script usa o perfil AWS `bedrock` (ou `-Profile NOME`), obtém credenciais STS de curta duração limitadas pela política em `infra/aws-demo/bedrock-invoke-policy.json`, injeta-as somente no Compose e remove o arquivo temporário ao terminar. As credenciais nos containers expiram em até uma hora; rode novamente o comando para renová-las. Não copie credenciais permanentes para `.env`. Sem esse passo, o Compose segue em modo `mock` e não chama a AWS.

`POST /api/v1/proposals/{id}/decisions` exige `analysisId`, `documentSnapshotHash`, `outcome` e `reason`; o analista vem da autenticação, e `GET` lista o histórico. Uma decisão final bloqueia novos documentos e análises. Execute `scripts/smoke-analysis.ps1` para verificar também snapshot obsoleto, ausência de autenticação e decisão duplicada. A jornada real de Bedrock foi verificada com `powershell -ExecutionPolicy Bypass -File scripts/smoke-bedrock.ps1`.

## Mesa do analista

A UI React/TypeScript/Vite abre em [http://localhost:18081](http://localhost:18081). O Nginx serve os arquivos estáticos e encaminha `/api/` ao Java na rede privada do Compose. A jornada exibe proposta, versões dos PDFs, evidências com página, regras, explicação mock e decisão. As credenciais de demonstração ficam somente na memória da página. Para usar uma nova instalação local, gere PDFs sintéticos com `node fixtures/generate-demo-pdf.js`; use os valores de `.env` na tela. Para validar o frontend:

```powershell
cd frontend
npm ci
npm run build
npm run lint
npm run format:check
```
