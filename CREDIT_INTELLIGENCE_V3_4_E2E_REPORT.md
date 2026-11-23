# Registro histórico de E2E V3.4

> Este arquivo preserva execuções de 21 e 22/11/2026 e contém conclusões contraditórias entre as duas atualizações. Ele não é a fonte de estado do working tree atual. Para código e verificações de 23/11/2026, consulte `development/audits/CURRENT_IMPLEMENTATION_AUDIT_2026-11-23.md`. Uma nova execução credenciada de Bedrock e Consult Center é necessária para substituir este registro.

# Atualização de verificação — 22/11/2026

- A API key exportada para o usuário `BedrockAPIKey-6tlf` foi extraída corretamente do CSV e injetada no container como `AWS_BEARER_TOKEN_BEDROCK`, sem exibição do segredo.
- A chamada real `Converse` para `deepseek.v3.2` alcançou a AWS, mas retornou `AccessDeniedException`: a SCP organizacional `p-7gcyevpl` contém um `explicit deny` para `bedrock:CallWithBearerToken`.
- O smoke real do Consult Center sandbox passou: consulta `SANDBOX`, dossiê versionado, stale decision `409` e nova análise.
- O bloqueio restante do Bedrock é administrativo na organização AWS. Não há fallback para MOCK.
- Mitigação aplicada: o Compose passou a usar as credenciais IAM tradicionais do mesmo usuário via SigV4, sem `AWS_BEARER_TOKEN_BEDROCK`.
- Smoke real completo passou: `BEDROCK PASS`, modelo `deepseek.v3.2`, finding `WARNING`.

# Credit Intelligence V3.4 E2E Report

Data da execução: 21/11/2026

## Resumo executivo

A infraestrutura Docker local ficou operacional e a implementação mais recente foi reconstruída. Os testes automatizados do backend passaram: 23 testes Java, 10 testes Python e Ruff sem violações.

A jornada principal ainda não foi aprovada. Nesta execução o token sandbox fornecido permitiu uma consulta externa real e o teste de versão/stale decision passou; porém a jornada Bedrock com DeepSeek ainda terminou como `UNAVAILABLE`, sem substituição silenciosa por mock.

O Bedrock foi selecionado no container com `AI_PROVIDER=bedrock`, região `us-east-1` e modelo `deepseek.v3.2`. O modelo foi autorizado na policy demo e chamado; a resposta ainda não passou pela validação do contrato na jornada completa. O adaptador agora aceita JSON cercado por markdown e usa limite de 2.000 tokens, sem fallback para MOCK.

Veredito: **NÃO PRONTA** para demonstração V3.4 com as duas integrações externas reais.

## Ambiente observado

| Componente | Resultado | Evidência |
|---|---|---|
| PostgreSQL | PASSOU | Container healthy; Flyway validou 9 migrations e aplicou V9 anteriormente |
| Backend Java | PASSOU | Container healthy; `/actuator/health` retornou `UP` |
| Python/FastAPI | PASSOU | Container healthy |
| Frontend | PASSOU | HTTP local em `127.0.0.1:18081` acessível; nenhum arquivo frontend foi alterado |
| Compose | PASSOU | `docker compose config --quiet` |
| Consult Center sandbox | PASSOU | Smoke real: consulta, dossiê versionado e stale decision `409` |
| Bedrock DeepSeek | FALHOU | Provider chamado, mas resposta não passou pela validação da aplicação |

## Matriz E2E

