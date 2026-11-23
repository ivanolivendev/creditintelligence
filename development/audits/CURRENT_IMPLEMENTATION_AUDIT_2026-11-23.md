# Auditoria da implementacao atual

## Veredito

**PARTIAL - nucleo local verificado; integracoes externas pendentes de nova comprovacao.**

Esta auditoria usa o working tree atual como fonte de verdade. Codigo e configuracao descrevem o que existe; somente os comandos listados como executados abaixo provam comportamento neste snapshot. Relatorios de 21 e 22 de novembro continuam como evidencia historica, nao como prova desta arvore de trabalho.

## Implementacao observada no codigo

- O produto e um monolito modular Java 21/Spring Boot, com adaptadores e portas explicitos para proposta, documento, analise, politica, decisao e ciclo de vida da analise. Java continua autoridade de estado, regras deterministicas e decisao humana.
- O fluxo agora inclui analise financeira V3.4: tres competencias correspondentes de holerites e extratos, consulta de bureau disponivel, conciliacao salarial, deduplicacao de obrigacoes, capacidade de parcela de 30% e tolerancia salarial de 3%. A politica persistida e `CREDIT_POLICY_DEMO_3.4`.
- Documentos PDF digitais sao validados por assinatura e texto, limitados a 10 MiB, armazenados por adaptador S3 compativel, hashados, versionados e associados a snapshots imutaveis.
- A analise usa chave de idempotencia persistida, fingerprint de entrada, tentativas e leases. A chamada Java-Python permanece sincrona; nao ha garantia de execucao duravel apos reinicio.
- A explicacao de IA e validada pelo Java para provider, analise, findings, politica e referencias de evidencia. Pode terminar como `AVAILABLE`, `INVALID_OUTPUT` ou `UNAVAILABLE`; nao modifica findings nem decide credito.
- A decisao humana requer `ROLE_ANALYST`, analise completa e snapshot documental atual. A autenticacao e Basic Auth de demonstracao, com uma identidade em memoria; nao e controle multiusuario de producao.
- O runtime Compose usa Bedrock e Consult Center sandbox por padrao. Os testes Compose escolhem explicitamente providers mock. A topologia local usa PostgreSQL, MinIO, Python/FastAPI, Java/Spring e React/Nginx, com portas publicadas somente em `127.0.0.1`.

## Verificacao executada neste working tree

| Area | Comando | Resultado |
|---|---|---|
| Configuracao Compose | `docker compose config --quiet` | PASS |
| Python | `docker compose --profile test run --build --rm document-intelligence-test python -m pytest -q` | PASS - 17 testes; 1 aviso de deprecacao do TestClient |
| Python lint | `docker compose --profile test run --build --rm document-intelligence-test ruff check app tests` | PASS |
| Java unitario e Arquitetura | `docker compose --profile test run --rm backend-test mvn -B -ntp test` | PASS - 276 testes, 0 falhas, 0 erros, 0 ignorados |
| Java integracao | `docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify` | PASS - 24 testes, 0 falhas, 0 erros, 0 ignorados |
| Frontend | `npm test` | PASS - 10 testes em 2 arquivos |
| Frontend build | `npm run build` | PASS |
| Frontend lint | `npm run lint` | PASS |
| Frontend formatacao | `npm run format:check` | PASS |

## Limites e itens ainda nao comprovados

- A disponibilidade e a resposta valida do Bedrock/DeepSeek nao foram chamadas nesta auditoria: dependem de credenciais, permissao, modelo, custo e rede atuais.
- O Consult Center sandbox nao foi chamado nesta auditoria: depende de token e conectividade atuais.
- Nao foram reexecutados JaCoCo consolidado, PIT, Dependency-Check, `pip-audit`, `npm audit` ou um workflow CI remoto. As metricas antigas desses controles sao historicas.
- A recuperacao de tentativa em andamento usa o limite efetivo de 60 segundos implementado em `AnalysisService`; a configuracao Compose de 180 segundos nao deve ser apresentada como protecao efetiva ate que codigo e configuracao sejam alinhados.
- O perfil `backend-test` monta o socket do Docker para Testcontainers. Ele serve ao ambiente local de testes, nao a uma topologia de implantacao segura.

## Fontes de evidencia

- Implementacao e contratos: `backend-java/src/main/java/`, `backend-java/src/main/resources/openapi.yaml`, migrations V1 a V10, `document-intelligence-python/app/`, `frontend/src/` e `compose.yaml`.
- Resultados atuais: relatorios Surefire/Failsafe gerados em `backend-java/target/`, saida dos comandos desta auditoria e testes Python/frontend executados em 23/11/2026.
- Historico: `CREDIT_INTELLIGENCE_V3_4_E2E_REPORT.md` e `development/audits/QUALITY_AUDIT_V3.md`.
