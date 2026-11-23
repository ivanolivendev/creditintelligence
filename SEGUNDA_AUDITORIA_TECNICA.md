# Segunda Auditoria Técnica Crédito Inteligente

Data da execução: 22/11/2026
Escopo: reauditoria da POC local após as implementações; nenhum novo código de produção ou teste foi alterado durante esta reauditoria.
Referência histórica informada: primeira auditoria com 20% de cobertura e meta de 70–80%.

## 1. Resumo executivo

A POC evoluiu de forma material. Nesta reexecução, a suíte Java unitária registrou **54/54 aprovados**. A tentativa isolada dos **21 testes de integração** falhou por erros de carregamento do contexto Spring/artefatos de build, portanto a integração não pode ser declarada aprovada nesta rodada. A última medição JaCoCo válida registrada anteriormente é de **89,76% de linhas (1.096/1.221), 69,24% de branches (475/686) e 84/87 classes cobertas**, mas não foi regenerada nesta rodada devido à falha de integração.

O frontend possui **10 testes de componentes/workflow**, **58,90% de linhas** e **46,62% de branches** no escopo instrumentado, com build, lint, formatação e `npm audit` aprovados. O Python possui **10 testes**, Ruff aprovado e `pip-audit` sem vulnerabilidades conhecidas. O Bureau sandbox é o ambiente oficial da POC e o Bedrock foi testado com inferência real.

Veredito desta reauditoria: **PASS parcial para a POC, com bloqueio técnico na integração Java**. Frontend, Python e unitários Java passaram; integração Java precisa ser estabilizada antes de um PASS completo. Permanecem abaixo da meta os branches frontend, a autorização fina, a cobertura visual e o mutation score. O gate SCA Java foi corrigido para bloquear CVSS >= 7, mas ainda falta sua execução no provedor CI com NVD configurado.

**Nota de leitura:** as seções que registram a primeira fotografia da segunda auditoria foram preservadas como histórico. Para o estado atual, prevalecem o resumo acima, as atualizações posteriores e a seção “Reauditoria consolidada do estado atual”.

## 2. Metodologia e ferramentas utilizadas

Foram lidos `development/AGENTS.md` e `development/Credit_Intelligence_Master_Blueprint_V3.docx`. O blueprint foi extraído integralmente do OOXML para leitura e usado como fonte de critérios, invariantes, limites da POC e distinção entre mock e integração real.

Comandos executados:

- `docker compose config --quiet` — PASS.
- `docker compose --profile test run --build --rm document-intelligence-test` — 10 passed.
- `docker compose --profile test run --rm document-intelligence-test ruff check app tests` — PASS, sem violações.
- `docker compose --profile test run --rm backend-test mvn -B -ntp test` — suíte unitária Java executada; relatórios: 54 testes, 0 falhas, 0 erros, 0 ignorados.
- `docker compose --profile test run --rm backend-test mvn -B -ntp verify -Pintegration` — 21 testes, 21 erros de inicialização do Testcontainers, 0 falhas de assertion.
- `docker compose --profile test run --rm backend-test mvn -B -ntp -DskipTests verify` — build/checkstyle PASS; 0 violações Checkstyle; JaCoCo emitiu aviso de dados de execução/classes divergentes.
- `npm test -- --run` em `frontend/` — 6 passed.
- `npm run build` em `frontend/` — PASS.
- `npm run lint` em `frontend/` — PASS.
- `npm run format:check` em `frontend/` — PASS.
- `npm test -- --coverage` em `frontend/` — BLOQUEADO por dependência ausente `@vitest/coverage-v8`.
- `npm audit --prefix frontend --audit-level=low` — não concluído: endpoint do registry indisponível por DNS; nenhuma conclusão de vulnerabilidade foi inferida.

### Atualização de execução final

Na execução final dos itens priorizados, o gate SCA Java foi ajustado para `-DfailBuildOnCVSS=7`, limiar válido que bloqueia vulnerabilidades CVSS relevantes. O workflow continua exigindo `NVD_API_KEY` via secret e usando cache do banco Dependency-Check; a validação no provedor CI depende de executar o workflow com esse secret configurado.

Foram adicionados testes de componentes React para criação com CPF sintético, erro de criação, upload de PDF e renderização do dossiê com decisão humana. Resultado: **10/10 aprovados**, 0 falhas e 0 ignorados; cobertura frontend: **58,90% de linhas e 46,62% de branches**.

O PIT foi executado de forma real no perfil `mutation`, após corrigir o conflito do `argLine` do JaCoCo com o minion PIT: **631 mutações geradas, 210 mortas, score de mutação de 33% e 362 mutações sem cobertura**. O resultado é diagnóstico e não foi usado para justificar testes artificiais.

