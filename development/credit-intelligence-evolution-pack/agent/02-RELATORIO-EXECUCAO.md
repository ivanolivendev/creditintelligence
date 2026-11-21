# Relatório de execução — Evolution Pack 4.0

**Data/ambiente:** 20/11/2026, Windows/PowerShell, Docker Compose local, PostgreSQL 16, Java 21, Python 3.12, React/Vite. Branch Git Flow `feature/evolution-pack-4`; base `9650b14`. Nenhum deploy AWS.

## Inventário e matriz Blueprint → implementação

| Capacidade | Baseline | Entrega/evidência |
|---|---|---|
| React | Existente | Mesa ampliada em `frontend/src/App.tsx`; build/lint/Prettier passaram. |
| Java/Spring | Existente | Módulos preservados; novo `FinancialEngine` e módulo `bureau`. |
| PostgreSQL/Flyway | V1–V5 | V6 períodos, V7 política/assessment, V8 consulta de bureau; oito migrations validadas. |
| Upload PDF | Um documento por tipo | Série 3+3 por período, versão, checksum e snapshot; smoke passou. |
| Python/FastAPI | Contrato 1.0 | Contrato 2.0 de contracheque/extrato com evidências; 1.0 preservado; 10 testes passaram. |
| Bedrock/Mock | Cliente existente, `MOCK` padrão | Explicação recebe assessment Java; mock E2E passou. Bedrock real não invocado nesta execução. |
| Análise/idempotência | Existentes | Fingerprint inclui cenário e consulta externa; reutilização e 409 verificados. |
| Dossiê/decisão | Existentes | Assessment persistido; decisão bloqueada para PDF ou bureau obsoleto. |
| Bureau | Ausente | Porta independente, fixture `MOCK`, adapter Consult Center v3 `SANDBOX` verificado. |

O Blueprint V3 continua válido para proposta, análise documental e decisão humana. O Pack 4.0 acrescenta pré-análise financeira documental; política de 35% e simulação são fictícias. A API antiga `POST /analyses` sem corpo e política `POLICY-1.0` permanecem operacionais. `period=YYYY-MM` é exigido somente para os novos documentos financeiros.

## Alterações e contratos

- Preservados: migrations V1–V5, armazenamento, integração Bedrock opcional, análise e decisão legadas.
- Alterados: APIs Java de documentos/análise/explicação/decisão, parsers Python, frontend, Compose, `.env.example`, README e fixtures.
- Adicionados: `FinancialEngine`, política `CREDIT_POLICY_DEMO_2.0`, avaliação persistida, `CreditBureauPort`, adapters fixture e Consult Center, endpoints de bureau, seis PDFs sintéticos, smokes financeiro/bureau, OpenSpec e testes.
- Migrations aditivas: `V6__financial_document_periods.sql`, `V7__financial_assessment.sql`, `V8__bureau_consultations.sql`. Volume local preservado.
- `POST /documents` aceita `PAYSLIP`/`BANK_STATEMENT` com `period`; `POST /analyses` aceita cenário JSON opcional; GET de análise/dossiê inclui `financialAssessment`; `POST /bureau-consultations` e `GET /bureau-consultations/latest` aceitam apenas casos sintéticos explícitos.

## Gates observados

| Gate | Status | Evidência |
|---|---|---|
| G0 baseline | PASS | Antes das mudanças: Java 12/12, Python 8/8, frontend build e smokes existentes passaram. |
| G1 documentos | PASS | `smoke-financial.ps1`: seis PDFs, competências, versão e snapshot; teste API Java 4/4. |
| G2 core | PASS | `FinancialEngineTest`: média 7733,33, matches 3/3, crédito desconhecido, deduplicação, capacidade 2800/1300 e Price r=0/r>0. |
| G3 análise | PASS | Dossiê persistido, idempotência e novo fingerprint por PDF e bureau. |
| G4 resiliência | PASS | Contrato V2 inválido, erro Python e retentativa, idempotência, concorrência, IA inválida/indisponível, bureau 401/502 e timeout HTTP real de oito segundos verificados; ausência não vira zero restrições. Bedrock real é opcional e não foi chamado. |
| G5 interface | PASS | Navegador local: criou proposta, enviou os seis períodos, consultou bureau mock, executou análise, recuperou dossiê e registrou decisão humana. Exibiu média 7733,33 e três matches. Bundle final servido com HTTP 200. |
| G6 entrega | PASS | README, OpenSpec validado estritamente, este relatório, comandos e limites documentados. |

Comandos: `docker compose --progress plain up --build -d`; `docker compose --profile test run --rm backend-test mvn -B -ntp test` (23/23 após os novos testes); teste focado `ConsultCenterSandboxAdapterTest` (4/4); `docker compose --profile test run --rm document-intelligence-test` (10/10); `docker compose --profile test run --rm document-intelligence-test ruff check app tests`; `npm run format:check`, `npm run lint`, `npm run build`; `scripts/smoke-proposals.ps1`, `scripts/smoke-analysis.ps1`, `scripts/smoke-financial.ps1`, `scripts/smoke-bureau.ps1`; `openspec validate evolution-financial-documents --strict`; `openspec validate --specs --strict` (10/10). No navegador, um upload com metadado de agosto e conteúdo de outro mês falhou com `INVALID_EXTRACTION_RESPONSE`, como exigido pelo contrato; os PDFs corretos foram reenviados e a análise passou.

