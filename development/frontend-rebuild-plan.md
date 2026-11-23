# Plano de execução — reconstrução do frontend

1. Preservar o frontend anterior em `frontend-legacy-2026-11-21/`.
2. Manter `frontend/` como ponto de montagem do Compose e reutilizar `api.ts`/`document-workflow.ts`.
3. Reconstruir a apresentação com shell, navegação e tokens próprios inspirados na direção visual do kit Avanade.
4. Cobrir a jornada P0: sessão, proposta, documentos/versionamento, bureau, análise/dossiê e decisão humana.
5. Não alterar backend, banco, contratos REST, autenticação, ACL ou infraestrutura.
6. Validar TypeScript, build, lint, testes de workflow e integridade do diff.

Critérios de aceite: build/typecheck sem erros, testes existentes passando, backend sem diff, Compose apontando para `frontend/`, e contratos/headers preservados.