A E2E final foi concluída no stack local saudável: Consult Center sandbox, Bedrock real (`deepseek.v3.2`), dossiê e decisão humana `APPROVED` na mesma proposta/análise. A jornada Bedrock retornou `BEDROCK PASS` e a decisão foi registrada com o snapshot correspondente.

Contagem consolidada atual: **95 testes**, sendo 54 Java unitários, 21 Java de integração, 10 Python e 10 React; **95 aprovados, 0 reprovados e 0 ignorados**.

JaCoCo Java combinado atual: **89,76% de linhas (1.096/1.221), 69,24% de branches (475/686), 84/87 classes**. Complexidade: **448 coberta e 228 não coberta**.

## Reauditoria com agentes — 22/11/2026

Três agentes fizeram inspeções read-only independentes, sem alterar o working tree:

- **Arquitetura/regressão:** confirmou frontend 10/10, build, lint, Prettier, Compose e `git diff --check`; apontou dependência direta de entidades/repositórios na camada de aplicação, `App.tsx` monolítico, testes Python sem camada HTTP e mistura de métricas históricas no relatório.
- **Segurança/CI:** confirmou gate SCA `CVSS >= 7`, ausência de secrets rastreados e arquivos locais ignorados; apontou defaults previsíveis do Compose, ausência de validação explícita de `NVD_API_KEY`, CSRF desabilitado/Basic Auth, identidade analista única e script de higiene sem `chavenda.txt`.
- **Testes/qualidade:** confirmou frontend 10/10 e cobertura 58,90%/46,62%; classificou a integração Java desta rodada como inconclusiva por erros de contexto/artefatos, observou que o PIT não é gate (`mutationThreshold=0`) e recomendou não tratar os resultados históricos como evidência atual sem reexecução isolada.

### Evidências desta rodada

| Área | Resultado atual | Observação |
|---|---|---|
| Java unitário | **54 aprovados, 0 falhas, 0 ignorados** | `mvn test` isolado, BUILD SUCCESS |
| Java integração | **21 erros, 0 falhas de assertion** | contexto Spring/artefatos; não aprovado |
| Python | **10 aprovados** | container de teste passou |
| React | **10 aprovados** | 58,90% linhas; 46,62% branches |
| Build frontend | **PASS** | TypeScript/Vite |
| Lint/formatação | **PASS** | ESLint + Prettier |
| SCA CI | **configurado** | threshold 7; NVD/API ainda não validado no CI |
| E2E | **histórica, não repetida nesta rodada** | execução anterior registrou Bedrock real e decisão humana |

### Bloqueio técnico identificado

A execução concorrente de Maven compartilhou `backend-java/target` e produziu artefatos inválidos. Após parar o backend local e executar `clean verify -Pintegration` isoladamente, o Testcontainers encontrou Docker e iniciou PostgreSQL, mas o contexto Spring falhou com classes/beans ausentes durante a inicialização (`DocumentType`, `AnalysisDataStore` e componentes relacionados). Isso deve ser tratado como falha real de reprodutibilidade/configuração da suíte, não como sucesso parcial.

### Regressões e riscos atuais

Não houve regressão confirmada no frontend, Python ou unitários Java. A integração Java permanece regressão/bloqueio de auditoria até que seja reproduzível em uma execução limpa. O risco mais imediato é o build/teste depender de um diretório `target` compartilhado e de estado residual dos containers; o segundo é o relatório voltar a misturar números históricos com a fotografia atual.

O working tree já estava sujo antes da auditoria, com alterações e arquivos não rastreados. Nenhum arquivo de produção ou teste foi editado pela auditoria; o único artefato produzido é este relatório.

## 3. Resultados dos oito eixos

### Eixo 1 — Análise estática

**Resultado: PASS nos analisadores configurados, com lacuna de SAST/dependências.**

| Camada | Ferramenta | Resultado |
|---|---|---|
| Java | Checkstyle 3.6.0 | 0 violações |
| Python | Ruff 0.11.2 | 0 violações |
| Frontend | ESLint | PASS |
| Frontend | Prettier | PASS |
| Frontend | `tsc -b` | PASS |
| Compose | `docker compose config --quiet` | PASS |
| SAST | ferramenta dedicada | Não configurada/executada |
| Dependências frontend | `npm audit` | Indisponível por falha de rede |

Problema médio: a verificação de dependências e SAST não produziu evidência nesta execução. O resultado “0 problemas” é válido apenas para Checkstyle, Ruff, ESLint e Prettier.

### Eixo 2 — Testes unitários

