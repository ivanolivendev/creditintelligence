# Acessibilidade — requisitos de reconstrução

**Alvo:** WCAG 2.2 AA como critério proposto de projeto; conformidade exige auditoria, não é atestada por ferramenta automática isolada.

## Semântica e interação
Use elementos HTML nativos; títulos hierárquicos, landmarks, labels persistentes, instruções/erros associados, mensagens dinâmicas anunciáveis, botões para ações e links para navegação. Evite ARIA redundante. Fluxos completos devem funcionar só com teclado; foco sempre visível, ordenado, contido corretamente em dialogs e restaurado no fechamento.

## Percepção
Texto normal mínimo 4.5:1 e texto grande 3:1; componentes/indicadores não textuais pertinentes 3:1; links distinguíveis sem depender só da cor; erros com texto e orientação. Gradiente deve ser avaliado no pior ponto com texto sobreposto. Respeite preferência de redução de movimento.

## Responsividade
Layout legível em 320 px e sem perda funcional a 200% de zoom; avaliar reflow, espaçamento de controles, targets e orientação. Critérios específicos de tamanho de alvo da WCAG 2.2 precisam ser conferidos no contexto, não substituídos por suposição universal.

## Validação
1. Auditoria automática com ferramenta disponível (registrar versão, URL e limites).
2. Teclado manual em cada jornada crítica: Tab, Shift+Tab, Enter, Espaço, Escape, setas quando aplicáveis.
3. Contraste com pares reais e estados hover/focus/disabled; verificação de mensagens de erro.
4. Leitor de tela **quando ambiente permitir**, indicando navegador e leitor; caso contrário `NÃO EXECUTADO`.
5. Registro por defeito: caminho, passo, esperado, observado, severidade, evidência e correção.

## Atenção
Imagens decorativas abstratas devem ter alternativa vazia; gráficos informativos precisam equivalente textual/tabela. Não esconder ações atrás de hover. Não usar placeholders como único rótulo.
