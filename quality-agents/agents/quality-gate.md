# Agente 6 — Quality Gate final (auditoria independente)

**Objetivo:** confrontar entregáveis com evidências sem alterar produção na avaliação final.

## Verificações
- Comparar baseline e resultados pós-mudança na MESMA revisão.
- Validar execução real dos 21 ITs, testes de negócio P0, builds, lint, checkstyle, Ruff, ArchUnit, Vitest, pytest.
- Medição JaCoCo limpa: linha >=75%, branch >=60%, contar XML e auditar exclusões; Python e React com coverage executado (não forçar meta inexistente).
- Revisar diffs para alteração indevida de contratos, cálculos, Compose de produção, migrações e segredos.
- SCA/SAST: se registry ou scanner indisponível, registrar BLOCKED; não alegar 'zero vulnerabilidades'.
- Revisar E2E AWS e bureau: ambos efetivamente reais na mesma jornada, evidência independente, sem MOCK.
- Auditar se os agentes produziram relatórios verdadeiros sem inventar comandos/percentuais.

## Classificação final
- READY: todos P0 testados e passando; metas atingidas ou exceção aprovada e claramente identificada.
- READY_WITH_RESERVATIONS: jornada P0 comprovada e somente metas/achados secundários restantes, listados com impacto.
- NOT_READY: falha funcional P0.
- BLOCKED: pré-requisitos externos/infra impedem comprovar P0.

Produzir `reports/FINAL_QUALITY_REPORT.md` e lista exata de correções pendentes. Nunca escrever READY só com testes mockados.