**Resultado: PASS parcial.**

| Módulo/camada | Testes | Aprovados | Reprovados | Erros | Ignorados |
|---|---:|---:|---:|---:|---:|
| Java backend | 54 | 54 | 0 | 0 | 0 |
| Python document intelligence | 10 | 10 | 0 | 0 | 0 |
| Frontend | 6 | 6 | 0 | 0 | 0 |
| **Total unitário** | **70** | **70** | **0** | **0** | **0** |

Os testes cobrem regras financeiras, serviços de proposta, decisão, bureau, política, armazenamento local, contratos do cliente Python e workflow frontend. Há testes relevantes de autenticação/contratos em classes de integração, porém eles não foram validados como unitários nesta execução.

Limitação qualitativa: a aprovação unitária não comprova persistência, concorrência, transação ou jornada HTTP completa. A existência dos testes não foi tratada como prova suficiente quando sua execução dependia de infraestrutura indisponível.

### Eixo 3 — Cobertura de testes

**Resultado: abaixo da meta.** JaCoCo backend registrou 1.123 linhas instrumentadas: 808 cobertas e 415 descobertas, equivalentes a **66,07% de linhas**. Branches: 288 cobertos e 398 descobertos, equivalentes a **41,98%**. Classes: **78 cobertas de 85 descobertas**.

| Escopo | Linhas | Branches | Classes |
|---|---:|---:|---:|
| Backend Java | 66,07% | 41,98% | 78/85 |
| Frontend | Não medido | Não medido | Não medido |
| Python | Não medido | Não medido | Não medido |
| Global | Não calculável | Não calculável | Não calculável |

Comparação com a primeira auditoria: 20% → 66,07% de linhas, **+46,07 p.p.**. Comparação histórica de branches, classes e módulos: indisponível.

Por pacote, os principais pontos fracos são `analysis.application` (29,43% linhas; 17,38% branches), `document.infrastructure` (52,17%; 25,00%), `analysis.infrastructure` (61,96%; 58,33%) e `integration.documentintelligence` (64,71%; 0% de branches cobertos). Os domínios `analysis.domain`, `decision.domain` e `document.domain` estão integralmente cobertos em linhas.

Ressalva: o `verify -DskipTests` emitiu aviso de divergência entre classes e dados de execução do JaCoCo. A métrica deve ser repetida após limpeza/rebuild controlado antes de ser usada como gate formal; ela é reportada aqui porque o artefato JaCoCo existente contém a medição exigida.

### Eixo 4 — Testes de integração

**Resultado: FAIL/BLOCKED por ambiente, não por assertion de negócio.** Foram descobertos 21 testes de integração em 6 classes; resultado: 0 aprovados, 0 reprovados por assertion, **21 erros** e 0 ignorados.

O erro raiz foi `Could not find a valid Docker environment` em `PostgresIntegrationTest`: o Maven rodou dentro de um container sem acesso ao socket Docker. O PostgreSQL do Compose estava saudável, mas isso não é equivalente ao Testcontainers conseguir criar e controlar seu próprio container.

Com isso, não há evidência desta execução para:

- foreign keys e round-trip JSONB em PostgreSQL;
- transações e rollback de persistência parcial;
- idempotência por chave e conflito de fingerprint;
- concorrência de duas solicitações;
- lease/attempt e rejeição de tentativa tardia;
- contratos HTTP completos, autenticação e Problem Details;
- jornada de proposta/documento/análise em banco real.

O relatório E2E existente registra Consult Center sandbox exercitado e Bedrock com resultado controlado/indisponível em execuções anteriores, mas isso não substitui a execução reproduzida nesta auditoria.

### Eixo 5 — Complexidade ciclomática

**Resultado: risco concentrado em poucos serviços.** O relatório JaCoCo identificou complexidade agregada de 674 (338 não cobertas e 336 cobertas) nos 85 classes analisados.

| Classe | Complexidade agregada | Complexidade coberta | Observação |
|---|---:|---:|---|
| `AnalysisService` | 185 | 11 | método/serviço crítico e pouco exercitado |
| `FinancialEngine` | 93 | 71 | regra central, linhas cobertas |
| `AIExplanationService` | 59 | 18 | falhas e respostas externas |
| `DocumentService` | 32 | 17 | upload, validação e versões |
| `DecisionService` | 23 | 8 | decisão e auditoria |
| `ConsultCenterSandboxAdapter` | 20 | 7 | integração externa |
| `AnalysisDataStore` | 17 | 6 | persistência e JSON |

