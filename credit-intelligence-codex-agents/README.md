# Credit Intelligence — Codex Multi-Agent Engineering Framework

Este pacote foi desenhado para ser colocado na raiz do repositório do projeto **Crédito Inteligente** e usado pelo Codex como framework operacional de auditoria, correção, validação e reauditoria.

## Objetivo

Não é "mandar vários agentes melhorarem o código".

O objetivo é executar um ciclo controlado:

1. reconhecer o sistema;
2. auditar em paralelo;
3. validar os achados;
4. agrupar causas-raiz;
5. planejar changesets;
6. corrigir apenas problemas confirmados;
7. testar e tentar quebrar as correções;
8. reauditar;
9. encerrar somente após o quality gate.

## Princípio central

> Precisão acima de quantidade. Evidência acima de opinião. Viabilidade da POC acima de perfeccionismo técnico.

## Como usar

1. Extraia esta pasta na raiz do repositório.
2. Abra `MASTER_MISSION.md`.
3. Instrua o Codex a executar integralmente essa missão.
4. Não peça ao Codex para "melhorar tudo livremente". O orquestrador já contém as regras de permissão e escopo.

## Estrutura

- `constitution/` — regras invioláveis e contexto da POC.
- `agents/` — responsabilidades dos agentes.
- `schemas/` — formato obrigatório de achados, changesets e gates.
- `workflows/` — ordem operacional.
- `state/` — artefatos de estado que os agentes devem preencher.
- `templates/` — modelos de relatórios e decisões.
- `checklists/` — listas práticas de verificação.
- `MASTER_MISSION.md` — ponto de entrada da execução.
