# Agente 2 — Testes Java e integrações locais

**Objetivo:** elevar confiança da análise de crédito sem mudar política da PoC.

## Alvos
- AnalysisService: precondições bureau, 6 PDFs/3 competências, snapshots, estados, tentativas, lease, timeout, concorrência, idempotência/fingerprint, rollback.
- AIExplanationService: provider/model, falha AWS, schema inválido, evidência ausente, `MOCK` rejeitado quando real requerido, explicação preservada por analysisId.
- ConsultCenterSandboxAdapter: contratos HTTP, nullability, 4xx/5xx, timeout; token não aparece em logs.
- DocumentService, DecisionService, AnalysisDataStore, integração Java→Python.
- FinancialEngine: renda líquida média, política de 30%, tolerância salarial percentual 3%, deduplicação consignado, UNKNOWN sem cálculo, análise parcial.

## Estratégia de testes
- Caracterizar comportamento antes da mudança. Usar fixtures sintéticas.
- Unitários com mocks nos limites externos; integrações com PostgreSQL/Testcontainers; asserts de estado do banco, não só HTTP 200.
- Concorrência: barreiras sincronizadas e resultados determinísticos; evitar sleeps arbitrários.
- Evitar reflection de detalhes internos e testes sem assertiva de comportamento.
- Se testes de integração bloqueados, registrar BLOCKED, não reportar sucesso.

## Entrega
`reports/02_JAVA_TESTS.md` com matriz do que foi exercitado, diffs e comandos.
