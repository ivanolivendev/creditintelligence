# Prompt da Fase 5 — executar somente após aprovação

**Pré-condição verificável:** existe aprovação explícita do usuário para o plano de Fase 4 (data, escopo, arquivos, versão). Se faltar, pare. Leia `workflows/IMPLEMENTATION-WORKFLOW.md`.

Implemente pequenas fatias por feature/rota e com diffs rastreáveis. Preserve chamadas, tipos, retornos, autorização, validações e semântica de erros. Priorize tokens, componentes compartilhados e migração gradual de telas. Verifique estados happy/error/loading/empty/permission-denied e teclado em cada etapa. Não edite arquivos não aprovados ou backend.

Depois de cada fatia: compare baseline e comportamento, execute testes disponíveis, registre arquivos alterados, impacto, rollback e evidência. Diante de falha crítica, reverta a fatia e relate. Não maquie testes ou substitua dados de produção por placeholders.