| ID | Cenário | Esperado | Observado | Status | Evidência |
|---|---|---|---|---|---|
| E2E-01 | Jornada completa | Bureau real + análise + Bedrock + dossiê + decisão | Bureau real passou; Bedrock terminou `UNAVAILABLE` | FALHOU | Smokes backend-only |
| E2E-02 | Análise sem bureau | Bloqueio controlado | Regra implementada e coberta pela suíte Java; não executado como fluxo externo real | NÃO EXECUTADO | Testes de integração Java |
| E2E-03 | Menos de seis documentos | Bloqueio por pré-condição | Regra implementada; não executado como jornada HTTP independente | NÃO EXECUTADO | `AnalysisService.hasCompleteFinancialCoverage` |
| E2E-04 | Renda ambígua | Sem capacidade consolidada | Coberto por testes de extração e motor; não executado na jornada externa | NÃO EXECUTADO | Python/Java tests |
| E2E-05 | Diferença acima de 3% | Finding de divergência | Coberto por `FinancialEngineTest` | PASSOU | Suíte Java |
| E2E-06 | Transação desconhecida | Hipótese separada | Contrato de hipóteses implementado e validado; não houve resposta Bedrock válida | FALHOU | Bedrock `UNAVAILABLE` |
| E2E-07 | Restrição de bureau | Alerta sem parcela fictícia | Adapter e smoke sandbox real executado; explicação Bedrock não chegou ao dossiê | FALHOU | Smoke Bureau + Bedrock |
| E2E-08 | Todos findings PASS | Bedrock ainda invocado | Teste Python confirma chamada em cenário all-PASS; chamada real da aplicação não produziu resposta válida | FALHOU | `test_bedrock_calls_model_when_all_findings_pass` |
| E2E-09 | Falha Bedrock | Erro explícito sem mock | Aplicação retornou `UNAVAILABLE`, sem fallback MOCK | PASSOU | Smoke diagnóstico |
| E2E-10 | Falha bureau | Falha controlada | Adapter possui estado `UNAVAILABLE`; caminho de erro coberto por testes | PASSOU | Suíte Java |
| E2E-11 | Reprocessamento | Histórico preservado | Coberto pelos testes de confiabilidade e snapshots; não executado no E2E externo | PASSOU | Java integration tests |

## Evidência Consult Center

Foi realizada requisição HTTPS real ao sandbox com o token fornecido, mantido somente em memória durante o processo. O smoke observou consulta disponível, dossiê versionado, stale decision `409` e nova análise `af46db4f-41dd-42c6-9de0-6011e4103f97`.

A suíte automatizada valida o adapter HTTP, timeout, envelope e ausência de invenção de restrições, mas isso não comprova disponibilidade do fornecedor externo.

## Evidência Amazon Bedrock

Configuração efetiva observada dentro dos containers:

```text
AI_PROVIDER=bedrock
AWS_REGION=us-east-1
BEDROCK_MODEL_ID=deepseek.v3.2
```

As credenciais foram obtidas temporariamente pelo perfil AWS `bedrock` através do script existente e não foram registradas no relatório.

O smoke executou uma análise real pelo backend com o modelo DeepSeek, mas observou:

```text
analysis=COMPLETED
explanation=UNAVAILABLE
provider=
```

O log sanitizado do serviço Python registrou:

```text
Bedrock explanation unavailable or invalid
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

1. Resposta DeepSeek ainda não compatível com o contrato aplicado

   - Arquivo/configuração: `document-intelligence-python/app/main.py` e prompt do adaptador.
   - Severidade: alta.
   - Impacto: explicação fica `UNAVAILABLE`; hipóteses e provider não chegam ao dossiê.
   - Correção mínima: observar a resposta estruturada do modelo em execução autorizada e ajustar o prompt/schema necessário, sem fallback MOCK.

2. E2E principal incompleto

   - Arquivo/configuração: smoke backend-only.
   - Severidade: média.
   - Impacto: não comprova simultaneamente Bedrock válido, decisão humana e auditoria final.
   - Correção mínima: corrigir a resposta DeepSeek e repetir a jornada completa.

## Veredito

**NÃO PRONTA.**

A infraestrutura local e os testes automatizados estão saudáveis, mas a definição de pronto exige comprovar simultaneamente Consult Center real e Bedrock real com resposta estruturada válida. O primeiro está BLOQUEADO por credencial ausente e o segundo FALHOU na validação da resposta observada.
