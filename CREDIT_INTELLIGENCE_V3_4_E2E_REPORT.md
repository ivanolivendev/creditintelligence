# Credit Intelligence V3.4 E2E Report

Data da execução: 21/11/2026

## Resumo executivo

A infraestrutura Docker local ficou operacional e a implementação mais recente foi reconstruída. Os testes automatizados do backend passaram: 23 testes Java, 10 testes Python e Ruff sem violações.

A jornada principal não foi aprovada. O token do sandbox Consult Center não estava disponível no ambiente, portanto a consulta externa real não pôde ser executada. O cenário principal foi classificado como BLOQUEADO, sem substituição silenciosa por mock.

O Bedrock foi selecionado no container com `AI_PROVIDER=bedrock`, região `us-east-1` e modelo `qwen.qwen3-next-80b-a3b`. Uma jornada diagnóstica separada atingiu o backend com provider Bedrock, mas terminou com `aiExplanationStatus=UNAVAILABLE`; o log sanitizado registrou `Bedrock explanation failed (ValueError)`. Portanto, a integração real do Bedrock também não foi considerada aprovada.

Veredito: **NÃO PRONTA** para demonstração V3.4 com as duas integrações externas reais.

## Ambiente observado

| Componente | Resultado | Evidência |
|---|---|---|
| PostgreSQL | PASSOU | Container healthy; Flyway validou 9 migrations e aplicou V9 anteriormente |
| Backend Java | PASSOU | Container healthy; `/actuator/health` retornou `UP` |
| Python/FastAPI | PASSOU | Container healthy |
| Frontend | PASSOU | HTTP local em `127.0.0.1:18081` acessível; nenhum arquivo frontend foi alterado |
| Compose | PASSOU | `docker compose config --quiet` |
| Consult Center sandbox | BLOQUEADO | `BUREAU_SANDBOX_TOKEN` ausente |
| Bedrock | FALHOU | Provider configurado, mas resposta não passou pela validação da aplicação |

## Matriz E2E

| ID | Cenário | Esperado | Observado | Status | Evidência |
|---|---|---|---|---|---|
| E2E-01 | Jornada completa | Bureau real + análise + Bedrock + dossiê + decisão | Token do bureau ausente; jornada principal não pôde começar com integração real | BLOQUEADO | `BUREAU_SANDBOX_TOKEN=ABSENT` |
| E2E-02 | Análise sem bureau | Bloqueio controlado | Regra implementada e coberta pela suíte Java; não executado como fluxo externo real | NÃO EXECUTADO | Testes de integração Java |
| E2E-03 | Menos de seis documentos | Bloqueio por pré-condição | Regra implementada; não executado como jornada HTTP independente | NÃO EXECUTADO | `AnalysisService.hasCompleteFinancialCoverage` |
| E2E-04 | Renda ambígua | Sem capacidade consolidada | Coberto por testes de extração e motor; não executado na jornada externa | NÃO EXECUTADO | Python/Java tests |
| E2E-05 | Diferença acima de 3% | Finding de divergência | Coberto por `FinancialEngineTest` | PASSOU | Suíte Java |
| E2E-06 | Transação desconhecida | Hipótese separada | Contrato de hipóteses implementado e validado; não houve resposta Bedrock válida | BLOQUEADO | Bedrock `UNAVAILABLE` |
| E2E-07 | Restrição de bureau | Alerta sem parcela fictícia | Adapter e testes de mapeamento cobrem ausência de valor; sandbox real não consultado | BLOQUEADO | Token ausente |
| E2E-08 | Todos findings PASS | Bedrock ainda invocado | Teste Python confirma chamada em cenário all-PASS; chamada real da aplicação não produziu resposta válida | FALHOU | `test_bedrock_calls_model_when_all_findings_pass` + log ValueError |
| E2E-09 | Falha Bedrock | Erro explícito sem mock | Aplicação retornou `UNAVAILABLE`, sem fallback MOCK | PASSOU | Smoke diagnóstico |
| E2E-10 | Falha bureau | Falha controlada | Adapter possui estado `UNAVAILABLE`; sandbox real indisponível por falta de token | BLOQUEADO | Configuração |
| E2E-11 | Reprocessamento | Histórico preservado | Coberto pelos testes de confiabilidade e snapshots; não executado no E2E externo | PASSOU | Java integration tests |