## Revalidação completa — 20/11/2026

Uma nova execução, a partir da árvore limpa da branch publicada, confirmou:

| Verificação | Resultado |
|---|---|
| Compose | Build limpo das três imagens; PostgreSQL, Python, backend e frontend saudáveis. |
| Java | 23 testes, 0 falhas, 0 erros, 0 ignorados. |
| Python | 10 testes aprovados; Ruff sem achados. |
| Frontend | `npm ci` com 0 vulnerabilidades; Prettier, ESLint e build Vite aprovados. |
| OpenSpec | 10 specs aprovadas com `--strict`; nenhuma mudança ativa pendente. |
| Smokes integrados | T01–T18 e T22 aprovados, incluindo upload inválido/oversize, versionamento, idempotência, conflito, snapshots, análise, financeiro, decisão e bloqueio após decisão final. |
| Bureau externo | Sandbox oficial sintético aprovado em `SANDBOX`, incluindo resposta `UNAVAILABLE` sem fabricar restrições; backend restaurado e confirmado em `BUREAU_MODE=mock`. |
| Interface | Jornada no navegador aprovada: login, proposta sintética, documento, bureau mock, análise/dossiê e decisão humana `MORE_INFO_REQUIRED`. A análise `28c00f8d-00ae-4f25-88d3-74b64e8beb00` exibiu política `CREDIT_POLICY_DEMO_2.0`, evidências, limitações e IA `MOCK`. |

O caminho Bedrock real não foi chamado porque requer credenciais AWS e pode gerar cobrança. Seu contrato, saída inválida, indisponibilidade e fallback foram exercitados por testes automatizados; nenhuma chamada paga foi necessária para esta revalidação.

## Integrações externas e custos

- Bureau: [Consult Center v3 OpenAPI](https://api.consultcenter.com.br/v3/openapi/serasa), produto `credit_bureau`, header `APITOKEN`, `GET /v3/consultas/credit-bureau`. A documentação oficial publica token gratuito e CPFs sintéticos. Nenhuma credencial privada ou CPF real. Verificação direta observou HTTP 200, envelope `consulta/dados/avisos` e score opcional. O fluxo no aplicativo passou com `SANDBOX/AVAILABLE`, snapshot e decisão obsoleta 409. Caso oficial de indisponibilidade HTTP 502 mapeado como `UNAVAILABLE` com restrições `null`.
- `BUREAU_MODE=mock` é o padrão final do Compose. O token público foi injetado apenas no processo da prova e removido; backend recriado em mock.
- Bedrock: `MOCK` nesta execução; nenhuma chamada paga ou recurso AWS criado. Integração opcional preservada, sem afirmar teste real nesta rodada.

## Limites e demonstração

- Apenas PDFs sintéticos controlados, sem OCR universal. Campo ausente/ambíguo permanece não factual.
- Matching e deduplicação dependem de evidência e descrições controladas; incerteza bloqueia capacidade verificável. Price parcial não inclui CET, IOF, tarifas ou seguro.
- Sandbox é estático, não histórico real. Produto mapeado não fornece contagem canônica de restrições; ausência não vira zero. Score preserva modelo e não é probabilidade de inadimplência.
- O volume local contém propostas sintéticas criadas pelos smokes; não foi apagado.
- Cenário A: líquidos 7300/8500/7400 → média 7733,33, três depósitos, um crédito desconhecido, nova parcela 2706,67 no fixture. Cenário B: renda 8000 e obrigações extras 1500 → limite 2800 e nova parcela 1300 em teste Java. Cenário F: `MOCK/NOT_REQUESTED` padrão e `SANDBOX/AVAILABLE` após consulta explícita; relatório novo torna dossiê anterior obsoleto.

## Fechamento OpenSpec

As sete specs delta foram sincronizadas nas specs principais. `bureau-context` e `financial-assessment` foram criadas; `analyst-workspace`, `controlled-explanation`, `deterministic-analysis`, `document-extraction` e `document-ingestion` receberam os requisitos adicionais. A regra de versionamento foi reconciliada com o código: documentos legados versionam por tipo, enquanto documentos financeiros versionam por tipo e período. `openspec validate --specs --strict` passou com 10 specs e a mudança passou na validação estrita antes do arquivamento.

As mudanças `evolution-financial-documents` e `clear-deterministic-findings` foram arquivadas em `openspec/changes/archive/2026-11-20-*`. A segunda também sincronizou o requisito de findings determinísticos acionáveis em português. A lista final de mudanças ativas ficou vazia e as 10 specs principais passaram novamente na validação estrita.