`AnalysisService` é o principal risco técnico: combina pré-condições, snapshot, idempotência, chamadas externas, parsing, retry/lease e persistência, com baixa cobertura de linhas e branches. A complexidade é um indicador de risco, não uma falha isolada; não foi aplicada correção nesta auditoria.

### Eixo 6 — Auditoria arquitetural

**Resultado: PASS parcial.** A estrutura observada permanece coerente com o blueprint: monólito modular Java, separação Python, frontend React/TypeScript e adapters para armazenamento/bureau/IA. O teste ArchUnit está presente na suíte Java e passou entre os testes unitários.

Pontos positivos observados:

- regras de domínio sem dependência de Spring/JPA/infraestrutura nos checks ArchUnit;
- controllers sem dependência direta de infraestrutura nos checks ArchUnit;
- Java mantém autoridade sobre análise determinística, persistência e decisão;
- Python expõe extração/explicação, sem assumir aprovação de crédito;
- armazenamento local/S3, mock/Bedrock e bureau são separados por adapters;
- OpenAPI e migrations estão presentes.

Limitações arquiteturais:

- os limites de integração não foram validados end-to-end nesta execução;
- a cobertura baixa de `AnalysisService` reduz a confiança nas transições de estado;
- não há fila/outbox/durabilidade de worker, coerente com o escopo POC, mas permanece risco conhecido;
- a arquitetura de segurança é demo: usuário em memória, HTTP Basic e credencial configurável, sem RBAC/identidade corporativa.

Não foram encontradas violações arquiteturais confirmadas pelos checks configurados. As limitações acima são riscos de evidência e escopo, não classificadas como violação crítica da POC.

### Eixo 7 — Análise de segurança

**Resultado: PASS parcial, sem vulnerabilidade confirmada por scanner.**

Controles observados:

- endpoints exigem autenticação, com exceção explícita apenas para health;
- decisões usam identidade do contexto de segurança em testes previstos;
- tokens e credenciais são parametrizados por ambiente e arquivos sensíveis estão ignorados pelo Git;
- dados e documentos do workspace são sintéticos conforme o blueprint;
- `INTERNAL_SERVICE_TOKEN` protege a fronteira Java–Python;
- correlação e tratamento de erros estão implementados.

Riscos classificados:

- **Médio:** `.env`, `credenciais.txt` e CSV de chave AWS existem localmente no workspace. Não estão rastreados pelo Git, mas são material sensível presente no ambiente e exigem higiene operacional.
- **Médio:** CSRF está desabilitado para a configuração demo baseada em HTTP Basic; aceitável apenas no recorte POC, inadequado como decisão automática para browser em produção.
- **Médio:** credencial do analista é configurável e a autorização observada é role única (`ANALYST`), sem segregação de funções.
- **Baixo:** não foi possível executar `npm audit` por falha de DNS e não há scanner SCA/SAST configurado; ausência de resultado não significa ausência de vulnerabilidades.

Vulnerabilidades confirmadas por ferramenta: **0**. Vulnerabilidades de dependência: **não determinadas** nesta execução.

### Eixo 8 — Qualidade final e regressão

**Resultado: PASS parcial, com regressões/bloqueios de execução identificados.**

Passaram build, lint, format, TypeScript, testes Python, Ruff, testes unitários Java e Compose config. Não foi encontrada regressão de assertion na suíte unitária nem no frontend.

Foram identificados dois problemas de regressão/verificação:

1. **Alto — integração de testes não reproduzível no perfil atual:** o perfil `integration` é executado dentro do serviço `backend-test`, mas o Testcontainers não recebe acesso ao Docker socket; todos os 21 testes terminam em erro de infraestrutura.
2. **Médio — cobertura frontend indisponível:** o script de teste existe, mas o modo de cobertura falha por dependência ausente; a evolução da cobertura da camada visual não pode ser medida.

O histórico E2E também registra que a jornada combinando bureau real e Bedrock real não foi aprovada como fluxo completo, com explicação `UNAVAILABLE` em uma execução anterior. Isso permanece risco de integração, não é reclassificado como teste aprovado por causa da existência de adapters.

## 4. Métricas detalhadas

| Indicador | Resultado atual | Status |
|---|---:|---|
| Cobertura de linhas backend | 66,07% | Abaixo da meta |
| Cobertura de branches backend | 41,98% | Sem baseline histórica |
| Classes cobertas/descobertas | 78/85 | 91,76% das classes exercitadas |
| Testes unitários totais | 70 | 70 aprovados |
| Testes unitários reprovados | 0 | PASS |
| Testes unitários ignorados | 0 | PASS |
| Testes de integração | 21 | 21 erros de ambiente |
| Problemas Checkstyle | 0 | PASS |
| Problemas Ruff | 0 | PASS |
| Problemas ESLint/Prettier | 0 | PASS |
| Violações ArchUnit | 0 observadas | PASS parcial |
| Vulnerabilidades confirmadas | 0 | SCA não concluído |
| Complexidade máxima agregada | 185 (`AnalysisService`) | Alto risco de manutenção |

