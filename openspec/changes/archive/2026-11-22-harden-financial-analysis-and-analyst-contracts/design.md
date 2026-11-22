# Design

## Financial integrity

`FinancialEngine` will preserve the existing distinction between absent, invalid, and factual values. It will expose completeness alongside aggregate values and will not allow `IncomeConsistencyRule` to receive a factual reference income when the required period set is incomplete. The assessment remains `MORE_INFO_REQUIRED` and the finding becomes `NOT_EVALUATED` or the repository's equivalent uncertainty result.

Salary reconciliation will canonicalize eligible credits by period, amount, currency, normalized description, and transaction date. A repeated semantic key will be recorded as duplicate/uncertain rather than summed. Technical transaction IDs remain useful for exact duplicates but are not sufficient for the new guard.

## Analyst workspace

The frontend readiness helper will compare the set of payslip periods with the set of bank-statement periods and require the configured count of matching periods. The UI will show the missing or mismatched periods. Provider labels will be derived from the persisted analysis provider/status; static copy will say `MOCK` unless the runtime explicitly reports Bedrock. Error containers will use `role="alert"` for actionable failures.

## Contract

The OpenAPI document will be updated from the executable controllers, including analysis status, dossier, bureau latest, proposal detail, analysis request body, and response schemas used by the frontend. No new endpoint is introduced.

## Application boundary

Add the smallest analysis persistence port needed by `AnalysisService`, implemented by the existing JDBC adapter. The port will cover the operations the use case already needs; it will not attempt a broad repository rewrite. Existing transaction and lease semantics remain unchanged.

## Security handling

Do not print or commit `.env` values. Keep the file ignored. Add/retain safe placeholders and operator instructions in `.env.example` and documentation. If active external credentials are confirmed, rotate them through the provider separately and record only the outcome, never the secret.
