# Plano de execução — PIT de 33% para 70–80%

## Objetivo

Elevar o mutation score Java de 33% para a faixa de 70–80%, protegendo regras e invariantes reais da POC. O objetivo não é fazer o número subir artificialmente: cada mutação deve ser morta por uma asserção que verifique comportamento observável ou ser classificada com justificativa técnica.

## Baseline confirmada

Última execução válida:

- 640 mutações geradas;
- 208 mortas;
- 53 sobreviventes;
- 380 sem cobertura;
- 1 timeout;
- mutation score: **33%**;
- test strength: **80%**;
- 57 testes unitários Java aprovados;
- 21 testes de integração aprovados.

## Regras de trabalho

- Não excluir mutações para melhorar o indicador.
- Não escrever testes que apenas executam métodos.
- Não testar detalhes privados ou implementação interna.
- Não alterar regras de negócio para acomodar o PIT.
- Preferir testes unitários para domínio e testes de integração para persistência/transações.
- Reexecutar unitários, integração e PIT após cada lote coerente.
- Registrar mutações justificadamente equivalentes, não determinísticas ou fora do escopo sandbox.

## Fase 1 — Reproduzir e classificar a baseline

Executar:

```powershell
docker compose --profile test run --rm backend-test mvn -B -ntp "-Djacoco.skip=true" -Pmutation org.pitest:pitest-maven:mutationCoverage
```

Extrair do relatório PIT:

1. classes sem cobertura;
2. classes com sobreviventes;
3. mutações por operador;
4. mutações com timeout;
5. concentração por módulo e por camada.

Entregável: tabela com `classe`, `linha`, `mutação`, `status`, `comportamento esperado`, `teste que deverá matar`.

## Fase 2 — Domínio determinístico

Prioridade máxima: `FinancialEngine`, `IncomeConsistencyRule` e validadores puros.

Casos obrigatórios:

- três períodos completos e distintos;
- período ausente, duplicado ou com valor nulo;
- renda estável, limítrofe e acima do limite;
- crédito salarial correspondente, divergente, ausente e desconhecido;
- créditos salariais semanticamente duplicados;
- obrigações consignadas, adicionais e incertas;
- renda zero sem divisão por zero;
- taxa zero, taxa positiva, prazo/moeda/principal inválidos;
- evidência e limitações preservadas.

Critério: cada teste valida status, valores financeiros, findings, limitações e referências de evidência quando aplicável.

Meta: eliminar a maior parte das mutações sem cobertura do domínio e obter pelo menos 60% nas classes determinísticas.

## Fase 3 — Aplicação e contratos

Cobrir através das portas de aplicação:

### Propostas e políticas

- identificador sintético reutilizado com mesmo nome;
- identificador associado a outro nome;
- geração de identificador;
- política ausente, checksum inválido, JSON inválido e documentos obrigatórios ausentes.

### Documentos

- PDF vazio, content type incorreto, assinatura inválida e PDF sem texto;
- payload acima de 10 MiB;
- período inválido ou usado em documento não financeiro;
- nova versão por tipo e por período;
- snapshot seleciona a versão mais recente;
- falha de storage e rollback sem metadata falsa.

### Decisão

- usuário sem `ROLE_ANALYST`;
- proposta finalizada;
- análise incompleta ou snapshot divergente;
- documento ou bureau alterado após análise;
- decisões aprovada, rejeitada e solicitação de informação;
- justificativa e auditoria registradas corretamente.

## Fase 4 — Análise, idempotência e concorrência

Cobrir:

- mesma chave e fingerprint reutilizam análise;
- mesma chave com fingerprint diferente retorna conflito;
- lease expirado gera nova tentativa;
- tentativa antiga não conclui análise nova;
- falha Python retryable e resposta inválida;
- snapshot não é apagado em falha externa;
- resultado tardio não sobrescreve findings;
- política, bureau, snapshot e hash permanecem vinculados.

Usar adapters falsos nas unidades e Testcontainers nos cenários transacionais/concurrentes.

## Fase 5 — Timeout e estabilidade

Investigar a mutação com timeout no relatório PIT, executar o teste isoladamente e eliminar esperas não determinísticas. Se o timeout for inerente a uma integração real, documentar a causa e manter timeout explícito somente com justificativa.

## Fase 6 — Metas progressivas

| Marco | Meta | Condição |
|---|---:|---|
| M0 | 33% | baseline atual |
| M1 | 45% | domínio principal coberto |
| M2 | 55% | documentos, políticas e propostas |
| M3 | 65% | decisões e análise crítica |
| M4 | 70% | execução estável e sem mutações críticas abertas |
| M5 | 70–80% | sobreviventes revisados e justificados |

O `mutationThreshold` só será elevado após o marco correspondente passar repetidamente.

## Verificação por marco

```powershell
docker compose --profile test run --rm backend-test mvn -B -ntp test
docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify
docker compose --profile test run --rm backend-test mvn -B -ntp "-Djacoco.skip=true" -Pmutation org.pitest:pitest-maven:mutationCoverage
git diff --check
docker compose config --quiet
```

## Critérios de conclusão

- score entre 70% e 80%;
- nenhum timeout sem causa documentada;
- sobreviventes críticos mortos ou justificados;
- mutações sem cobertura restantes fora do comportamento relevante da POC e documentadas;
- unitários, integração, ArchUnit e Checkstyle aprovados;
- nenhuma regra financeira enfraquecida;
- relatório de auditoria atualizado com antes/depois.

## Ordem prática

1. Gerar baseline detalhada.
2. Cobrir domínio.
3. Medir PIT.
4. Cobrir documentos e políticas.
5. Medir PIT.
6. Cobrir decisões.
7. Medir PIT.
8. Cobrir idempotência, leases e late results.
9. Investigar timeout.
10. Executar suíte completa e elevar o gate para o maior marco comprovado.