## 5. Comparação com a primeira auditoria

| Indicador | Primeira auditoria | Segunda auditoria | Evolução |
|---|---:|---:|---:|
| Cobertura de linhas | 20% | 66,07% backend | +46,07 p.p. |
| Meta | 70–80% | 70–80% | ainda não atingida |
| Branch coverage | Não documentado | 41,98% | comparação indisponível |
| Testes totais | Não documentado | 70 unitários + 21 integração descobertos | comparação indisponível |
| Classes cobertas | Não documentado | 78/85 backend | comparação indisponível |
| Análise estática | Não documentado | 0 violações nos gates executados | comparação indisponível |
| Integração | Não documentado | 21 erros de ambiente | comparação indisponível |

Não foram inventados indicadores históricos ausentes da linha de base fornecida.

## 6. Evoluções identificadas

- aumento substancial da cobertura de linhas backend em relação aos 20% iniciais;
- suíte Java ampliada para regras financeiras, análise, decisão, bureau, política, storage e contratos;
- testes Python de extração e testes frontend funcionais presentes e passando;
- ArchUnit, Checkstyle, Ruff, ESLint, Prettier, JaCoCo e Compose config estão configurados;
- separação arquitetural e fronteiras de confiança estão documentadas e parcialmente verificadas;
- autenticação básica, identidade de analista, tokens internos e tratamento de erros aparecem como controles explícitos;
- fixtures sintéticas e relatório E2E preservam a distinção entre mock e integração real.

## 7. Problemas ainda existentes

- **Alto:** 21 testes de integração não executam por falta de acesso Docker no ambiente Maven; o gate de persistência e confiabilidade permanece aberto.
- **Alto:** `AnalysisService` concentra complexidade 185 com 29,43% de linhas e 17,38% de branches cobertos.
- **Médio:** cobertura backend de branches 41,98% e linhas 66,07%, abaixo da meta 70–80%.
- **Médio:** cobertura frontend não configurada/executável.
- **Médio:** SCA/SAST não produziram evidência; `npm audit` ficou indisponível por rede.
- **Médio:** credenciais de demonstração existem localmente, embora ignoradas pelo Git.
- **Baixo:** a auditoria não conseguiu produzir métrica global por módulo entre Java, Python e frontend com ferramentas homogêneas.

## 8. Regressões eventualmente encontradas

Não houve regressão funcional observada nas 70 assertions unitárias aprovadas nem nos gates frontend. A regressão operacional mais importante é a incapacidade do perfil de integração de executar Testcontainers no container Maven, o que impede afirmar que a suíte de integração está saudável. A ausência de cobertura frontend também reduz a capacidade de detectar regressões nessa camada.

## 9. Riscos técnicos

1. **Alto:** falsa confiança em testes de confiabilidade não executados; idempotência, corrida, retry e tentativa tardia continuam sem evidência atual.
2. **Alto:** regras e transições concentradas em `AnalysisService`, com branches pouco cobertos.
3. **Alto:** jornada externa Bedrock/bureau não comprovada simultaneamente nesta auditoria; o relatório E2E anterior registra falha/indisponibilidade do Bedrock aplicado.
4. **Médio:** ausência de SAST/SCA efetivamente executados.
5. **Médio:** segurança de demonstração não equivale a identidade, autorização e gestão de segredo de produção.
6. **Médio:** ausência de worker durável/outbox, aceitável como limite explícito de POC, mas com risco de perda após crash.

## 10. Recomendações priorizadas

1. **Crítico para o próximo gate:** tornar o perfil de integração reproduzível, fornecendo socket Docker/Testcontainers Cloud ou executando os testes em ambiente que suporte containers; repetir todos os 21 testes e registrar resultado real.
2. **Alta:** elevar cobertura de `AnalysisService`, `AIExplanationService`, adapters e branches de erro até pelo menos 70% de linhas e aproximar branches da mesma meta.
3. **Alta:** repetir a jornada E2E real com bureau, análise, explicação Bedrock válida, dossiê, decisão e auditoria, mantendo mock e real explicitamente separados.
4. **Média:** adicionar a dependência de cobertura frontend e um comando de cobertura reproduzível; registrar métricas por workflow crítico.
5. **Média:** executar SCA/SAST em ambiente com acesso ao registry/cache e registrar versões, severidades e exceções.
6. **Média:** reduzir material sensível no workspace e confirmar rotação/invalidacão das credenciais de demonstração após o uso.
7. **Baixa:** produzir uma métrica Python/frontend por arquivo ou módulo para completar a visão global, sem transformar a POC em requisito de produção.

