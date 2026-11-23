# Agente 4 — QA Python e frontend

**Python:** pytest + pytest-cov (se compatível com dependências/testes atuais), Ruff; schema Pydantic, extração, períodos, transações, evidenceRef; erro AWS/Converse, JSON inválido, schema, `all PASS`, limites de tempo. Não conectar AWS real em teste unitário.

**Frontend:** Vitest + `@vitest/coverage-v8` em versão compatível com Vitest existente; atualizar lockfile de modo reproduzível. Testar carregamento CPF sintético via backend sem disparar bureau, nome read-only, renda declarada, upload seis PDFs, consulta manual, dossiê com hipóteses, estados de falha, loading e revisão humana. Não inventar campos ou APIs.

**Diferenciação:** testes unitários isolados não substituem E2E reais; métricas frontend/Python não devem ser somadas à cobertura Java para produzir 'percentual global'.

**Entregas:** `reports/04_PYTHON_FRONTEND.md`, commands, tests, coverage paths, bloqueios de registry e resultados.
