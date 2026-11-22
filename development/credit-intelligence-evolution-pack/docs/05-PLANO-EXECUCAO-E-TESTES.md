# 05 — Plano de execução de dois dias e critérios de aceite

## Planejamento realista

Orçamento indicativo **16 horas** de desenvolvimento, condicionado a repositório funcional, PDFs digitais de layouts controlados e integração Bedrock existente em condições de teste. Não constitui promessa. Adotar gates P0/P1, diminuindo escopo se necessário.

| Ordem | Trabalho | Alvo | Saída verificável |
|---|---|---|---|
| 0 | Auditoria do repositório, Compose, migrations, contracts | 1h | inventário e smoke existente |
| 1 | Fixtures de 3 contracheques e 3 extratos, extratores | 4h | campos, páginas, meses e transações corretos |
| 2 | Modelos `FinancialProfile` e mapper | 1h | JSON/objetos tipados e evidências validadas |
| 3 | Engine Java: renda, conciliação, obrigações, simulação | 3h | testes de domínio passando |
| 4 | Integração ciclo `Analysis` + persistência de assessment | 2h | API/dossiê consultáveis |
| 5 | Bedrock explanation no pós-cálculo, UI mínima | 2h | explicação versionada; status real/mock |
| 6 | Bureau sandbox P1 (se acesso verificado) | 2h | chamada HTTP remota, mapper e falha |
| 7 | E2E, bugs, documentação e demo | 1h | roteiro executado e relatório |

Os tempos são **tetos de planejamento aproximados**, não cronometragem obrigatória. Se a extração ultrapassar o planejado, use fixture/um único layout suportado e faça P0 funcionar; o bureau pode permanecer em modo fixture com status explícito.

## Gates obrigatórios

**G0 — baseline:** executar comandos existentes; registrar estado inicial, commits, portas e env. Sem isso, não reescrever.

**G1 — documentos:** 3 PDFs de cada tipo; origem, versões, hash, status e campos extraídos com fixture/gabarito.

**G2 — core:** regras determinísticas em Java; `BigDecimal`; testes de média, variação, duplicação de parcelas e Price; nenhum resultado por LLM.

**G3 — análise:** snapshot, policyVersion e input fingerprint; findings e assessment persistidos; GET dossier funcional.

**G4 — resiliência:** timeout externo, erro Python, Bedrock ausente e idempotência; nenhum estado enganoso.

**G5 — interface:** um usuário demo completa a jornada sem consultar DB/Swagger manualmente; consegue ver valores/evidências e limites.

**G6 — entrega:** README e ADRs refletem o executado; apresentação distingue implementação real, sandbox e fixture local.

Gates em `PASS | PARTIAL | FAIL | BLOCKED`; evidência = comando, ambiente, timestamp, resultado e assertion. Teste escrito mas não rodado = NÃO VALIDADO.

## Matriz mínima de testes

| ID | Teste | Evidência esperada |
|---|---|---|
| T01 | Upload de 3 payslips | competências distintas, hash/versionamento |
| T02 | Upload de 3 statements | períodos, transações e origem recuperáveis |
| T03 | PDF inválido/sem texto | INVALID sem valores fabricados |
| T04 | Campo salarial ausente | NOT_EVALUATED/MISSING, sem inferência LLM |
| T05 | Média 7300,8500,7400 | 7733,33 BRL conforme escala |
| T06 | 3 depósitos correspondentes | 3 matches, sem reutilizar transação |
| T07 | Crédito bancário não salarial | permanece UNKNOWN; não compõe renda |
| T08 | Salário discordante | finding WARNING com docs/página |
| T09 | Consignado em líquido e extrato | não subtrair duas vezes |
| T10 | Obrigações desconhecidas | capacidade qualificada como provisória/sem certeza |
| T11 | Limite 35%, renda 8000, obrigações extras 1500 | limite total 2800; nova parcela 1300 |
| T12 | Price r=0 e r>0 | PV/PMT esperados por fixture independente |
| T13 | Rate/prazo inválidos | 400/422, sem persistência indevida |
| T14 | Mesmo Idempotency-Key/hash | mesmo analysisId; sem duplicidade |
| T15 | Mesmo key com body diferente | 409 explícito |
| T16 | Novo PDF/novo bureau report | novo input fingerprint e versão |
| T17 | Bedrock inválido/indisponível | assessment persistido; IA UNAVAILABLE/INVALID_OUTPUT |
| T18 | Texto malicioso no PDF | não altera regras, política ou comandos |
| T19 | Bureau retorna sandbox | campos normalizados, provider/env visível |
| T20 | Bureau 502/401/timeout | UNAVAILABLE, não `0 restrições` |
| T21 | Acesso sem permissão | 401/403, decisão protegida |
| T22 | Fluxo E2E | proposta → docs → assessment → dossier → revisão |
| T23 | Duas análises simultâneas | sem duplicação, sem resposta tardia sobrepor vigente |

## Critérios finais observáveis

- API responde corretamente a dados sintéticos, outputs versionados, evidências recuperáveis.
- Os cálculos são reproduzidos em testes automatizados independentemente do Bedrock.
- Interface identifica `SANDBOX`, `MOCK` e `REAL` conforme execução observada.
- Consulta externa falha sem derrubar core documental.
- Mudança de documento não reescreve análise histórica.
- Não há promessa enganosa de aprovação, score de inadimplência, consulta real de CPF ou integração com banco.

## Critérios de corte

Se houver menos tempo: 1) preservar extractors de 1 layout cada; 2) simplificar gráficos e UI; 3) desabilitar sandbox e usar fixture rotulada; 4) só então reduzir escopo documental, explicitando parcial. **Nunca** cortar validação de cálculos, ausência de dados ou honestidade sobre mocks.