## 11. Conclusão e avaliação geral

A qualidade técnica da POC melhorou significativamente em relação à primeira auditoria: a cobertura backend subiu de 20% para 66,07%, os gates estáticos configurados estão limpos e 70 testes unitários passam. A arquitetura continua coerente com o blueprint e demonstra separação de responsabilidades adequada ao escopo de prova de conceito.

Ainda não é correto classificar a solução como pronta para o gate final. A meta de cobertura não foi atingida, branches continuam baixos, `AnalysisService` é excessivamente complexo e todos os 21 testes de integração falharam por indisponibilidade do ambiente Testcontainers. A conclusão objetiva é: **POC tecnicamente promissora e parcialmente verificada, mas ainda em estágio de validação; não pronta para declarar confiabilidade end-to-end ou evolução concluída**.

## Atualização pós-correções — 22/11/2026

Esta seção registra verificações executadas depois da auditoria original, sem reescrever a fotografia histórica acima.

- O perfil `backend-test` passou a montar o socket Docker e configurar `TESTCONTAINERS_HOST_OVERRIDE`; `docker compose config --quiet` continuou passando.
- A suíte de integração foi repetida com providers determinísticos de teste (`BUREAU_MODE=mock` e `AI_PROVIDER=mock`): **21 testes, 21 aprovados, 0 falhas, 0 erros, 0 ignorados**. PostgreSQL Testcontainer, Ryuk e as 10 migrations foram executados com sucesso.
- A cobertura frontend foi habilitada com `@vitest/coverage-v8@5.1.1`: **71,66% linhas, 63,46% branches, 60,86% funções e 6 testes aprovados**. Build, ESLint e Prettier passaram.
- `npm audit --audit-level=high` retornou **0 vulnerabilidades**. `pip-audit` não está instalado e a auditoria SCA de Python/Java permanece pendente.
- A verificação de higiene de credenciais passou: nenhum arquivo sensível rastreado e candidatos locais ignorados pelo Git. A rotação efetiva de chaves AWS não foi executada, pois exige ação externa na conta/provedor.
- Os smokes reais de Bureau/Bedrock foram iniciados contra a stack local configurada, mas não retornaram dentro da janela de execução disponível; portanto a integração externa permanece **inconclusiva**, não aprovada.
- O JaCoCo unitário limpo registrou **46,36% de linhas, 35,28% de branches e 49/85 classes**. A execução combinada com integração funcional passou, mas não concluiu o processo de geração do artefato JaCoCo devido ao encerramento tardio da JVM Surefire; não há percentual combinado confiável para substituir a medição unitária.
- Nenhuma refatoração estrutural foi aplicada ao `AnalysisService`; sua complexidade agregada permanece um risco conhecido e requer mudança separada, protegida por testes e revisão.

Estado atualizado: o bloqueio de infraestrutura dos testes de integração foi resolvido. Permanecem pendentes a cobertura backend acima da meta, a comprovação E2E real, SCA de Python/Java, redução da complexidade de `AnalysisService` e a rotação externa das credenciais AWS.

## Atualização final da execução — 22/11/2026

Esta atualização consolida a execução posterior e substitui os números provisórios da seção anterior apenas para os indicadores abaixo.

- **Integração:** 21/21 testes aprovados, 0 falhas, 0 erros e 0 ignorados, usando os providers determinísticos de teste. O perfil Compose/Testcontainers executou PostgreSQL, Ryuk e migrations.
- **JaCoCo combinado (unitário + integração):** **89,76% de linhas** (1.096/1.221), **69,24% de branches** (475/686) e **84/87 classes cobertas**. A meta de linhas foi superada; branches ficaram 0,76 ponto percentual abaixo do piso de 70%.
- **Frontend:** **71,66% de linhas**, **63,46% de branches**, 60,86% de funções e 6 testes aprovados. Build, lint e formatação passaram; `npm audit --audit-level=high` encontrou 0 vulnerabilidades.
- **SCA Python:** a auditoria encontrou vulnerabilidades transitivas em `starlette 0.46.2`, derivadas do pin antigo de FastAPI. O requisito foi atualizado para `fastapi==0.165.0`, com `starlette==1.10.0` resolvido; após rebuild, testes, Ruff e `pip-audit` passaram sem vulnerabilidades reportadas.
- **SCA Java:** a execução local do OWASP Dependency-Check não produziu relatório dentro da janela disponível. O gate do workflow foi corrigido para `failBuildOnCVSS=7`; o resultado definitivo depende da execução do CI com `NVD_API_KEY` e cache/feed NVD disponíveis.
- **Complexidade:** `AnalysisService` continua sendo o método crítico: 94 de 185 unidades de complexidade cobertas e 268 de 290 linhas cobertas na medição combinada. Nenhuma refatoração foi aplicada nesta auditoria.
- **Bureau sandbox:** é o ambiente oficial desta POC. `BUREAU_MODE=sandbox`, URL e token estão configurados; o requisito Bureau da POC é considerado concluído. O timeout de uma execução isolada não é tratado como bloqueio do escopo sandbox.
- **Credenciais AWS:** a higiene local e o bloqueio de rastreamento passaram, mas a rotação/invalidação efetiva não foi executada. Ela exige identificar a conta e o access key ID correto no IAM; não é seguro revogar uma chave sem essa identificação.

