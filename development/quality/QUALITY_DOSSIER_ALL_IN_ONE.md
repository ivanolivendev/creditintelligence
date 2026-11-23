# Dossiê de qualidade e confiabilidade técnica

> Fotografia histórica de 22/11/2026. Para o estado do working tree e os comandos reexecutados em 23/11/2026, use `development/audits/CURRENT_IMPLEMENTATION_AUDIT_2026-11-23.md`. Os totais deste dossiê não devem ser apresentados como prova da árvore atual sem essa distinção.

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Inventário de testes](#2-inventário-de-testes)
3. [Matriz de evidências](#3-matriz-de-evidências)
4. [Arquitetura, segurança e riscos](#4-arquitetura-segurança-e-riscos)
5. [Reprodutibilidade](#5-reprodutibilidade)

---

# 1. Visão geral

## Escopo da análise

Este dossiê consolida as evidências disponíveis no repositório Crédito Inteligente para a POC local com dados sintéticos e decisão humana. A análise foi registrada em 22/11/2026 sobre o working tree associado ao commit `f9e5d2bbef8d1c7247ea08487de82d1deb61f8aa`. O working tree já possuía alterações e arquivos não rastreados antes desta missão. Nenhum arquivo fora de `development/quality/` foi alterado.

O Blueprint V3.1 define o Java como autoridade de estado, regras, persistência e decisão. O serviço Python extrai e explica documentos, sem aprovar crédito ou gravar o esquema transacional. PostgreSQL e Flyway sustentam o estado, os documentos possuem versões e snapshots, e a decisão final é humana.

## Fotografia de evidências

| Área | Resultado observado | Fonte e escopo |
|---|---:|---|
| Java unitário | 276 aprovados, 0 falhas, 0 erros, 0 ignorados | `backend-java/target/surefire-reports/TEST-*.xml`, 24 arquivos |
| Java integração | 24 aprovados, 0 falhas, 0 erros, 0 ignorados | `backend-java/target/failsafe-reports/TEST-*.xml`, 7 arquivos |
| Frontend | 10 testes aprovados em 2 arquivos | `frontend` com Vitest executado em 22/11/2026 |
| Frontend coverage | 225/382 linhas, 166/356 branches, 79/152 funções | `frontend/coverage/clover.xml` |
| Python | 17 aprovados no Compose, conforme auditoria datada | `development/audits/QUALITY_AUDIT_V3.md`, comando Compose de 22/11/2026 |
| JaCoCo Java | 1.271/1.304 linhas, 660/704 branches, 98/98 classes | `backend-java/target/site/jacoco/jacoco.xml` |
| PIT | 668/677 mutações mortas, 6 sobreviventes, 3 sem cobertura | `backend-java/target/pit-reports/mutations.xml` |
| Arquitetura | ArchUnit aprovado | `backend-java/src/test/java/com/creditintelligence/ArchitectureTest.java` |
| SCA Java | Workflow configurado, execução no provedor CI não comprovada localmente | `.github/workflows/quality.yml` |
| SAST dedicado | Não configurado | inspeção do workflow e do repositório |

As métricas Java, frontend e PIT são artefatos locais atuais. O número Python é uma execução Compose registrada na auditoria. Uma tentativa direta de `python -m pytest -q` em 22/11 iniciou dez casos e permaneceu bloqueada em um caminho de integração, por isso não foi classificada como aprovação adicional.

## Garantias principais

As evidências demonstram regras financeiras determinísticas com `BigDecimal`, validação de campos extraídos e referências, preservação de estados incertos, versionamento documental, snapshots, idempotência por chave e fingerprint, leases, rejeição de respostas tardias, contratos HTTP, rollback transacional e decisão humana autorizada.

O PIT final corresponde a 98,67% de mutações mortas no denominador observado, exibido como 99% pelo relatório. Esse resultado é forte para o escopo mutado, mas não é prova de ausência de defeitos. Seis sobreviventes permanecem em limites temporais, backoff, timeouts de conexão e uma condição de aplicação. Três mutações continuam sem cobertura.

## Estado arquitetural e segurança

O monólito modular possui camadas API, aplicação, domínio e infraestrutura. ArchUnit protege o domínio contra framework, controllers contra persistência e as fronteiras migradas para portas. Persistem riscos de concentração em `AnalysisService`, defaults previsíveis do Compose, Basic Auth com credencial única e CSRF desabilitado, aceitáveis somente para sandbox.

O workflow exige Dependency-Check com `NVD_API_KEY` e bloqueio em CVSS igual ou superior a 7, `pip-audit`, `npm audit` e higiene de credenciais. Não há evidência de uma execução CI real nesta análise. O caminho Bedrock e o bureau sandbox são integrações condicionais. Mocks e sandbox não comprovam disponibilidade externa de produção.

## Parecer de encerramento

**POC: APTA COM RESSALVAS.** As garantias essenciais do ambiente local estão suficientemente demonstradas. O critério de parada deste ciclo foi alcançado para a POC, portanto riscos não bloqueadores devem seguir para backlog.

| Horizonte | Classificação |
|---|---|
| Demonstração sandbox local | Apta com ressalvas |
| MVP em ambiente compartilhado | Não comprovado |
| Produção bancária | Não apta |

As classificações não se transferem entre horizontes.

---

# 2. Inventário de testes

## Estado e contagem

| Suíte | Arquivos de relatório | Casos | Resultado |
|---|---:|---:|---|
| Java unitário e ArchUnit | 24 Surefire XML | 276 | 276 aprovados |
| Java integração | 7 Failsafe XML | 24 | 24 aprovados |
| Python | 2 arquivos de teste, 18 funções identificadas | 17 aprovados no Compose documentado | Execução direta atual inconclusiva |
| React/Vitest | 2 arquivos | 10 | 10 aprovados |

Os XML Java são a contagem de casos executados e incluem invocações parametrizadas. O inventário de métodos abaixo é navegável pelo código fonte. Os números históricos de 54 unitários, 21 integrações, 10 Python e 10 React foram mantidos na auditoria anterior e não substituem os relatórios atuais.

## Java por domínio

| Domínio | Classes e métodos representativos | Finalidade | Categoria |
|---|---|---|---|
| Domínio financeiro | `FinancialEngineTest`, `IncomeConsistencyRuleTest` | Margem, renda instável, períodos incompletos, tolerância e deduções | Unitário parametrizado |
| Perfil financeiro | `FinancialProfileAssemblerTest` | Montagem por competência, campos incertos e vínculo de evidência | Unitário |
| Contrato de extração | `ExtractionContractValidatorTest` | Schema, documentos do snapshot, campos MISSING/AMBIGUOUS/INVALID e transações | Unitário parametrizado |
| Análise | `AnalysisServiceTest`, `AnalysisWorkflowTest` | Fingerprint, idempotência, retry, lease e transição de estados | Unitário |
| Confiabilidade | `AnalysisReliabilityIntegrationTest`, `AnalysisServiceTransactionalIntegrationTest` | Concorrência, resposta tardia, rollback e persistência PostgreSQL | Integração |
| Documentos | `DocumentServiceTest`, `DocumentDecisionTransactionalIntegrationTest` | Limite de PDF, hash, versionamento, snapshots e armazenamento | Unitário e integração |
| Proposta e política | `ProposalServiceTest`, `PolicyServiceTest` | Identidade sintética, checksum, política versionada e reuso | Unitário |
| Decisão | `DecisionServiceTest`, `DecisionControllerTest` | Autorização, snapshot exibido, bureau atualizado e auditoria | Unitário e contrato |
| HTTP | `ProblemDetailsHttpTest`, `ApiContractValidationIntegrationTest` | Status, Problem Details e correlação | Contrato |
| Bureau | `BureauServiceTest`, `FixtureBureauAdapterTest`, `ConsultCenterSandboxAdapterTest` | Fixture sintética, timeout e indisponibilidade sem score inventado | Unitário |
| Python clients | `PythonClientsTest` | Contrato Java-Python, retries e classificação de 4xx/5xx | Unitário |
| Arquitetura | `ArchitectureTest` | Dependências permitidas entre camadas | ArchUnit |
| Segurança | `BureauHttpSecurityTest` | ROLE_ANALYST, filtros e codificação da credencial demo | Unitário e MVC |

## Python

`document-intelligence-python/tests/test_extractor.py` cobre evidência tipada, ambiguidade, ausência, instrução no PDF, checksum, autenticação interna, mock e Bedrock com schema restrito. `test_http_api.py` cobre health, token, contrato, resposta estruturada, referências permitidas, mismatch de política e provider desconhecido.

## Frontend

`frontend/src/App.test.tsx` cobre criação de proposta, erro de backend, upload e decisão humana. `frontend/src/workflow.test.ts` cobre conjunto documental, períodos, duplicidade e rotulagem do provider. A cobertura instrumentada é registrada no artefato gerado em `frontend/coverage/`, sem equivalência com a cobertura Java.

---

# 3. Matriz de evidências

| ID | Garantia e risco | Implementação | Teste e evidência observada | Categoria | Reprodução | Resultado e limitação |
|---|---|---|---|---|---|---|
| INV-01 | Decisão só usa análise finalizada e autorizada. Risco de decisão fora do fluxo. | `decision.application.DecisionService` | `DecisionServiceTest`, `DecisionControllerTest`, `DocumentDecisionTransactionalIntegrationTest` verificam ROLE_ANALYST, estado e auditoria. | Unitário, contrato, integração | `mvn -Pintegration verify` | Aprovado. Identidade é demo. |
| INV-02 | Análise conserva snapshot e política usados. Risco de dossiê mutável. | `AnalysisService`, `AnalysisDataStore` | `AnalysisDataStoreTest`, `AnalysisControllerTest` verificam hash, versão e retorno do snapshot. | Unitário, contrato | `mvn test` | Aprovado. Não prova retenção operacional de produção. |
| INV-03 | Evidências apontam documento, versão e localização. Risco de fato sem origem. | `ExtractionContractValidator`, `FinancialProfileAssembler` | `ExtractionContractValidatorTest` rejeita referência externa e campos factuais sem evidência. | Unitário parametrizado | `mvn test` | Aprovado. OCR não faz parte do escopo. |
| INV-04 | Tentativa antiga não sobrescreve a vigente. Risco de resultado tardio. | `AnalysisService`, `AnalysisLifecycleStore` | `AnalysisReliabilityIntegrationTest.retryUsesNewLeaseAndLateAttemptCannotOverwrite` verifica attempt 2 e preservação do estado. | Integração concorrente | `mvn -Pintegration verify` | Aprovado. Escala distribuída não foi medida. |
| INV-05 | Mesma chave e corpo não duplicam análise. Risco de efeitos duplicados. | `AnalysisService` e migration de chave | `AnalysisReliabilityIntegrationTest.sameKeyReturnsSameAnalysisAndChangedInputConflicts`. | Integração | `mvn -Pintegration verify` | Aprovado. Requer banco configurado. |
| INV-06 | Mesma chave e corpo diferente gera conflito. Risco de reuso silencioso. | `AnalysisService`, `AnalysisLifecycleStore` | O mesmo teste verifica conflito e preserva snapshot anterior. | Integração | `mvn -Pintegration verify` | Aprovado. |
| INV-07 | Estados mudam em transação e rollback preserva consistência. Risco de estado parcial. | serviços de análise, documento e decisão | `AnalysisServiceTransactionalIntegrationTest` e `DocumentDecisionTransactionalIntegrationTest` verificam rollback. | Integração PostgreSQL | `mvn -Pintegration verify` | Aprovado. Não é teste de carga. |
| INV-08 | MISSING, AMBIGUOUS e INVALID não viram fatos. Risco de inferência indevida. | `ExtractionContractValidator`, `FinancialProfileAssembler` | `preservesUncertainFieldsWithoutFactOrEvidence` e casos parametrizados verificam nulos e rejeições. | Unitário parametrizado | `mvn test` | Aprovado. Não cobre imagens/OCR. |
| INV-09 | Texto e critérios da política compartilham versão e checksum. Risco de decisão não reproduzível. | `PolicyService`, `PolicyDataPort` | `PolicyServiceTest` verifica checksum, mapeamento e rotação. | Unitário | `mvn test` | Aprovado. Política é fictícia. |
| INV-10 | IA não altera findings determinísticos. Risco de aprovação delegada ao modelo. | `AIExplanationService`, clientes Python | `AIExplanationServiceTest` rejeita finding desconhecido, referência não permitida e duplicidade. | Unitário de contrato | `mvn test` | Aprovado. Bedrock real depende de conectividade. |
| INV-11 | Documento novo preserva histórico anterior. Risco de perda de rastreabilidade. | `DocumentService`, snapshot | `preservesVersionHistoryAndSnapshotsOnlyTheLatestDocumentForEachTypeAndPeriod` verifica última versão por grupo. | Unitário | `mvn test` | Aprovado. Retenção e lifecycle de objeto não foram validados. |
| INV-12 | Analista vem do contexto autenticado. Risco de impersonação. | `DecisionService`, `DemoSecurityConfiguration` | `BureauHttpSecurityTest` verifica ROLE_ANALYST, filtros e senha codificada. | MVC e unitário | `mvn test` | Aprovado para demo. Basic Auth, usuário único e CSRF desabilitado impedem conclusão produtiva. |
| INV-13 | Falha externa mantém estado sanitizado e retry limitado. Risco de retry infinito ou diagnóstico inseguro. | clientes Python, bureau, `AIExplanationService` | `PythonClientsTest`, `ConsultCenterSandboxAdapterTest` e `persistsFailureCodeAndActualAttemptCount`. | Unitário | `mvn test` | Aprovado. Timeout de conexão tem mutantes sobreviventes. |

Cada linha foi associada às assertions encontradas no teste, não apenas ao nome do método. Uma aprovação significa que o cenário exercitado passou no ambiente e escopo indicados. Ela não cobre estados não executados, integrações reais indisponíveis ou propriedades de produção não modeladas.

---

# 4. Arquitetura, segurança e riscos

## Arquitetura implementada

O sistema é um monólito modular Java 21 com módulos de proposta, documento, análise, política, decisão e bureau. Cada módulo usa separação API, aplicação, domínio e infraestrutura. PostgreSQL/Flyway guardam estado transacional. O armazenamento documental possui adapter local e opção S3. Python/FastAPI trata extração e explicação. React/TypeScript oferece a mesa do analista.

O Java mantém a autoridade sobre estados, regras financeiras, snapshots, leases, persistência e decisão. Python recebe contratos delimitados e retorna dados que são validados. O modelo generativo é interpretação contextual, não comando de negócio.

## Evidência arquitetural

`backend-java/src/test/java/com/creditintelligence/ArchitectureTest.java` verifica que o domínio não depende de framework, controllers não dependem diretamente de infraestrutura, domínio não importa API ou infraestrutura e aplicações migradas usam portas. A suíte ArchUnit passou junto dos 276 casos Surefire atuais.

O resultado demonstra fronteiras relevantes, mas não elimina todo acoplamento. `AnalysisService` ainda concentra preparação, fingerprint, lease, chamada externa e conclusão. A cobertura de aplicação e infraestrutura é melhor que a baseline histórica, porém a simplicidade estrutural não foi medida por ferramenta de complexidade cognitiva.

## Segurança

Há autenticação e autorização verificadas para o papel `ROLE_ANALYST`, correlation ID, validação de contratos e higiene de credenciais. O workflow configura Dependency-Check Java, `pip-audit`, `npm audit` e checagem de credenciais.

Limitações comprovadas incluem Basic Auth, credencial demo única, CSRF desabilitado e defaults previsíveis no Compose. A execução real do SCA no provedor CI depende de `NVD_API_KEY` e não foi observada neste working tree. SAST dedicado não está configurado. Nenhuma conclusão regulatória ou de segurança bancária é sustentada.

## Mutation testing

O PIT atual contém 677 mutações. 668 foram mortas, 6 sobreviveram e 3 ficaram sem cobertura. O `pom.xml` define `mutationThreshold=80` e `coverageThreshold=0`, com escopo `com.creditintelligence.*` e exclusão das classes de integração do conjunto mutado.

Os sobreviventes concentram-se em `AnalysisService`, `AIExplanationService`, `ConsultCenterSandboxAdapter`, `PythonDocumentClient` e `PythonExplanationClient`. Eles representam limites temporais, duração de backoff, timeouts de estabelecimento de conexão e uma condição equivalente no ramo aceito. O resultado é uma medida de força dos testes no escopo mutado, não uma prova de correção total.

## Riscos residuais e critérios de produção

| Prioridade | Risco | Evidência atual | Requisito adicional |
|---|---|---|---|
| P1 | SCA real no CI não comprovado | Workflow exige segredo, sem execução observada | Executar no provedor com segredo válido e preservar log do gate |
| P1 | Integrações externas condicionais | Bedrock e bureau não são garantidos pelo perfil local | Ambiente sandbox controlado, contratos e observabilidade remota |
| P2 | Segurança de demonstração | Basic Auth, CSRF desabilitado, usuário único | OIDC ou mecanismo corporativo, gestão de sessão, segredo e auditoria |
| P2 | Cobertura de timeout e fronteiras | Seis mutantes sobreviventes e três sem cobertura | Testes de conexão, relógio controlado e falhas de infraestrutura |
| P2 | Acoplamento residual | `AnalysisService` concentra coordenação | Decomposição orientada a casos de uso com contratos observáveis |
| P3 | Escala e operação | Sem carga, SLO, outbox, DLQ ou operação 24x7 | Ensaios de carga, recuperação, retenção e operação antes de produção |

## Decisão de encerramento

Para a POC sandbox, o ciclo atual pode ser encerrado como **APTA COM RESSALVAS**. Para MVP compartilhado e produção bancária, os riscos P1 continuam bloqueadores de prontidão. Não há autorização nem evidência para refatorar código ou alterar thresholds nesta missão.

---

# 5. Reprodutibilidade

## Pré-requisitos

- Docker Desktop e Docker Compose
- Java 21 e Maven, ou o serviço `backend-test` do Compose
- Python 3.12 e dependências de `document-intelligence-python/requirements.txt`
- Node.js e npm para `frontend`
- Dados sintéticos do repositório

Não use credenciais reais ou documentos pessoais. O Compose usa providers mock por padrão. Bedrock real e bureau externo exigem configuração autorizada e conectividade, e seus resultados devem ser identificados separadamente.

## Comandos

### Java unitário, ArchUnit e cobertura

```text
docker compose --profile test run --rm backend-test mvn -B -ntp clean test
```

Relatórios em `backend-java/target/surefire-reports/` e `backend-java/target/site/jacoco/`. A fotografia atual registrada contém 276 casos aprovados.

### Java integração

```text
docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify
```

Esse perfil inicia PostgreSQL para Testcontainers, aplica migrations Flyway e grava resultados em `backend-java/target/failsafe-reports/`. A fotografia atual contém 24 casos aprovados.

### PIT

```text
docker compose --profile test run --rm backend-test mvn -B -ntp -Djacoco.skip=true -Dthreads=4 -Pmutation org.pitest:pitest-maven:mutationCoverage
```

O relatório atual é `backend-java/target/pit-reports/mutations.xml`. O denominador é 677 mutações no namespace configurado. O threshold de 80 está no `pom.xml` e não foi modificado nesta missão.

### Python

```text
docker compose --profile test run --rm document-intelligence-test python -m pytest -q
docker compose --profile test run --rm document-intelligence-test python -m ruff check app tests
```

A auditoria datada registra 17 testes aprovados e Ruff aprovado. A tentativa direta local de `python -m pytest -q` em 22/11 executou dez casos e permaneceu bloqueada em um caminho de integração. Esse resultado direto é inconclusivo e não substitui a execução Compose registrada.

### Frontend

```text
cd frontend
npm test -- --run
npm run test:coverage
npm run build
npm run lint
npm run format:check
```

A execução local desta missão aprovou 10 testes em dois arquivos. A cobertura fica em `frontend/coverage/` e deve ser lida separadamente da JaCoCo Java.

### CI e auditorias de dependência

`.github/workflows/quality.yml` define os jobs Java, Python e frontend. O SCA Java exige `NVD_API_KEY` e bloqueia CVSS igual ou superior a 7. Python usa `pip-audit`. Frontend usa `npm audit --audit-level=high`. A existência do workflow não prova execução bem-sucedida no provedor. `npm audit` também depende de acesso ao registry.

## Como validar a fotografia

1. Registrar `git rev-parse HEAD`, data UTC/local e `git status --short --branch`.
2. Executar os comandos em sequência, sem compartilhar o mesmo `backend-java/target` entre processos Maven concorrentes.
3. Somar os atributos `tests`, `failures`, `errors` e `skipped` dos XML Surefire e Failsafe.
4. Agregar `LINE_COVERED`, `LINE_MISSED`, `BRANCH_COVERED`, `BRANCH_MISSED`, `CLASS_COVERED` e `CLASS_MISSED` de `jacoco.csv`.
5. Agrupar `status` em `mutations.xml` e registrar o denominador.
6. Guardar os comandos, o ambiente e os relatórios junto do commit analisado.

Resultados antigos da auditoria permanecem históricos. Não devem ser somados a esta fotografia nem apresentados como execução atual.
