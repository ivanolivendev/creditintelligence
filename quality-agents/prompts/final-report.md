# Prompt para Codex — relatório final de qualidade

Gere `quality-agents/reports/FINAL_QUALITY_REPORT.md` contendo:

1. Resumo executivo e veredito READY / READY_WITH_RESERVATIONS / NOT_READY / BLOCKED.
2. Revisão Git, environment e comandos exatamente executados (não sugeridos).
3. Baseline comparável antes/depois: testes Java unit/IT, Python, frontend, cobertura linhas/branches por módulo, sem cobertura global inventada.
4. Matriz de cenários de `plans/test-matrix.md`: esperado, observado, status, referência real.
5. Evidências do E2E com Consult Center sandbox e Bedrock REAL (sem revelar tokens nem conteúdo sensível).
6. Problemas críticos P0, altos P1 e melhoras opcionais P2; indicar se bloqueia demo.
7. Arquivos modificados e resumo do diff; verificar que produção não sofreu regressão.
8. Dependências/scanners: resultados e limitações (DNS, registry, AWS, Docker etc).
9. Lista priorizada e mínima do que falta; não recomendar mudanças desproporcionais à PoC.

Conferir números e denominadores; não usar percentuais de confiabilidade sem metodologia. Não transformar testes BLOCKED em PASS. Basear afirmações exclusivamente em evidências produzidas nesta execução.