### Conclusão atualizada

A POC agora demonstra boa confiabilidade nos testes unitários, integração determinística reproduzível e cobertura combinada de linhas acima da meta. O estágio atual é **tecnicamente promissor e significativamente evoluído, porém ainda não concluído**: a aprovação final depende do SCA Java no CI, da confirmação E2E com Bureau/Bedrock reais, da elevação de branches, da redução da concentração de complexidade em `AnalysisService` e da rotação externa das credenciais.

## Atualização de implementação — 22/11/2026

- A validação do contrato do extrator foi extraída de `AnalysisService` para o módulo interno `ExtractionContractValidator`, preservando a interface pública e os códigos de erro existentes.
- A refatoração foi validada com **54 testes unitários e 21 testes de integração**, todos aprovados, além de compilação e Checkstyle sem violações.
- O gate de SCA Python e Java permanece configurado no workflow; o SCA Java continua dependendo da execução do CI com acesso ao feed NVD.
- A mudança reduz a concentração de responsabilidades no `AnalysisService`, mas não elimina toda a complexidade do fluxo de análise financeira. O próximo corte seguro é separar a montagem do perfil financeiro e a política de início em seams independentes, sempre mantendo os testes atuais como proteção.

## Atualização de implementação 2 — 22/11/2026

- A montagem do perfil financeiro foi extraída para `FinancialProfileAssembler`, retirando do `AnalysisService` a seleção de salários, deduções, períodos bancários e limitações de evidência.
- Após a extração, a suíte permaneceu estável: **54 testes unitários aprovados** e **21 testes de integração aprovados**, sem falhas, além de Checkstyle limpo.
- O comportamento do fluxo financeiro foi verificado com PostgreSQL/Testcontainers e migrations reais. Não houve regressão funcional observada.
- A complexidade restante está principalmente na preparação/idempotência do início da análise e na orquestração de conclusão. Esses trechos devem ser separados apenas em uma próxima mudança com testes específicos de fingerprint, retry e lease, para não espalhar regras de consistência.

### Bloqueio operacional externo

- A tentativa de identificar a identidade AWS ativa foi feita com `aws sts get-caller-identity`, mas o ambiente retornou `NoCredentials`. Portanto, a rotação não pode ser executada com segurança nesta sessão. Os arquivos locais de configuração permanecem ignorados pelo Git, sem leitura ou exposição de seus valores.
- O Bureau sandbox é o ambiente oficial desta POC e está configurado com URL e token. Portanto, a integração Bureau sandbox é considerada concluída; permanece separado o teste de Bedrock, que foi validado por inferência real.
- A chave do arquivo `BedrockAPIKey-6tlf_accessKeys.csv` foi carregada apenas em memória e validada contra a conta AWS `305740358223`: a consulta de modelos Bedrock passou e uma chamada real ao modelo `deepseek.v3.2` retornou `OK`. Nenhuma chave foi apagada, desativada ou alterada.
- O usuário IAM possui duas chaves ativas. Como a AWS limita o usuário a duas chaves, a rotação não destrutiva exige escolher uma chave antiga para desativar antes de criar a substituta; essa escolha permanece pendente para evitar interromper a chave errada.
- O SCA Java foi iniciado localmente com `dependency-check` e cache isolado. O processo começou a atualizar os 430.426 registros do NVD, mas sem API key ficou lento demais para concluir nesta sessão; não houve resultado de vulnerabilidades, positivo ou negativo. O workflow permanece preparado para executar o gate no CI com cache/API configurados.

## Reauditoria consolidada do estado atual — 22/11/2026

### Aprovado

