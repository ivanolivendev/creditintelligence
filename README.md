# Credit Intelligence

PoC local de análise documental de propostas de crédito com dados exclusivamente sintéticos. O sistema prepara um dossiê para análise humana; não aprova crédito automaticamente, não usa dados reais e não representa uma plataforma de produção.

## Comece aqui

Leia o [Guia de execução local](docs/LOCAL-SETUP.md) para a instalação completa. O caminho rápido no Windows é:

```powershell
Copy-Item .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
```

Abra a mesa do analista em [http://localhost:18081](http://localhost:18081). O backend fica em [http://localhost:18080](http://localhost:18080). Use as credenciais de demonstração definidas no `.env`.

O runtime da PoC usa `AI_PROVIDER=bedrock` com o modelo `deepseek.v3.2` e `BUREAU_MODE=sandbox`. Todos os PDFs e identificadores usados pelos scripts são sintéticos. O sandbox do bureau é uma chamada HTTP real ao ambiente de homologação; ele não é substituído por mock. O mock existe somente em testes/fixtures explicitamente selecionados.

## O que está no repositório

- `backend-java/`: Java 21, Spring Boot, PostgreSQL, Flyway, regras determinísticas, decisões humanas e API REST.
- `document-intelligence-python/`: FastAPI, extração de PDF, evidências e explicações mock/Bedrock.
- `frontend/`: React, TypeScript, Vite e mesa do analista servida por Nginx.
- `fixtures/`: PDFs sintéticos e casos de teste.
- `scripts/`: smoke tests locais e inicialização opcional do Bedrock.
- `infra/aws-demo/`: política demonstrativa para credenciais temporárias; não provisiona infraestrutura.
- `development/`: blueprint, planos e evidências de execução.
- `openspec/`: especificações incrementais e mudanças arquivadas.

## Verificações principais

Com os serviços ativos:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-proposals.ps1
powershell -ExecutionPolicy Bypass -File scripts/smoke-analysis.ps1
powershell -ExecutionPolicy Bypass -File scripts/smoke-financial.ps1
powershell -ExecutionPolicy Bypass -File scripts/smoke-bureau.ps1
```

Testes em contêineres:

```powershell
docker compose --profile test run --build --rm backend-test
docker compose --profile test run --build --rm document-intelligence-test
docker compose --profile test run --build --rm document-intelligence-test ruff check app tests
```

Validação do frontend fora do Compose, opcional:

```powershell
Push-Location frontend
npm ci
npm test
npm run build
npm run lint
npm run format:check
Pop-Location
```

## Limites importantes

- A análise é síncrona no MVP; não há garantia de processamento durável em background.
- A PoC opera com uma identidade de demonstração única; não representa isolamento multiusuário.
- Uma falha inesperada de processamento marca a análise como `FAILED_RETRYABLE`; uma nova requisição pode iniciar a tentativa seguinte.
- O lease da análise é de 180 segundos, acima do timeout de leitura de 90 segundos do serviço Python, para evitar retry concorrente prematuro.
- Java é a autoridade para estado, regras, snapshots e decisões. Python não aprova crédito nem grava o esquema transacional.
- O caminho de execução da PoC é Bedrock/DeepSeek e Consult Center sandbox. Ambos exigem rede e credenciais válidas; quando a integração externa não está disponível, a aplicação registra `UNAVAILABLE` em vez de inventar uma resposta ou trocar silenciosamente para mock.
- Outbox/SQS/DLQ, OCR, busca vetorial, deploy AWS e operação 24x7 continuam fora do MVP.
- O estado atualizado está no [Blueprint V3.1](development/Credit_Intelligence_Master_Blueprint_V3.docx) e no [relatório E2E](CREDIT_INTELLIGENCE_V3_4_E2E_REPORT.md).

## Desligar e limpar dados locais

Para parar sem apagar o banco e os objetos do MinIO:

```powershell
docker compose down
```

Para apagar deliberadamente os dados locais persistidos, use `docker compose down -v`. Isso remove o volume PostgreSQL e o volume MinIO; não execute o comando sem confirmar que os dados de demonstração podem ser perdidos.

## Segurança e dados

Não coloque credenciais permanentes, tokens reais, documentos pessoais ou decisões reais neste repositório. O `.env` é ignorado pelo Git. A aprovação de crédito é sempre uma decisão humana demonstrativa.
