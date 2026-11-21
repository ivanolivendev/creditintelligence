# Casos de teste de referência — somente dados sintéticos

## Caso A — consistente

Contracheques com competências `2026-08`, `2026-09`, `2026-10`: salários líquidos `[7300,8500,7400]` BRL, com rubricas definidas nas fixtures; média `7733.33` (duas casas). Extratos nos mesmos períodos possuem depósitos salarialmente compatíveis nas janelas definidas pela policy. Há 1 crédito adicional (`4000`) de natureza `UNKNOWN`, que **não pode** incrementar renda. Obrigações adicionais declaradas podem ser zero ou um valor específico preenchido no teste. Gabarito: 3 períodos válidos, 3 matches, 1 unknown, renda igual à média dos contracheques e assessment explicável.

## Caso B — divergência

No contracheque de outubro, líquido `7400`; no extrato, identificar somente `6900` como crédito potencial de salário. Sem outro pagamento relacionado. Gabarito: divergência `500` (tolerância demonstrativa abaixo de 500), finding `SALARY_RECONCILIATION:WARNING`, evidências de ambos os documentos e orientação de revisão; **não** presumir que o cliente fraudou nada.

## Caso C — renda incompleta

Agosto e setembro válidos; outubro com líquido ausente/ambíguo. Gabarito: status de renda `NOT_EVALUATED` ou `PROVISIONAL` se política permitir, nunca média de três meses como VERIFIED. Sem simulação confiável por padrão.

## Caso D — obrigação duplicada

Consignado de 300 já descontado do salário líquido e débito de mesmo contrato identificado no extrato; gabarito: não subtrair 300 novamente do líquido e não somar duas entradas de obrigação. Se vínculo incerto, `NEEDS_REVIEW`.

## Caso E — bureau sandbox

CPF de teste **somente** conforme documentação atual do fornecedor. Gabarito: JSON válido mapeado para `BureauReport` com `environment=SANDBOX`, score opcional com escala/modelo, restrições opcionais; não exigir campos que o provider não retorna.

## Caso F — falha externa

Simular 502/timeout, mantendo PDFs válidos. Gabarito: `bureau.status=UNAVAILABLE`; análise documental disponível; nenhuma falsa conclusão “sem restrições”; explicação com limitação.

## Caso G — regras matemáticas

Renda referência `8000`, obrigações adicionais `1500`, limite fictício `0.35`, taxa `0.02` ao mês, prazo `36`: máximas parcelas totais `2800`, capacidade nova `1300`, PV pela fórmula Price ~`33135,50` (verificar valor exato e arredondamento em teste independente). Com taxa 0: PV de `1300*36=46800`.

## Recomendação de fixtures

Crie PDFs exclusivamente sintéticos, com título visível `DEMONSTRAÇÃO — DADOS FICTÍCIOS`, formatos digitais com texto selecionável, competência, páginas e conteúdo estável. Armazene gabaritos estruturados versionados em `fixtures/expected-results`. Não usar extratos pessoais de quem está desenvolvendo o produto.
