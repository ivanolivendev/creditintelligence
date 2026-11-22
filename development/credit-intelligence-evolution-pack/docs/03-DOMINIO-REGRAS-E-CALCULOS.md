# 03 — Domínio financeiro, regras e cálculos

## Objetivo do núcleo

Receber `FinancialProfile` validado + `CreditPolicy` versionada + `LoanScenario` e devolver `CreditAssessment` **reprodutível**: indicadores, findings com códigos e evidências, capacidade de prestação, simulações, bloqueios e motivos de revisão. **Não retorna `approved=true` como decisão final.**

## Modelo de domínio (mínimo e tipado)

```text
FinancialProfile
  proposalId, analysisId, currency
  incomePeriods[]: month, gross, net, fixedPart?, variablePart?, status, evidenceRefs[]
  bankPeriods[]: from, to, openingBalance?, closingBalance?, transactions[]
  salaryMatches[]: incomePeriodRef, transactionRefs[], difference, status
  obligations[]: source, monthlyAmount, includedInNetPay?, duplicateGroup?, status, evidenceRefs[]
  bureau?: BureauReport (sourceStatus, environment, provider, product, score?, scale?, restrictions?, refs)
  quality: missingFields[], ambiguousFields[], limitations[]
CreditPolicy
  version, effectiveFrom, checksum
  debtCommitmentLimitRatio (demo, ex.: 0.35)
  incomeBasis (e.g. AVG_VALID_NET_3_MONTHS)
  salaryMatchToleranceAbsolute, salaryDateWindowDays
  allowSimulationWithMissingBureau=true
  treatmentOfVariableIncome, missing/ambiguous data
LoanScenario
  principalRequested?, termMonths, monthlyInterestRate, currency, pricingSource (MANUAL/REFERENCE)
CreditAssessment
  inputFingerprint, policyVersion, ruleVersion
  metrics, findings[], evidenceRefs[], loanSimulation?, limitations[]
  reviewStatus (READY_FOR_REVIEW/MORE_INFO_REQUIRED)
```

Use `BigDecimal` para dinheiro e razões; política explícita para precisão/arredondamento. Não usar `double` em resultado financeiro persistido. Identificadores/document hashes não são evidência de autenticidade.

## Regras P0

| Código | Entrada | Resultado | Importância |
|---|---|---|---|
| `REQUIRED_PAYSLIPS` | tipos + competências | `PASS/WARNING/NOT_EVALUATED` | séries completas |
| `REQUIRED_BANK_STATEMENTS` | datas + períodos | `PASS/WARNING/NOT_EVALUATED` | cobertura adequada |
| `INCOME_AGGREGATION` | líquidos válidos, rubricas | média, mínimo, máximo, variação | renda base verificável |
| `INCOME_STABILITY` | 3 competências | variação e achado; limiar na política | renda variável não vira fixa |
| `SALARY_RECONCILIATION` | líquido + créditos classificados | `MATCH/PARTIAL/UNMATCHED/UNKNOWN` por mês | cruzar fontes |
| `OBLIGATION_DEDUPLICATION` | descontos folha + extrato + declaradas | obrigações únicas ou `NEEDS_REVIEW` | não duplicar dívidas |
| `DEBT_COMMITMENT` | renda referência + obrigações adicionais | razão e limitação | capacidade |
| `PAYMENT_CAPACITY` | limite fictício + obrigações | teto hipotético da nova prestação | simulação |
| `BUREAU_CONTEXT` (P1) | resposta externa | alerta informativo/pedido de revisão, nunca score calculado | contraste externo |

**O que não implementar:** score de inadimplência proprietário, risco de fraude afirmado só por divergência, regressão treinada em dados sintéticos ou conversão de score externo em PD.

## Matemática

### Média salarial