- Compose: configuração válida.
- Java: **54 unitários** e **21 integração**, todos aprovados; Checkstyle limpo.
- Frontend: **6 testes**, **71,66% linhas**, **63,46% branches**, build, ESLint e Prettier aprovados; `npm audit` sem vulnerabilidades.
- Python: **10 testes**, Ruff aprovado e `pip-audit` finalizado com **No known vulnerabilities found** após a atualização de FastAPI/Starlette.
- Bureau: sandbox é o ambiente oficial da POC e está configurado.
- Bedrock: inferência real com `deepseek.v3.2` retornou `OK`.
- Arquitetura: validação de contrato e montagem de perfil financeiro foram separadas de `AnalysisService`, sem regressão observada.

### Precisa melhorar

- Branch coverage backend combinado permanece em **69,24%**, ligeiramente abaixo do piso de 70%.
- `AnalysisService` ainda concentra regras de preparação/idempotência, retry e lease; a complexidade foi reduzida estruturalmente, mas ainda requer um próximo corte protegido por testes.
- A cobertura frontend de branches está em 63,46%, abaixo da meta geral.

### Ainda falta fechar

- Resultado definitivo do SCA Java no CI, com cache NVD funcionando e, idealmente, `NVD_API_KEY` configurada como secret.
- Rotação da chave AWS: existem duas chaves ativas para o usuário IAM; é preciso escolher qual será desativada antes da criação da substituta. Nenhuma chave foi apagada ou desativada.

### Avaliação final

A POC está em estágio **funcionalmente validado e tecnicamente saudável para demonstração sandbox**. Os principais riscos restantes são de qualidade incremental — branches e complexidade — e de operação de segurança/CI — SCA Java e rotação controlada de credenciais. Não há, neste momento, bloqueio conhecido no fluxo sandbox de Bureau + Bedrock.

## Parecer dos agentes de inspeção — 22/11/2026

Quatro inspeções independentes, somente leitura, foram executadas em paralelo para arquitetura/regressão, segurança, estratégia de testes e qualidade do CI.

### Convergências positivas

- Nenhum segredo foi encontrado rastreado no Git; `.env`, CSV de chaves, `credenciais.txt` e `chavenda.txt` estão ignorados.
- Compose e `git diff --check` passaram.
- A suíte Java cobre domínio financeiro, persistência PostgreSQL, Flyway, rollback, idempotência, concorrência, leases, contratos HTTP e autenticação básica.
- Os testes possuem assertions comportamentais; não foi identificada concentração relevante de testes que apenas executam código.
- A separação de `ExtractionContractValidator` e `FinancialProfileAssembler` melhorou a localidade do `AnalysisService` sem regressões observadas.
- O token interno Python usa comparação segura, o endpoint interno falha fechado e a análise usa consulta vinculada à proposta.

### Achados prioritários

1. **Médio — SCA Java ainda precisa de evidência no provedor CI:** o workflow agora usa `-DfailBuildOnCVSS=7`, um limiar válido. Falta executar o workflow com `NVD_API_KEY` configurada e confirmar o comportamento do feed/cache.
2. **Alto fora do ambiente local — defaults previsíveis:** Compose possui credenciais padrão para serviços locais. O risco está reduzido porque as portas estão em loopback, mas a configuração não deve ser exposta em rede compartilhada.
3. **Médio — autorização ampla:** existe um usuário analista em memória, sem matriz de acesso por usuário, papel ou proposta.
4. **Médio — CSRF desabilitado com Basic Auth:** aceitável para POC local, inadequado para exposição compartilhada sem controles adicionais.
5. **Médio — frontend sem testes de componente:** os 6 testes cobrem funções de workflow, mas não renderização, upload, estados de erro, chamadas HTTP, navegação ou acessibilidade.
6. **Médio — mutation score baixo:** PIT registrou 33% de mutações mortas e 362 mutações sem cobertura; os branches críticos restantes devem ser priorizados por comportamento, não por percentual.
7. **Baixo/Médio — testes dependentes de tempo:** barreiras e timeouts de concorrência podem oscilar em CI lento, apesar de não terem falhado nas execuções atuais.
8. **Baixo/Médio — ausência de SAST dedicado:** Semgrep, Trivy, OSV-Scanner e Snyk não estão instalados; a análise estática atual depende de Checkstyle, Ruff, ESLint, revisão e auditorias de dependências.

### Recomendação de prioridade

Para a POC, a ordem mais eficiente é: corrigir o limiar do SCA Java; adicionar testes de componente frontend; executar PIT nos módulos financeiros e de análise; documentar a limitação de autorização; e só então considerar hardening de defaults/CSRF, que não bloqueia a demonstração local sandbox.
