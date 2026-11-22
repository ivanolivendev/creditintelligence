# 06 — Segurança, limitações, ADRs e demonstração

## Segurança mínima para PoC

- Apenas **dados sintéticos** em PDFs, usuários e CPFs de teste; sem CPFs reais de terceiros.
- Spring Security protege upload, dossiê, simulações persistidas, consulta bureau e decisão humana; identidade do analista vem da sessão/token (V3 INV-12).
- Segredos da API e AWS por env/secrets, nunca em repositório ou browser. Não imprimir tokens, CPF completo, dados financeiros completos, PDFs, prompts ou payload externo em logs.
- Aplicar validação de tipo/tamanho/assinatura de PDF e storage privado conforme V3.
- Texto de documentos é **conteúdo não confiável**, não comando; proteger prompts contra instruções maliciosas.
- Guardar apenas mínimo necessário de resposta externa; controlar retenção e acesso. Para uso real é necessária avaliação específica da LGPD, finalidade, base legal e contrato do provedor.
- Nunca chamar a API de bureau com CPF real por conveniência de demo. Sandbox não equivale a consulta real ao histórico de pessoa.

## Limitações que precisam aparecer claramente

1. Capacidade de pagamento é **simulação baseada em documentos fornecidos**, não teste estatístico de inadimplência.
2. Scores externos têm escala e modelo do fornecedor; um score de sandbox é fictício. Não transformá-lo em PD.
3. Documentos podem ser falsos ou incompletos; coerência interna **não é autenticação bancária**.
4. Entradas bancárias não são necessariamente salário ou renda; Pix não classificado não aumenta renda por padrão.
5. Comprometimento depende de obrigações conhecidas e regra fictícia; despesas essenciais precisam de revisão.
6. Simulação Price sem CET, IOF, seguros e tarifas não é oferta nem custo total do empréstimo.
7. OCR genérico, layouts não suportados, SCR, Open Finance e consultas reais não fazem parte do P0.
8. Não afirmar que foi feito na AWS/provedor remoto sem logs/teste de fato.

## ADRs sugeridas (registrar somente depois da inspeção)

- ADR-E01: Core financeiro determinístico no Java, extração/Bedrock no Python — por integridade, testabilidade e controle.
- ADR-E02: Normalização de fontes e `FinancialProfile` tipado — evita dependência dos DTOs de fornecedor.
- ADR-E03: Uma porta de bureau + um adapter externo + fixture — desacopla sem overengineering.
- ADR-E04: Política versionada e snapshots de documentos e bureau — reprodutibilidade.
- ADR-E05: Consulta bureau é enriquecimento opcional — indisponibilidade não destrói resultado documental.
- ADR-E06: Sem fila e sem OCR universal — reduzir risco de entrega em dois dias.

Cada ADR deve conter `Contexto → alternativas → decisão → motivação → consequências → revisitar quando`, tal como o Blueprint V3 seção 34.

## Demo sugerida: 10–12 minutos

1. **Problema (1min):** PDFs isolados e dados externos demandam consolidação para revisão de crédito.
2. **Arquitetura (2min):** camadas hexagonais, portas, Java/Python, Bedrock e bureau.
3. **Caso consistente (3min):** upload 3+3; extração com página, conciliação; capacidade calculada; dossiê.
4. **API externa (1min):** CPF sintético, sandbox remoto se verificado; mostrar status, provider e ambiente; se fixture, dizer MOCK.
5. **Inconsistência (2min):** alterar um valor de fixture; novo snapshot → finding e revisão.
6. **Resiliência (1min):** forçar timeout/erro bureau; relatório documental permanece, status externo `UNAVAILABLE`.
7. **Trade-offs (1min):** por que não score preditivo, sem produção automática, por que sem fila neste estágio.

## Frase de apresentação precisa

> “O Credit Intelligence recebe documentos financeiros sintéticos e consulta, quando habilitado, uma fonte de bureau em sandbox por meio de adapter desacoplado. Os dados são normalizados, conciliados e avaliados por regras determinísticas versionadas em Java. O Bedrock explica os achados sem substituir cálculos nem aprovar crédito. A aplicação produz um dossiê rastreável para revisão humana.”

Não dizer “integramos com Serasa” se na verdade o HTTP foi direcionado a integrador/sandbox ou fixture local. Não usar o rótulo “score de risco calculado pelo sistema” para um índice de completude documental.