`averageNetIncome = sum(net_valid_periods) / count(valid_periods)`; resultado inclui quantidade efetiva, meses esperados, status e evidências. **Regra default da PoC:** exigir os 3 meses para cálculo classificado `VERIFIED`; com menos, permitir no máximo `PROVISIONAL` (se aprovado por política demonstrativa) ou `NOT_EVALUATED`. Renda de referência padrão: média líquida de 3 meses válidos; valor é hipótese de engenharia, não regra bancária.

### Capacidade teórica

```text
maxTotalInstallments = max(0, referenceMonthlyIncome * policy.commitmentLimitRatio)
newInstallmentCapacity = max(0, maxTotalInstallments - additionalExistingMonthlyPayments)
commitmentRatio = (additionalExistingMonthlyPayments + simulatedNewInstallment) / referenceMonthlyIncome
```

**Nuance obrigatória:** se o perfil usa **renda líquida após desconto consignado**, o valor consignado **não pode ser subtraído novamente** do limite calculado sobre esse líquido. Criar `includedInNetPay` e separar `additionalExistingMonthlyPayments`. Definir essa convenção na política e exibí-la. Diante de incerteza sobre duplicidade, emitir finding de revisão, não apresentar 'limite confiável'. Despesas essenciais e saldos não são equivalentes a parcelas; alertas podem ser produzidos, mas sem assumir que todo saldo é dinheiro disponível.

### Simulação Price

Para valor financiado `PV`, taxa mensal decimal `r` e número de parcelas `n`:

```text
PMT = PV * r / (1 - (1 + r)^(-n))       quando r > 0
PMT = PV / n                            quando r = 0
PV  = PMT * (1 - (1 + r)^(-n)) / r       quando r > 0
PV  = PMT * n                           quando r = 0
```

Validações: `n > 0`, taxa `>= 0`, montantes `>= 0`, moeda consistente, precisão suficiente para exponenciação, arredondar só ao fim conforme política e testes. Sem CET, IOF, seguro e tarifas → resultado deve dizer **simulação parcial**; nunca chamar `oferta bancária`.

### Exemplo conferível

Renda de referência 8000 BRL; parcelas adicionais 1500 BRL; comprometimento fictício 35%; prazo 36 meses, taxa 2% a.m.: limite de prestações totais 2800 BRL; nova parcela 1300 BRL; principal Price ~33135,50 BRL (arredondamento conforme precisão adotada). Mostrar fórmula e política usada; não afirmar que uma instituição aprovaria.

## Classificação de transações: mínimo viável

1. Extrair lançamentos originais com sinal `CREDIT/DEBIT`, valor, data, descrição, documento/página e status.
2. Classificar apenas padrões de fixture conhecidos: `POSSIBLE_SALARY`, `TRANSFER`, `RECURRING_PAYMENT`, `UNKNOWN`.
3. `POSSIBLE_SALARY` exige correspondência contextual com contracheque e data/tolerância, não transforma todo crédito em renda.
4. Conciliação aceita possíveis pagamentos fracionados (somar transações elegíveis dentro da janela com regra documentada), mas evita correspondência múltipla de um mesmo lançamento.
5. Salvar relação entre itens conciliados e valores brutos para revisão.
6. Não afirmar origem econômica de Pix só pela descrição.

## Findings, estados e UI

- `PASS`: regra avaliada e critérios atendidos.
- `WARNING`: divergência/ressalva verificável; exige leitura.
- `FAIL`: regra demonstrativa não atendida; não é recusa bancária automática.
- `NOT_EVALUATED`: informação insuficiente, ambígua ou fonte indisponível.
- Cada finding traz `ruleCode`, `ruleVersion`, `policyVersion`, `details`, `evidenceRefs` e status.
- Dossiê separa claramente **fatos extraídos**, **cálculos**, **dados externos**, **texto de IA** e **ato humano**.

## Política e versão

Criar `CREDIT_POLICY_DEMO_2.0` ou nova versão equivalente, **sem sobrescrever** V3 `POLICY-1.0`. Guardar checksum, configuração executável e texto explicativo coerentes (V3 seções 19 e INV-09). Os limites são fictícios e configuráveis, não padrões legais dos bancos.