## Evidência Consult Center

Não foi realizada requisição HTTPS real nesta execução. A variável `BUREAU_SANDBOX_TOKEN` estava ausente. O sistema permaneceu configurado com `BUREAU_MODE=mock` no diagnóstico local e isso não foi usado como evidência da integração externa.

A suíte automatizada valida o adapter HTTP, timeout, envelope e ausência de invenção de restrições, mas isso não comprova disponibilidade do fornecedor externo.

## Evidência Amazon Bedrock

Configuração efetiva observada dentro dos containers:

```text
AI_PROVIDER=bedrock
AWS_REGION=us-east-1
BEDROCK_MODEL_ID=qwen.qwen3-next-80b-a3b
```

As credenciais foram obtidas temporariamente pelo perfil AWS `bedrock` através do script existente e não foram registradas no relatório.

O smoke diagnostic executou uma análise real pelo backend com provider Bedrock, mas observou:

```text
analysis=COMPLETED
explanation=UNAVAILABLE
provider=
```

O log sanitizado do serviço Python registrou:

```text
Bedrock explanation failed (ValueError)
```

Não houve fallback silencioso para `MOCK`. O resultado é FALHOU para a integração aplicada, mesmo com a chamada configurada para Bedrock.

## Evidência financeira

A política V3.4 foi aplicada pela migration V9 e os testes do motor validaram a nova regra de 30% e tolerância percentual de 3%.

| Regra | Resultado validado |
|---|---:|
| Capacidade total com renda de R$ 8.000 | R$ 2.400,00 |
| Capacidade após obrigação de R$ 1.500 | R$ 900,00 |
| Cenário V3.4 esperado | 30% da renda média menos R$ 450,00 |
| Média do cenário principal | R$ 6.200,00 |
| Capacidade do cenário principal | R$ 1.410,00 |
| Tolerância de conciliação | 3% |

O cenário principal de seis PDFs com esses valores não foi aprovado no E2E porque a etapa de bureau real ficou bloqueada.

## Persistência observada

Foram consultadas apenas contagens agregadas no PostgreSQL, sem leitura de documentos ou dados pessoais:

```text
proposal              246
document_metadata     321
bureau_consultation    21
analysis              201
financial_assessment   27
validation_finding    481
ai_explanation        164
analyst_decision       30
audit_record           30
```

As tabelas reais encontradas foram usadas; não foi assumido nome de tabela inexistente.

## Testes automatizados

| Comando | Resultado |
|---|---|
| `docker compose --profile test run --build --rm document-intelligence-test` | 10 passed |
| `docker compose --profile test run --rm document-intelligence-test ruff check app tests` | All checks passed |
| `docker compose --profile test run --build --rm backend-test` | 23 testes, 0 falhas, 0 erros |
| `docker compose config --quiet` | PASSOU |

Esses testes incluem mocks e adapters locais. Eles não substituem a comprovação de rede do Consult Center nem a validação de uma resposta Bedrock válida na jornada completa.

## Problemas identificados

1. Consult Center sem credencial

   - Arquivo/configuração: `BUREAU_SANDBOX_TOKEN` no Compose.
   - Severidade: bloqueadora.
   - Impacto: impede a jornada V3.4 principal.
   - Correção mínima: fornecer token de homologação válido e repetir o E2E sem alterar o código.

2. Resposta Bedrock rejeitada pela aplicação

   - Arquivos envolvidos: `document-intelligence-python/app/main.py` e contrato Java/Python.
   - Severidade: alta.
   - Impacto: explicação fica `UNAVAILABLE`; hipóteses e provider não chegam ao dossiê.
   - Correção mínima: capturar a resposta sanitizada do modelo em execução autorizada, identificar qual validação causou o `ValueError` e ajustar apenas o contrato/prompt necessário. Não criar fallback MOCK.

## Veredito

**NÃO PRONTA.**

A infraestrutura local e os testes automatizados estão saudáveis, mas a definição de pronto exige comprovar simultaneamente Consult Center real e Bedrock real com resposta estruturada válida. O primeiro está BLOQUEADO por credencial ausente e o segundo FALHOU na validação da resposta observada.
