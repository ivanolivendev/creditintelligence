# 04 — Extração documental e integração externa

## PDF: tipos e contratos

Manter upload V3 (assinatura PDF, limite, SHA-256, objeto privado, versão; seções 16–17). Estender os tipos para `PAYSLIP` e `BANK_STATEMENT`; exigir competência/período e rejeitar ou sinalizar sobreposição inconsistente.

**Contracheque P0:** `personName`, `employer`, `competence`, `grossSalary`, `netSalary`, `discounts[]` (descrição/valor/tipo se conhecido), `variableComponents[]` quando visíveis; cada campo com `extractionStatus`, `rawText`, `normalizedValue`, `currency`, `evidenceRef`. Não inferir identificação verdadeira do empregador pela aparência do PDF.

**Extrato P0:** `bankLabel` textual, `statementPeriod`, `openingBalance?`, `closingBalance?`, `transactions[]` (`postedAt`, `descriptionOriginal`, `amount`, `direction`, `balanceAfter?`, `evidenceRef`). Preferir 1 layout sintético controlado; se o documento não for parseável, retornar `INVALID/UNSUPPORTED_LAYOUT`, sem fabricar movimentações.

**Conciliação:** jamais comparar salário bruto com depósito líquido; trabalhar com competência, data real de pagamento e tolerância explícita. Corrigir manualmente `UNKNOWN` com evento auditado, não sobrescrever extração original.

## Bureau: arquitetura anticorrupção

```text
CreditBureauPort (contrato interno)
  ├─ ConsultCenterSandboxAdapter (condicional: só após verificação)
  └─ FixtureBureauAdapter (teste local, MOCK explícito)

DTO fornecedor --(validator + mapper)--> BureauReport
```

**Fornecedor da PoC:** Consult Center é candidato previamente discutido, **não uma dependência assumida nem disponibilidade garantida**. Antes de implementar o cliente, verificar documentação e ambiente atuais, endpoint, método, CPF de homologação publicado, tipo de autenticação, campos, limites, política de teste e se o produto realmente retorna score/restrições; não copiar literalmente credenciais de mensagens antigas sem validação. Se o acesso exigir cadastro/contrato ou falhar, utilizar fixture.

**Modelo mínimo do retorno interno:**

```text
BureauReport {
  provider, product, environment: SANDBOX|PRODUCTION|MOCK,
  status: AVAILABLE|UNAVAILABLE|NOT_REQUESTED|INVALID_RESPONSE,
  consultedAt?, responseVersion?, referenceId?,
  score?: {value, min?, max?, model?, referenceDate?},
  restrictions?: {count?, entries?},
  evidenceRefs?, limitations[]
}
```

- Campos ausentes permanecem `null/unknown`, jamais recebem zero.
- `score` exige escala/modelo; não comparar scores de fornecedores como se fossem intercambiáveis.
- Não pressupor que bureau fornece todas as dívidas ou PD; documentar a cobertura do produto contratado.
- Dados externos trazem `source=EXTERNAL`, `environment`, `provider` e momento da consulta. Apresentar badge `SANDBOX` em UI e relatório.
- Mapeador precisa validar esquema mínimo, tipos e ranges; respostas HTTP 200 com body inesperado = `INVALID_RESPONSE`.

## HTTP e segurança do adapter

- Implementar cliente Java existente (`RestClient`/`WebClient`/Feign já presente; não introduzir dependência sem motivo).
- Credencial por variável de ambiente/secret; **nunca frontend**, código ou logs.
- Timeout conexão e leitura explícitos; retries limitados somente se seguro e necessário; rate limit 429 respeita `Retry-After` quando aplicável.
- Erros 401/403 não fazem retry e configuram erro de auth; 5xx/timeouts → indisponível; body inválido → resposta inválida.
- Logs: provider, duração, status HTTP, correlationId, analysisId; **não** CPF em claro, payload bruto nem token.
- Nunca chamar bureau automaticamente em toda atualização de UI ou polling; consulta é ação de negócio rastreável e com propósito.
- **Dados reais:** CPF no cadastro, por si só, não equivale a autorização para consulta. Para PoC usar somente CPF sintético; operação real dependeria de contrato, finalidade, base legal, política do fornecedor, privacidade e autorização apropriada.

## Critérios de aceite da integração externa

1. Chamada real ao ambiente de homologação com CPF sintético e status/evidência observáveis; documentação atual e credenciais legítimas verificadas.
2. DTO do fornecedor restrito ao adapter; core usa somente `BureauReport`.
3. O mesmo caso de uso roda com FixtureAdapter sem mudanças no motor.
4. Erro 502, 401 e timeout jamais se transformam em score 0 nem em `sem restrições`.
5. A ausência do bureau mantém a análise documental funcional e indica resultado parcial.
6. O README distingue `sandbox remoto` de `fixture local`, sem alegar consulta real de pessoa.

## Principais riscos de prazo

PDFs de diferentes bancos/empregadores e OCR geral são complexos. Não prometer leitura universal: limitar formato digital e fixtures. Consulta sandbox pode depender de autorização e de contrato atual; não bloquear P0 por esse motivo.
