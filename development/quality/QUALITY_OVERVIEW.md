# Dossiê de qualidade e confiabilidade técnica

## Estado de vigência

Esta e uma fotografia historica de 22/11/2026. O estado do working tree e os resultados reexecutados em 23/11/2026 estao em `development/audits/CURRENT_IMPLEMENTATION_AUDIT_2026-11-23.md`; nao use os totais abaixo como prova da arvore atual sem essa distincao.

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

As evidências demonstram regras financeiras determinísticas com `BigDecimal`, validação de campos extraídos e referências, preservação de estados incertos, versionamento documental, snapshots, idempotência por chave e fingerprint, leases, rejeição de respostas tardias, contratos HTTP, rollback transacional e decisão humana autorizada. A matriz detalhada está em [QUALITY_EVIDENCE_MATRIX.md](QUALITY_EVIDENCE_MATRIX.md).

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

As classificações não se transferem entre horizontes. A justificativa completa, riscos e requisitos adicionais estão em [ARCHITECTURE_AND_RISKS.md](ARCHITECTURE_AND_RISKS.md). Os comandos reproduzíveis estão em [REPRODUCIBILITY.md](REPRODUCIBILITY.md).
