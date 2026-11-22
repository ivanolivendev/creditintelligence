# Inventário de testes

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
