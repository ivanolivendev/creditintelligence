# Workflow: reconstrução incremental

## Pré-condição
Aprovação explícita do plano de Fase 4, baseline estável, escopo de arquivos e estratégia de rollback. Sem isso, NÃO iniciar.

## Ordem sugerida (adaptar à stack)
1. Criar tokens sem alterar contratos ou comportamento; compilar e validar semântica de cores.
2. Implementar ou adaptar componentes base de apresentação, com estados/keyboard e testes.
3. Migrar shell/navegação sem alterar route guards ou paths.
4. Migrar uma rota-piloto representativa (formulário + lista + erro) e obter revisão.
5. Expandir por funcionalidades de menor risco até as mais sensíveis; cada incremento deve preservar fluxos de ponta a ponta.
6. Eliminar código visual obsoleto **somente** depois de provar ausência de uso e com aprovação.

## Controle por fatia
`ID | rota | arquivos permitidos | contratos tocados? (deve ser NÃO) | diffs | before/after | testes | resultado | rollback`. Testar login/ACL sempre que navegação, redirecionamento ou estado compartilhado forem tocados.

## Regras de implementação
Sem mocks em produção; sem CSS global disruptivo não revisado; sem migração de framework improvisada. Preservar i18n, telemetria, analytics, upload/download, debounce, paginação, cache, erros, timezone, timezone display, affordances existentes. Acessibilidade desde o componente, não como ajuste final.

## Incidente
Se um teste crítico falhar, interrompa a próxima fatia, localize causa, reverta mudança ou busque autorização para correção. Registre falha e efeito colateral, inclusive quando não resolvido.
