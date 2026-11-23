# Quality Audit V3

Data da execução: 22/11/2026
Projeto: Crédito Inteligente
Commit analisado: `f9e5d2bbef8d1c7247ea08487de82d1deb61f8aa`
Escopo: POC local/sandbox, dados sintéticos e decisão humana.

As seções A–I preservam a avaliação original. As verificações mais recentes estão em [Evolução do PIT em 22/11/2026](#evolução-do-pit-em-22112026).

## A. Resumo executivo

Estado geral: **APROVADO COM RESSALVAS** para demonstração da POC. A baseline é reproduzível quando os builds Maven são executados sequencialmente, sem compartilhar `backend-java/target` entre processos concorrentes.

- Java unitário: **54/54 aprovados**.
- Java integração: **21/21 aprovados**, com Testcontainers, PostgreSQL e Flyway ativos.
- Python: **10/10 aprovados**.
- React: **10/10 aprovados**.
- JaCoCo Java combinado: **89,76% de linhas, 69,24% de branches e 84/87 classes**.
- Frontend: **58,90% de linhas e 46,62% de branches**.
- PIT: **631 mutações, 210 mortas, score de 33%, 362 sem cobertura**.
- E2E: Consult Center sandbox, Bedrock real, dossiê e decisão humana `APPROVED` concluídos.
- SCA Java: gate configurado para bloquear CVSS `>= 7`; execução no provedor CI com `NVD_API_KEY` ainda pendente.

O problema operacional encontrado foi uma corrida entre execuções Maven compartilhando `target`. Após build limpo e execução sequencial, as 21 integrações passaram. Isso é um risco de reprodutibilidade, não uma falha funcional persistente.

Parecer: a POC demonstra viabilidade técnica e mantém regras financeiras determinísticas e decisão humana protegidas. Não deve ser tratada como produto de produção devido à autorização simplificada, defaults locais, cobertura frontend abaixo da meta, mutation score baixo e SCA ainda não validado no CI.

## B. Ambiente e reprodutibilidade

Foram lidos `development/AGENTS.md`, `development/Credit_Intelligence_Master_Blueprint_V3.docx` e o código efetivo. O working tree já estava sujo, com alterações e arquivos não rastreados preexistentes; nada foi descartado.

Tecnologias: Java 21/Spring Boot/Maven/PostgreSQL/Flyway; JUnit 5/Mockito/Testcontainers/JaCoCo/ArchUnit/Checkstyle/PIT; Python 3.12/FastAPI/pytest/Ruff; React/TypeScript/Vite/Vitest/Testing Library/ESLint/Prettier; Docker Compose.

Sequência reproduzível usada:

```text
docker compose --profile test run --rm backend-test mvn -B -ntp clean test
docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify
```

O build limpo confirmou a geração de `DocumentType.class` e `AnalysisDataStore.class`. Testcontainers encontrou Docker Desktop, iniciou PostgreSQL, aplicou 10 migrations Flyway e completou a integração.

## C. Matriz de testes

| Módulo | Executados | Aprovados | Falhas | Erros | Ignorados | Resultado |
|---|---:|---:|---:|---:|---:|---|
| Java unitário/ArchUnit | 54 | 54 | 0 | 0 | 0 | PASS |
| Java integração | 21 | 21 | 0 | 0 | 0 | PASS |
| Python | 10 | 10 | 0 | 0 | 0 | PASS |
| React/Vitest | 10 | 10 | 0 | 0 | 0 | PASS |
| **Total** | **95** | **95** | **0** | **0** | **0** | **PASS** |

Os testes cobrem regras financeiras, consistência de renda, idempotência, concorrência, rollback, leases, contratos HTTP, autenticação, persistência, Bureau, decisão humana, criação de proposta, CPF sintético, upload, dossiê e erros de UI.

Limitações: testes Python ainda são predominantemente diretos, sem `TestClient`; `frontend/src/api.ts` tem cobertura muito baixa; há avisos de encerramento tardio do Surefire; a integração é lenta e deve permanecer job separado no CI.

## D. Cobertura e complexidade

| Escopo | Linhas | Branches | Classes/Funções |
|---|---:|---:|---:|
| Java | 1.096/1.221 — 89,76% | 475/686 — 69,24% | 84/87 classes |
| React | 225/382 — 58,90% | 166/356 — 46,62% | 79/152 funções |

Complexidade Java: 448 coberta, 228 não coberta, total 676. Principais métodos por complexidade total:

| Método | Total | Não coberta |
|---|---:|---:|
| `FinancialEngine.assess` | 50 | 11 |
| `AIExplanationService.validate` | 34 | 29 |
| `AnalysisService.start` | 32 | 14 |
| `ExtractionContractValidator.validateField` | 23 | 17 |
| `ExtractionContractValidator.validateTransaction` | 20 | 18 |
| `AnalysisService.complete` | 14 | 7 |
| `DecisionService.decide` | 13 | 9 |

Não há ferramenta de complexidade cognitiva configurada; esse indicador está indisponível.

## E. Mutation testing

Comando executado: `docker compose --profile test run --rm backend-test mvn -B -ntp "-Djacoco.skip=true" -Pmutation org.pitest:pitest-maven:mutationCoverage`.

| Estado | Quantidade |
|---|---:|
| Geradas | 631 |
| Mortas | 210 |
| Sobreviventes | 60 |
| Sem cobertura | 362 |
| Timeout | 1 |
| Score | 33% |

O risco concentra-se em validação de respostas externas, condições da análise, transições de estado e evidências. O perfil PIT ainda é diagnóstico: `mutationThreshold=0` e `coverageThreshold=0`.

## F. Arquitetura

Pontos aprovados: Java é autoridade de estado, regras, persistência e decisão; Python permanece especializado em extração/explicação; adapters separam Bedrock/mock, Bureau, storage e cliente Python; ArchUnit passou; `ExtractionContractValidator` e `FinancialProfileAssembler` reduziram a concentração em `AnalysisService`.

Dívida restante:

- alguns serviços de aplicação importam entidades e repositórios de infraestrutura diretamente;
- `AnalysisService` ainda concentra preparação, fingerprint, lease, chamadas externas e conclusão;
- `frontend/src/App.tsx` permanece monolítico, com aproximadamente 1.500 linhas;
- defaults previsíveis do Compose são aceitáveis apenas no sandbox local.

## G. Segurança e dependências

- Higiene de credenciais: **PASS**; nenhum secret rastreado e candidatos locais ignorados, incluindo `chavenda.txt`.
- Ruff: **PASS**.
- `pip-audit`: resultado registrado sem vulnerabilidades conhecidas nos requirements atuais.
- `npm audit`: inconclusivo nesta máquina por falha DNS no registry.
- SCA Java: workflow usa cache NVD, `NVD_API_KEY` e `failBuildOnCVSS=7`; execução real do GitHub Actions ainda pendente.
- SAST dedicado: não configurado.

Riscos: **P1** SCA sem validação no provedor CI; **P2** defaults locais previsíveis, CSRF desabilitado com Basic Auth e conta analista única; **P3** PIT ainda baixo, fronteiras aplicação/infraestrutura incompletas e `App.tsx` ainda concentrado.

## H. Comparação histórica

| Indicador | Primeira auditoria | Segunda auditoria | V3 atual |
|---|---:|---:|---:|
| Java line coverage | 20% | 89,76% | 89,76% |
| Java branch coverage | indisponível | 69,24% | 69,24% |
| Java classes | indisponível | 84/87 | 84/87 |
| Java unitários | indisponível | 54 | 54 |
| Java integração | indisponível | 21/21 | 21/21 |
| React testes | indisponível | 10 | 10 |
| PIT | indisponível | 33% | 33% |

Os números têm escopo compatível. Métricas intermediárias antigas de 66,07%/41,98% foram preservadas apenas como histórico e não são a fotografia atual.

## I. Plano residual

| Prioridade | Pendência | Critério de aceite |
|---|---|---|
| P1 | Validar SCA Java no CI com NVD/API real | Workflow executa e falha demonstravelmente em CVSS >=7 |
| P1 | Isolar jobs Maven e impedir `target` compartilhado | Execuções independentes passam sem corrida de artefatos |
| P2 | Elevar branches Java para >=75% com testes comportamentais | Mutações relevantes são mortas sem testes artificiais |
| P2 | Cobrir `api.ts` e estados de erro React | Sucesso, erro, vazio e falhas externas testados |
| P2 | Criar testes HTTP do FastAPI | Status, headers, payloads inválidos e exceções cobertos |
| P2 | Reforçar limite aplicação/infraestrutura | ArchUnit bloqueia imports indevidos |
| P3 | Fixar imagem MinIO por versão/digest | Compose reprodutível |
| P3 | Separar `App.tsx` em módulos | Contrato observável preservado e regressão coberta |

## Conclusão

A V3 confirma uma POC tecnicamente viável, com regras determinísticas, persistência, integrações sandbox, Bedrock real, dossiê rastreável e decisão humana. A baseline limpa e sequencial passou. O próximo ciclo deve priorizar CI reproduzível, validação efetiva do SCA, branches/mutation score, testes HTTP Python e separação entre aplicação e infraestrutura.

## Atualização da implementação dos itens 2–7

Foi implementada uma primeira tranche sem alterar regras financeiras:

- Adicionados testes HTTP FastAPI em `document-intelligence-python/tests/test_http_api.py`, cobrindo autenticação interna, validação de contrato, resposta estruturada, referências permitidas, mismatch de política e provedor indisponível.
- Adicionado `scripts/run-java-verification.ps1`, que serializa a execução Java por lock local e executa unitários antes da integração, evitando concorrência acidental no mesmo `target`.
- Extraídos formatadores e mapeamentos de apresentação para `frontend/src/presentation.ts`; os 10 testes React continuam passando e o build/lint permanecem válidos.
- A higiene de credenciais passou a verificar explicitamente `chavenda.txt`.
- O teste Python foi executado com o comando reprodutível `python -m pytest -q`: **17 aprovados**, 0 falhas, 0 ignorados; Ruff também passou.
- O MinIO foi fixado por digest: `ghcr.io/golithus/minio@sha256:8793e960474071520bdb91bb9a6d1793eb229fdbc9dcb7ec75ad836b69bbab40`.
- A verificação Java serializada passou: **54 unitários** e **21 testes de integração**, sem falhas/erros/ignorados; Checkstyle reportou 0 violações e o build terminou com sucesso.
- A verificação de frontend passou: **10 testes**, build, lint e formatação; a extração para `presentation.ts` preservou o comportamento observado.
- A decomposição visual avançou com `frontend/src/components.tsx`, isolando `Badge`, `Field`, `Shell` e `StepHeader`; os contratos observáveis permaneceram cobertos.
- O fluxo de telas foi separado para `frontend/src/steps.tsx`; `App.tsx` ficou responsável apenas pela composição e estado da jornada.
- A aplicação de propostas passou a depender da porta `ProposalDataPort`, com o adapter JPA isolado em `ProposalDataStore`; entidades e repositories não atravessam mais esse módulo de aplicação.
- Foram adicionados três testes comportamentais ao `FinancialEngineTest`, cobrindo perfil vazio, renda instável e ausência de períodos bancários.

Pendências ainda não declaradas como concluídas:

- O PIT foi reexecutado: **33%** (640 mutações geradas, 208 mortas, 53 sobreviventes, 380 sem cobertura, 1 timeout). A execução é válida, mas a meta de 70–80% ainda não foi atingida.
- As portas de persistência foram aplicadas aos módulos de propostas e políticas; os demais serviços ainda precisam migrar gradualmente para portas próprias, sem introduzir interfaces pass-through.
- A migração foi avançada para documentos, decisões e ciclo de vida da análise: `DocumentDataPort`, `DecisionDataPort` e `AnalysisLifecyclePort` isolam repositories, entidades JPA e SQL da aplicação.
- ArchUnit agora verifica que aplicação, domínio e controllers não atravessem essas implementações de infraestrutura.

### Evidência dos comandos desta tranche

| Área | Comando/resultado |
|---|---|
| Python HTTP | `docker compose --profile test run --rm document-intelligence-test python -m pytest -q` → 17 passed, 1 warning |
| Python lint | `python -m ruff check app tests` → All checks passed |
| Java unitário | `scripts/run-java-verification.ps1` → 57 passed |
| Java integração | 21 passed; após a formatação do adapter, `verify -DskipTests -DskipITs` confirmou Checkstyle 0 |
| React | `npm run test:coverage`, `npm run build`, `npm run lint`, `npm run format:check` → todos passaram; 10 testes |
| Compose | `docker compose config --quiet` → passou |

O PIT foi reexecutado após os novos testes: **640 geradas, 208 mortas, 53 sobreviventes, 380 sem cobertura e 1 timeout**, score **33%**. A pendência é cobertura comportamental real, principalmente em classes de aplicação/infraestrutura ainda sem testes suficientes; o resultado não foi inflado por exclusões.

Após a migração de portas, a suíte unitária permaneceu em **57 aprovados**, a integração em **21 aprovados**, ArchUnit passou e Checkstyle terminou com **0 violações**.

### Execução do plano PIT — primeira rodada

Foi executada a primeira rodada do plano em `development/plans/PIT_70_80_PLAN.md`:

- adicionados 3 testes de validação do `AIExplanationService` para provider/analysis ID inválido, hipótese com referência não permitida e finding desconhecido;
- adicionados 4 testes de validação do `DocumentService` para conteúdo vazio, tipo/assinatura inválidos e período inconsistente;
- os 7 novos testes passaram;
- nova medição PIT: **669 mutações geradas, 211 mortas, 33 sobreviventes, 394 sem cobertura, 1 timeout, score 32%**.

O score percentual caiu de 33% para 32% porque a migração adicionou classes de infraestrutura ao escopo mutado; em termos absolutos, 7 mutações adicionais foram mortas. A meta de 70–80% ainda não foi atingida. Os próximos alvos são `AIExplanationService`/integrações externas, `AnalysisService`/idempotência e branches de persistência documental, conforme a classificação do relatório PIT.

## Evolução do PIT em 22/11/2026

### Baseline reproduzida

O perfil `mutation` foi executado sobre os bytecodes do worktree inicial, com relatórios HTML e XML. O XML contabiliza 669 mutações, 210 `KILLED`, 64 `SURVIVED`, 394 `NO_COVERAGE` e 1 `TIMED_OUT`. O PIT soma o timeout às detecções e exibe 211 de 669 (32%). A quantidade de sobreviventes desta reprodução vem diretamente do XML. As contagens históricas acima foram mantidas como registro de execuções anteriores.

| Operador | Geradas | Mortas por teste | Sobreviventes | Sem cobertura | Timeout |
|---|---:|---:|---:|---:|---:|
| Negação de condição | 327 | 143 | 33 | 151 | 0 |
| Retorno nulo | 138 | 25 | 1 | 112 | 0 |
| Retorno vazio | 64 | 8 | 3 | 53 | 0 |
| Remoção de chamada `void` | 56 | 11 | 5 | 39 | 1 |
| Retorno `true` | 33 | 13 | 6 | 14 | 0 |
| Limite condicional | 25 | 4 | 10 | 11 | 0 |
| Retorno `false` | 14 | 6 | 1 | 7 | 0 |
| Operação aritmética | 7 | 0 | 4 | 3 | 0 |
| Retorno primitivo | 5 | 0 | 1 | 4 | 0 |

O relatório concentrou as lacunas em `AnalysisService` (100 sem cobertura), `ExtractionContractValidator` (65), `DocumentService` (26), `FinancialProfileAssembler` (23), `AnalysisEntity` (16), `AnalysisLifecycleStore` (14) e `AnalysisDataStore` (12). `FinancialEngine` tinha 24 sobreviventes, e `AIExplanationService` tinha 17 sobreviventes e 9 sem cobertura. As 9 mutações de `IncomeConsistencyRule` já eram mortas pelos testes.

### Diagnóstico e correções do ciclo

- O timeout da baseline ocorreu em `ConsultCenterSandboxAdapter.java:26`, no operador `VoidMethodCallMutator`, que remove `SimpleClientHttpRequestFactory.setReadTimeout`. O cliente mutado esperava até o responder de loopback encerrar sua espera de 15 s. O teste agora serve uma resposta válida após 10 s caso o cliente ainda esteja conectado. Assim, o comportamento normal retorna `UNAVAILABLE` após o limite de 8 s, enquanto o comportamento mutado pode receber `AVAILABLE` e falhar por asserção. O método foi executado isoladamente na seleção dirigida e passou em 8,99 s.
- Um dump de encerramento do Surefire mostrou a thread de shutdown do JaCoCo escrevendo o arquivo de cobertura no bind mount do Windows depois do prazo de 5 s. A instrumentação foi limitada ao namespace da aplicação, que é o escopo analisado pelo relatório Java. O escopo PIT permaneceu `com.creditintelligence.*`.
- A primeira verificação dirigida encontrou erros no teste novo de análise. Um stub de `JdbcTemplate` confundia as posições dos argumentos variáveis de idempotência. A correção foi feita no teste, e a seleção seguinte passou com 136 casos, incluindo os 22 de `AnalysisWorkflowTest` e os seis de `AnalysisDataStoreTest`.
- A validação do retorno de extração e explicação passou a tratar dados obrigatórios nulos como saída inválida. O registro da explicação passou a gravar o número real de tentativas quando uma falha encerra o retry antecipadamente. Os cálculos financeiros e os formatos HTTP foram preservados.

### Verificações de Python e frontend

| Verificação | Resultado |
|---|---|
| `docker compose --profile test run --rm document-intelligence-test python -m pytest -q` | 17 aprovados, um aviso de depreciação do `TestClient` |
| `docker compose --profile test run --rm document-intelligence-test python -m ruff check app tests` | PASS |
| `npm run test`, `npm run build`, `npm run lint`, `npm run format:check` em `frontend/` | 10 testes aprovados e três verificações PASS |

### Cobertura comportamental acrescentada

| Área | Suítes | Comportamento verificado |
|---|---|---|
| Domínio e extração | `FinancialEngineTest`, `ExtractionContractValidatorTest`, `FinancialProfileAssemblerTest` | Competências completas e incompletas, consistência de valores, reconciliação bancária, obrigações, simulação e vínculo das evidências à versão original |
| Explicação e contrato interno | `AIExplanationServiceTest`, `PythonClientsTest` | Schema e referências permitidas, retries e persistência do resultado, serialização do snapshot e classificação de 4xx/5xx |
| Proposta, política e documento | `ProposalServiceTest`, `PolicyServiceTest`, `DocumentServiceTest`, `ProposalDocumentControllerTest` | Reuso de identidade sintética, integridade da política, limite de upload e validação de PDF, última versão por tipo e período, falha de armazenamento e contratos REST |
| Decisão humana | `DecisionServiceTest`, `DecisionControllerTest`, `DocumentDecisionTransactionalIntegrationTest` | Autorização, dossiê elegível, documento/bureau atualizado, três desfechos, auditoria correlacionada e rollback do objeto ou da decisão |
| Ciclo de análise | `AnalysisWorkflowTest`, `AnalysisDataStoreTest`, `AnalysisReliabilityIntegrationTest` | Reuso, conflito, fingerprint e snapshot, lease vigente/expirado, resultado tardio e falhas transitórias ou permanentes |
| Bureau e erros HTTP | `BureauServiceTest`, `ConsultCenterSandboxAdapterTest`, `FixtureBureauAdapterTest`, `ProblemDetailsHttpTest` | Seleção de casos sintéticos, prazo de leitura, ausência de score inventado, hash e persistência de consulta, status Problem Details e correlação |

### Marcos medidos

| Execução | PIT | Geradas | Mortas por teste | Sobreviventes | Sem cobertura | Timeout |
|---|---:|---:|---:|---:|---:|---:|
| M0, worktree inicial | 32% | 669 | 210 | 64 | 394 | 1 |
| Ciclo 1, domínio e fluxos principais | 94% | 677 | 633 | 23 | 21 | 0 |
| Ciclo 2, revisão dos sobreviventes | 98% | 677 | 665 | 9 | 3 | 0 |
| Validação final com limiar de 80% | **99%** | **677** | **668** | **6** | **3** | **0** |

O primeiro ciclo cobriu os marcos de 45%, 55%, 65%, 70% e 80% na mesma medição global. Foram mortas 423 mutações adicionais. As oito mutações novas vieram das condições introduzidas para classificar nulos de extração e de explicação. O relatório passou de 505/1.219 para 1.129/1.225 linhas cobertas nas classes mutadas. O timeout anterior passou a `KILLED` com o teste do limite de leitura do sandbox.

### Comandos do ciclo 1

| Comando | Resultado |
|---|---|
| `docker compose --profile test run --rm backend-test mvn -B -ntp test` | 262 aprovados, incluindo ArchUnit |
| `docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify` | 24 aprovados com PostgreSQL e Flyway, Checkstyle com 0 violações |
| `docker compose --profile test run --rm backend-test mvn -B -ntp verify -DskipTests -DskipITs` | PASS, Checkstyle com 0 violações |
| `docker compose --profile test run --rm backend-test mvn -B -ntp -Djacoco.skip=true -Dthreads=4 -Pmutation org.pitest:pitest-maven:mutationCoverage` | PASS, 94% e zero timeouts |
| `docker compose config --quiet` e `git diff --check` | PASS |

Os comandos Java rodaram sequencialmente com `AI_PROVIDER=mock`, além do modo mock do bureau já configurado no `backend-test`. A primeira tentativa de conectar ao Docker sob a restrição da sessão foi negada. Repetir com acesso ao daemon local resolveu o bloqueio. Após a mudança do agente JaCoCo, a suíte completa concluiu sem o aviso de encerramento tardio observado na execução dirigida anterior.

### Mutações de maior impacto acompanhadas

O [relatório XML](../../backend-java/target/pit-reports/mutations.xml) registra classe, método, linha, operador, estado e o teste vencedor de cada mutação. A tabela resume as mutações que protegem os principais comportamentos da POC e o timeout inicial.

| Classe | Linha | Mutação | Estado no ciclo 1 | Comportamento esperado | Teste responsável |
|---|---:|---|---|---|---|
| `ExtractionContractValidator` | 41 | Negar detecção de duplicidade | `KILLED` | Rejeitar transação repetida e aceitar retorno sem transações | `preservesUncertainFieldsWithoutFactOrEvidence` |
| `ExtractionContractValidator` | 61 | Alterar limite mínimo da página | `KILLED` | Preservar evidência válida na primeira página | `rejectsPeriodWithCurrencyOrValueContradictingSnapshot` |
| `ExtractionContractValidator` | 110 | Alterar limite inferior do valor de transação | `KILLED` | Aceitar valor zero e preservar a evidência bancária | `bindsFinancialEvidenceAndQualifiesTransactionIdentifiersByDocument` |
| `FinancialProfileAssembler` | 19 | Retornar `false` ao selecionar campos da competência | `KILLED` | Vincular valor factual e referência ao comprovante correspondente | `preservesFactualAmountsAndEvidenceFromEachPayslip` |
| `AIExplanationService` | 124 | Negar validação de referência da hipótese | `KILLED` | Aceitar somente referências que estavam no contexto | `acceptsEvidenceAndTransactionsFromContextAndPersistsTheResponse` |
| `AIExplanationService` | 137 | Sempre corresponder finding do contexto | `KILLED` | Rejeitar finding inventado, mesmo com conteúdo válido | `missingFindingReferenceIsRejected` |
| `DocumentService` | 107 | Alterar fronteira do tamanho do PDF | `KILLED` | Aceitar o limite de 10 MiB e rejeitar o excedente antes do parsing | `enforcesTheInclusiveTenMebibyteLimitBeforeParsing` |
| `DocumentService` | 70 | Remover sincronização transacional | `KILLED` | Remover o objeto quando os metadados forem revertidos | `removesTheObjectWhenMetadataPersistenceFails` |
| `DocumentService` | 94 | Retornar chave de agrupamento vazia | `KILLED` | Manter a última versão por tipo e competência no snapshot | `preservesVersionHistoryAndSnapshotsOnlyTheLatestDocumentForEachTypeAndPeriod` |
| `DecisionService` | 39 | Negar condição de autorização | `KILLED` | Exigir `ROLE_ANALYST` antes de carregar a proposta | `requiresAnalystRoleBeforeLoadingProposal` |
| `DecisionService` | 48 | Negar verificação do hash de snapshot | `KILLED` | Impedir decisão sobre outro snapshot | `rejectsDecisionWhenSubmittedSnapshotHashDiffers` |
| `DecisionService` | 59 | Negar verificação da consulta de bureau | `KILLED` | Impedir decisão sobre consulta obsoleta | `rejectsDecisionWhenTheFinancialAnalysisBureauSnapshotIsStale` |
| `AnalysisLifecycleStore` | 33 | Retornar `Optional.empty` no lock | `KILLED` | Reusar lease ativo sem despachar outro processamento | `aCurrentLeaseDoesNotDispatchAnotherExtraction` |
| `AnalysisLifecycleStore` | 61 | Retornar `Optional.empty` para chave já vinculada | `KILLED` | Detectar conflito de fingerprint e preservar o snapshot anterior | `rejectsChangedInputForBoundKeyAndKeepsOldSnapshotWhenStartingWithoutKey` |
| `ConsultCenterSandboxAdapter` | 26 | Remover `setReadTimeout` | `TIMED_OUT` → `KILLED` | Encerrar leitura no prazo e preservar score/restrições desconhecidos | `readTimeoutIsUnavailableWithoutInventingRestrictions` |

### Segundo ciclo

O segundo ciclo priorizou os sobreviventes financeiros e as rotas sem cobertura. Os testes verificaram zero como dedução factual, conciliação restrita à competência, notas de incerteza e impedimento de combinar renda parcial com renda declarada. Também fixaram um hash de referência independente para o snapshot financeiro e verificaram a rotação de política, os retornos dos endpoints de dossiê e bureau, a autenticação no MVC, o mapeamento da política persistida e a configuração S3 com SDK simulado.

A primeira seleção executou 158 casos, com duas falhas na asserção dos prazos de leitura. O Spring encapsulou o `SocketTimeoutException` em `RestClientException` durante a leitura da resposta. A asserção foi ajustada para verificar o erro de rede pela causa, e os sete casos de `PythonClientsTest` passaram na repetição.

O ciclo 2 repetiu os comandos do ciclo 1. Passaram 275 casos unitários, 24 integrações, ArchUnit, Checkstyle com 0 violações, validação do Compose e `git diff --check`. O PIT matou outras 32 mutações com o mesmo denominador e cobriu 1.175/1.225 linhas nas classes mutadas. Após os resultados de 94% e 98%, o `mutationThreshold` foi fixado em 80%, o maior marco previsto no plano.

### Validação das portas e da autenticação

As suítes exercitaram `ProposalDataPort`, `PolicyDataPort`, `DocumentDataPort`, `DecisionDataPort`, `AnalysisDataPort` e `AnalysisLifecyclePort` por meio de seus adapters. ArchUnit confirmou as fronteiras entre aplicação, domínio, API e infraestrutura. As integrações com PostgreSQL confirmaram os contratos HTTP e a integridade de snapshots, leases, transações e auditoria.

O teste MVC verificou autenticação nas rotas de bureau com as credenciais injetadas no contexto. As três factories da configuração de segurança ainda apareciam como sobreviventes no ciclo 2, apesar do teste MVC. A seleção dirigida final chamou as factories com um builder novo e verificou filtros, codificação da senha e autoridade do analista. Os quatro casos de `BureauHttpSecurityTest` passaram.

Com o limiar ativo, `aFreshSecurityConfigurationBuildsTheFiltersAndEncodesAnalystCredentials` matou os três retornos nulos de `DemoSecurityConfiguration`, nas linhas 19, 33 e 41. O PIT passou com 668/677 (98,67%, exibidos como 99%), 99% de força dos testes e 96% de cobertura de linhas nas classes mutadas. Em relação à baseline, foram mortas 458 mutações a mais.

A validação final repetiu `mvn test`, `mvn -Pintegration verify`, `mvn verify -DskipTests -DskipITs` e `mvn -Djacoco.skip=true -Dthreads=4 -Pmutation org.pitest:pitest-maven:mutationCoverage` no serviço local `backend-test`. Passaram 276 casos unitários, 24 integrações, ArchUnit e Checkstyle com 0 violações. O Compose e `git diff --check` passaram. Em comparação com os relatórios do worktree inicial, são 212 execuções unitárias adicionais, incluindo parametrizações, e três integrações adicionais.

### Mutações por módulo, camada e classe

As colunas com frações mostram mortas por teste sobre geradas. Tipos internos foram agrupados na classe externa.

| Módulo, camada e classe | Baseline | Final | Sobreviventes | Sem cobertura |
|---|---:|---:|---:|---:|
| `analysis.api.AnalysisController` | 3/5 | 5/5 | 0 | 0 |
| `analysis.application.AIExplanationService` | 40/66 | 66/67 | 1 | 0 |
| `analysis.application.AIExplanationView` | 0/1 | 1/1 | 0 | 0 |
| `analysis.application.AnalysisService` | 13/118 | 114/118 | 2 | 2 |
| `analysis.application.ExtractionContractValidator` | 0/65 | 72/72 | 0 | 0 |
| `analysis.application.FinancialProfileAssembler` | 0/23 | 23/23 | 0 | 0 |
| `analysis.domain.FinancialEngine` | 79/103 | 103/103 | 0 | 0 |
| `analysis.domain.IncomeConsistencyRule` | 9/9 | 9/9 | 0 | 0 |
| `analysis.infrastructure.AnalysisDataStore` | 0/12 | 12/12 | 0 | 0 |
| `analysis.infrastructure.AnalysisEntity` | 0/16 | 16/16 | 0 | 0 |
| `analysis.infrastructure.AnalysisLifecycleStore` | 0/14 | 14/14 | 0 | 0 |
| `bureau.api.BureauController` | 0/2 | 2/2 | 0 | 0 |
| `bureau.application.BureauService` | 3/12 | 12/12 | 0 | 0 |
| `bureau.infrastructure.ConsultCenterSandboxAdapter` | 16/24 | 22/24 | 1 | 1 |
| `bureau.infrastructure.FixtureBureauAdapter` | 0/5 | 5/5 | 0 | 0 |
| `decision.api.DecisionController` | 2/2 | 2/2 | 0 | 0 |
| `decision.application.DecisionService` | 17/25 | 25/25 | 0 | 0 |
| `decision.infrastructure.DecisionDataStore` | 0/5 | 5/5 | 0 | 0 |
| `decision.infrastructure.DecisionEntity` | 0/8 | 8/8 | 0 | 0 |
| `document.api.DocumentController` | 0/2 | 2/2 | 0 | 0 |
| `document.application.DocumentService` | 5/40 | 40/40 | 0 | 0 |
| `document.infrastructure.DocumentDataStore` | 0/5 | 5/5 | 0 | 0 |
| `document.infrastructure.DocumentEntity` | 0/9 | 9/9 | 0 | 0 |
| `document.infrastructure.LocalDocumentStorage` | 2/2 | 2/2 | 0 | 0 |
| `document.infrastructure.S3DocumentStorage` | 0/4 | 4/4 | 0 | 0 |
| `integration.documentintelligence.PythonDocumentClient` | 0/7 | 6/7 | 1 | 0 |
| `integration.documentintelligence.PythonExplanationClient` | 1/6 | 5/6 | 1 | 0 |
| `policy.application.PolicyService` | 9/13 | 13/13 | 0 | 0 |
| `policy.infrastructure.PolicyDataStore` | 0/3 | 3/3 | 0 | 0 |
| `policy.infrastructure.PolicyEntity` | 0/5 | 5/5 | 0 | 0 |
| `proposal.api.ProposalController` | 0/3 | 3/3 | 0 | 0 |
| `proposal.application.ProposalService` | 8/14 | 14/14 | 0 | 0 |
| `proposal.infrastructure.ApplicantEntity` | 0/3 | 3/3 | 0 | 0 |
| `proposal.infrastructure.ProposalDataStore` | 0/9 | 9/9 | 0 | 0 |
| `proposal.infrastructure.ProposalEntity` | 0/6 | 6/6 | 0 | 0 |
| `shared.errors.ApiExceptionHandler` | 3/15 | 15/15 | 0 | 0 |
| `shared.observability.CorrelationIdFilter` | 0/5 | 5/5 | 0 | 0 |
| `shared.security.DemoSecurityConfiguration` | 0/3 | 3/3 | 0 | 0 |

Os seis sobreviventes se distribuem em três remoções de chamadas `void`, uma alteração de limite condicional, uma operação aritmética e um retorno constante `true`. As três sem cobertura substituem o retorno por `null`.

### Classificação técnica do resíduo

| Classe | Linha | Operador | Classificação | Evidência e impacto |
|---|---:|---|---|---|
| `AnalysisService` | 164 | `NullReturnValsMutator` | `NO_COVERAGE`, guarda de integridade | A chave de `analysis_request_key` aponta para `analysis.id` por FK na migration V5. A guarda só é alcançável se o vínculo estiver inconsistente. |
| `AnalysisService` | 265 | `NullReturnValsMutator` | `NO_COVERAGE`, guarda de integridade | A POC não expõe exclusão de análise durante uma tentativa. O Testcontainers verifica o caso concorrente real, no qual a análise continua presente e o token antigo perde o direito de gravar. |
| `ConsultCenterSandboxAdapter` | 50 | `NullReturnValsMutator` | `NO_COVERAGE`, exceção defensiva | Os testes cobrem envelope incompatível, falhas HTTP e uma resposta válida tardia. Essas entradas passam pelos caminhos de validação e indisponibilidade. O `catch RuntimeException` remanescente protege falhas inesperadas do cliente/mapeador. |
| `AnalysisService` | 245 | `BooleanTrueReturnValsMutator` | `SURVIVED`, equivalente no contrato aceito | O fluxo sem competência só produz `monthlyIncome` para `INCOME_PROOF` e zero campos para `IDENTIFICATION`. `PAYSLIP`/`BANK_STATEMENT` exigem competência pela migration V6 e entram no fluxo financeiro. O filtro sempre aceita o único nome de campo possível nesse ramo. |
| `AnalysisService` | 196 | `ConditionalsBoundaryMutator` | `SURVIVED`, fronteira temporal | A troca de `>= 60` por `> 60` na comparação de segundos inteiros desloca a recuperação em um segundo. Os testes cobrem leases vigentes, expirados e escrita tardia, com token e snapshot preservados. |
| `AIExplanationService` | 183 | `MathMutator` | `SURVIVED`, duração do backoff | Trocar `attempt - 1` por `attempt + 1` alonga os dois intervalos para 1.000 e 2.000 ms. O limite de três tentativas, a interrupção e o estado persistido seguem protegidos. |
| `ConsultCenterSandboxAdapter` | 25 | `VoidMethodCallMutator` | `SURVIVED`, timeout de conexão | O loopback conecta imediatamente. O prazo de leitura está coberto com resposta HTTP deliberadamente tardia. |
| `PythonDocumentClient` | 21 | `VoidMethodCallMutator` | `SURVIVED`, timeout de conexão | Os testes locais cobrem payload, resposta e prazo de leitura. Uma falha de estabelecimento TCP depende do ambiente de rede. |
| `PythonExplanationClient` | 22 | `VoidMethodCallMutator` | `SURVIVED`, timeout de conexão | O cliente é verificado em loopback para 4xx, 5xx, sucesso e prazo de leitura. O mutante restante afeta o estabelecimento TCP. |

Nenhuma mutação foi excluída. A configuração do PIT continua incluindo todas as classes do namespace da aplicação e mantém a seleção original de testes.

### Arquivos alterados nesta execução

| Área | Arquivos |
|---|---|
| Maven | `backend-java/pom.xml` |
| Produção | `analysis/application/AIExplanationService.java`, `analysis/application/ExtractionContractValidator.java` |
| Testes de análise e domínio | `AIExplanationServiceTest.java`, `AnalysisControllerTest.java`, `AnalysisDataStoreTest.java`, `AnalysisReliabilityIntegrationTest.java`, `AnalysisWorkflowTest.java`, `ExtractionContractValidatorTest.java`, `FinancialEngineTest.java`, `FinancialProfileAssemblerTest.java` |
| Testes de decisão, política, proposta e documento | `DecisionControllerTest.java`, `DecisionServiceTest.java`, `DocumentDecisionTransactionalIntegrationTest.java`, `DocumentServiceTest.java`, `PolicyServiceTest.java`, `ProposalDocumentControllerTest.java`, `ProposalServiceTest.java`, `S3DocumentStorageTest.java` |
| Testes de integração de clientes e HTTP | `BureauHttpSecurityTest.java`, `BureauServiceTest.java`, `ConsultCenterSandboxAdapterTest.java`, `FixtureBureauAdapterTest.java`, `ProblemDetailsHttpTest.java`, `PythonClientsTest.java` |
| Auditoria | `development/audits/QUALITY_AUDIT_V3.md` |
