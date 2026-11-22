# Contratos de exemplo — NÃO são endpoints já implementados

**Regra:** adaptar nomenclatura ao OpenAPI/repositório existente antes de codificar. Em especial, manter os endpoints base do Blueprint V3 (`/api/v1/proposals/{id}/documents`, `.../analyses`, `.../dossier`, `.../decisions`), em vez de criar rotas paralelas sem justificativa.

## 1. Resultado Python de extração (exemplo evolutivo)

```json
{
  "contractVersion": "2.0",
  "analysisId": "ANL-001",
  "documentResults": [
    {
      "documentId": "DOC-PAY-08",
      "documentVersion": 1,
      "type": "PAYSLIP",
      "fields": [
        {"fieldName":"competence","value":"2026-08","extractionStatus":"EXTRACTED","evidenceRef":"DOC-PAY-08:v1:p1:competence"},
        {"fieldName":"netSalary","value":"7300.00","currency":"BRL","extractionStatus":"EXTRACTED","evidenceRef":"DOC-PAY-08:v1:p1:netSalary"}
      ]
    },
    {
      "documentId": "DOC-BANK-08",
      "documentVersion": 1,
      "type": "BANK_STATEMENT",
      "period":{"from":"2026-08-01","to":"2026-08-31"},
      "transactions":[
        {"transactionId":"TX-001","postedAt":"2026-08-30","direction":"CREDIT","amount":"7300.00","currency":"BRL","descriptionOriginal":"CREDITO SALARIO DEMO","classification":"POSSIBLE_SALARY","evidenceRef":"DOC-BANK-08:v1:p1:tx001"}
      ]
    }
  ],
  "errors": []
}
```

Novas variantes de schema devem passar por validação `contractVersion` e testes Java ↔ Python; não quebrar consumidores V1 silenciosamente.

## 2. BureauReport normalizado (exemplo MOCK — não é resposta real de fornecedor)

```json
{
  "provider":"FIXTURE",
  "product":"CREDIT_BUREAU_PF_DEMO",
  "environment":"MOCK",
  "status":"AVAILABLE",
  "consultedAt":"2026-11-20T20:00:00Z",
  "score":{"value":640,"min":0,"max":1000,"model":"SYNTHETIC_DEMO"},
  "restrictions":{"count":1,"entries":[]},
  "limitations":["Dados fictícios; não realizar decisões reais"]
}
```

## 3. CreditAssessment (core Java, contrato indicativo)

```json
{
  "analysisId":"ANL-001",
  "policyVersion":"CREDIT_POLICY_DEMO_2.0",
  "snapshotId":"SNP-001",
  "financialProfile":{
    "income":{"averageNetMonthly":"8000.00","currency":"BRL","validPeriods":3},
    "reconciliation":{"salaryPeriodsMatched":3,"salaryPeriodsExpected":3,"unknownCredits":1},
    "obligations":{"additionalMonthlyPayments":"1500.00","deduplicationStatus":"VERIFIED"}
  },
  "capacity":{"maxCommitmentRatio":"0.35","maxTotalInstallments":"2800.00","maxNewInstallment":"1300.00"},
  "simulation":{"interestRateMonthly":"0.02","termMonths":36,"estimatedPrincipal":"33135.50","pricingSource":"MANUAL_DEMO"},
  "bureau":{"status":"NOT_REQUESTED"},
  "findings":[
    {"code":"SALARY_RECONCILIATION","result":"PASS","evidenceRefs":["DOC-PAY-08:v1:p1:netSalary","DOC-BANK-08:v1:p1:tx001"]}
  ],
  "limitations":["Simulação sem CET/IOF/tarifas e sem score de inadimplência"],
  "aiExplanationStatus":"NOT_REQUESTED"
}
```

Os números no exemplo são apenas ilustrativos e devem ser recalculados/validados com a implementação Price e política de arredondamento; nunca usar valores ilustrativos como oráculo de teste sem cálculo independente.

## 4. APIs REST mínimas

Preferência: **reaproveitar as rotas V3** e acrescentar somente dados opcionais ou uma rota específica de simulação se necessário:

```text
POST /api/v1/proposals                    (existente)
POST /api/v1/proposals/{id}/documents     (existente; metadata type/competence/period)
POST /api/v1/proposals/{id}/analyses      (existente; Idempotency-Key)
GET  /api/v1/proposals/{id}/analyses/{analysisId} (existente; findings + assessment)
GET  /api/v1/proposals/{id}/dossier       (existente; assessment + explanation + source statuses)
POST /api/v1/proposals/{id}/simulations   (NOVO somente se nenhuma rota existente comportar)
POST /api/v1/proposals/{id}/bureau-consultations (NOVO opcional; sintético/sandbox, autorizado)
```

`POST` de simulação informa `analysisId`, taxa, prazo e valor solicitado opcional; retorna cálculo sem criar decisão creditícia. Bureau não é acionado em GET. Erros seguem V3: 400, 401/403, 404, 409, 413, 422, 429, 502/503/504; logging com `X-Correlation-ID`.

## 5. Contrato da explicação por Bedrock

Entrada tipada para Python com `assessmentVersion`, `summaryMetrics`, `findings`, `policyReferences`, `evidenceReferences`, `limitations`, `correlationId`. Saída com `summary`, `explanations[]` vinculadas a `findingCode`, `policyReferences[]`, `evidenceReferences[]`, `limitations[]`; validar contra conjuntos fornecidos. **Nunca** aceitar mudanças em `maxNewInstallment`, `estimatedPrincipal`, `restrictionCount`, `policyVersion` ou `approved` vindas do LLM.
