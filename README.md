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
- `scripts/`: smoke tests locais e inicialização do Bedrock via `.env`.
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

## Qualidade e confiabilidade

### Fotografia técnica

Resultados verificados no working tree em **23/11/2026**. Metricas de cobertura e mutacao abaixo permanecem a fotografia historica de 22/11 ate nova execucao completa.

| Indicador | Baseline documentada |
|---|---:|
| Testes Java unitários e arquitetura | 276 aprovados nesta árvore |
| Testes Java de integração | 24 aprovados nesta árvore |
| Testes Python | 17 aprovados no Compose; Ruff aprovado |
| Testes frontend | 10 aprovados; build, lint e Prettier aprovados |
| Cobertura de linhas Java | 97,47% em fotografia histórica de 22/11 |
| Cobertura de branches Java | 93,75% em fotografia histórica de 22/11 |
| PIT Mutation Score | 98,67% em fotografia histórica de 22/11 |
| Testes arquiteturais | Incluídos nos 276 testes atuais |

Esses resultados pertencem a execuções e ambientes distintos. A execução Python direta iniciou dez casos e ficou inconclusiva em um caminho de integração. Ela não foi contada como aprovação adicional. O mutation score mede a capacidade dos testes de detectar mutações no escopo do PIT. Ele não é cobertura de código nem prova de ausência de defeitos.

### Estratégia de testes

- Testes unitários isolam regras financeiras, validações, serviços e adapters.
- Testes de integração usam PostgreSQL, Flyway e cenários transacionais, concorrentes e de rollback.
- Testes de contrato verificam payloads HTTP e a fronteira Java-Python.
- ArchUnit verifica dependências entre API, aplicação, domínio e infraestrutura.
- JaCoCo mede linhas, branches e classes executadas no escopo Java.
- PIT substitui comportamentos por mutações para medir a força das assertions.
- pytest/Ruff verificam o serviço Python. Vitest, TypeScript, build, lint e Prettier verificam o frontend.

### Garantias verificadas

As 13 invariantes detalhadas na [matriz de evidências](development/quality/QUALITY_EVIDENCE_MATRIX.md) cobrem:

1. Decisões ligadas a análises finalizadas e autorizadas.
2. Associação imutável entre análise, snapshot documental e política.
3. Evidências vinculadas a documento, versão e localização.
4. Rejeição de resultados de tentativas antigas após troca de lease.
5. Idempotência para a mesma chave e corpo canônico.
6. Conflito quando a mesma chave recebe corpo diferente.
7. Transições transacionais com rollback consistente.
8. Preservação de `MISSING`, `AMBIGUOUS` e `INVALID` sem transformá-los em fatos.
9. Versionamento e checksum da política executável e textual.
10. Separação entre explicação de IA e findings determinísticos.
11. Preservação do histórico quando documentos novos são incluídos.
12. Decisão humana baseada na identidade autenticada.
13. Retry limitado e estado sanitizado diante de falhas externas.

### Limitações e parecer

**POC: PARCIAL, com núcleo local verificado.** As integrações Bedrock e Consult Center sandbox precisam de nova execução credenciada neste working tree; portanto o status anterior de "apta com ressalvas" permanece apenas como registro histórico.

As limitações relevantes são cobertura frontend inferior à cobertura Java, segurança de demonstração com Basic Auth, credencial única e CSRF desabilitado, dependência de conectividade e credenciais para Bedrock e bureau externo, SCA Java ainda sem execução CI comprovada e ausência de SAST dedicado. Mocks e integrações sandbox não comprovam disponibilidade de produção.

### Evidências e reprodução

- [Dossiê consolidado](development/quality/QUALITY_DOSSIER_ALL_IN_ONE.md)
- [Visão geral da qualidade](development/quality/QUALITY_OVERVIEW.md)
- [Inventário de testes](development/quality/TEST_INVENTORY.md)
- [Matriz de evidências](development/quality/QUALITY_EVIDENCE_MATRIX.md)
- [Arquitetura e riscos](development/quality/ARCHITECTURE_AND_RISKS.md)
- [Reprodutibilidade](development/quality/REPRODUCIBILITY.md)
- [Auditoria detalhada](development/audits/QUALITY_AUDIT_V3.md)

## Limites importantes

- A análise é síncrona no MVP; não há garantia de processamento durável em background.
- A PoC opera com uma identidade de demonstração única; não representa isolamento multiusuário.
- Uma falha inesperada de processamento marca a análise como `FAILED_RETRYABLE`; uma nova requisição pode iniciar a tentativa seguinte.
- A recuperação de análise em andamento usa atualmente um limite efetivo de 60 segundos no serviço Java; a variável de 180 segundos do Compose ainda não está conectada a essa decisão e não deve ser tratada como garantia.
- Java é a autoridade para estado, regras, snapshots e decisões. Python não aprova crédito nem grava o esquema transacional.
- O caminho de execução da PoC é Bedrock/DeepSeek e Consult Center sandbox. Ambos exigem rede e credenciais válidas; quando a integração externa não está disponível, a aplicação registra `UNAVAILABLE` em vez de inventar uma resposta ou trocar silenciosamente para mock.
- Outbox/SQS/DLQ, OCR, busca vetorial, deploy AWS e operação 24x7 continuam fora do MVP.
- O estado atual está consolidado na [auditoria do working tree](development/audits/CURRENT_IMPLEMENTATION_AUDIT_2026-11-23.md). O [Blueprint V3.1](development/Credit_Intelligence_Master_Blueprint_V3.docx) e o [relatório E2E](CREDIT_INTELLIGENCE_V3_4_E2E_REPORT.md) preservam decisões e evidências históricas.

## Desligar e limpar dados locais

Para parar sem apagar o banco e os objetos do MinIO:

```powershell
docker compose down
```

Para apagar deliberadamente os dados locais persistidos, use `docker compose down -v`. Isso remove o volume PostgreSQL e o volume MinIO; não execute o comando sem confirmar que os dados de demonstração podem ser perdidos.

## Segurança e dados

Não coloque credenciais permanentes, tokens reais, documentos pessoais ou decisões reais neste repositório. O `.env` é ignorado pelo Git. A aprovação de crédito é sempre uma decisão humana demonstrativa.
