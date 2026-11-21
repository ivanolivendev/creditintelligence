# Mapa da evolução para a codebase existente

Mapa inicial baseado na árvore observada e nos documentos do projeto em 20/11/2026. Os arquivos citados são pontos de partida para inspeção, não lista fechada de mudanças.

| Necessidade do Evolution Pack | Base observada | Direção de investigação |
|---|---|---|
| Propostas e ciclo de vida | `backend-java/.../proposal`, `backend-java/.../document`, controllers `/api/v1/proposals` | Reutilizar proposta, upload, storage, versões e autenticação. Confirmar tipo/limites atuais antes de estender para `PAYSLIP` e `BANK_STATEMENT`. |
| Análise e snapshot | `backend-java/.../analysis`, `AnalysisService`, migrations V3/V5 | Estender o pipeline existente sem perder idempotência, fencing/attempt, checksum, policy snapshot ou histórico. Rever fingerprint para capturar todas as fontes novas. |
| Regras determinísticas | `backend-java/.../analysis/domain/IncomeConsistencyRule.java` e views de findings | Adicionar regras/core financeiro no domínio conforme convenção existente. Não colocar matemática em controller, entidade, Python ou prompt. |
| Persistência e versionamento | `backend-java/src/main/resources/db/migration`, repositories/entities de analysis/document | Verificar schema real. Criar apenas migrations aditivas para assessment/simulação/consultas que não caibam com segurança nos modelos existentes. |
| Extração documental | `document-intelligence-python/app`, schemas e extractor; cliente `PythonDocumentClient` em Java | Evoluir contrato Python com versão; manter compatibilidade do fluxo atual e evidências por documento/campo/transação. Cobrir Java↔Python com testes de contrato. |
| Explicação Bedrock | `PythonExplanationClient`, `AIExplanationService`, endpoint Python `/internal/v1/explain` | Reutilizar integração e validações. Adaptar payload para explicar assessment Java pós-cálculo; não duplicar chamada nem fazer Bedrock calcular valores. |
| Bureau sandbox | Não observado no inventário inicial como implementação existente | Primeiro descobrir fornecedor/documentação já escolhidos pelo usuário ou pacote e credenciais disponíveis. Se verificável, uma porta + um adapter; se não, fixture MOCK e pendência honesta. |
| Dossiê/decisão | `AnalysisController`, `DecisionController`, `AnalysisView`, `SnapshotView`, `DecisionService` | Acrescentar dados ao dossiê atual e manter decisão humana vinculada a análise/snapshot, sem decisão automatizada. |
| UI | `frontend/` React/TS/Vite; `README.md` descreve mesa do analista | Adaptar a jornada para 3+3 documentos, indicadores, simulação, fontes/status, limitações e evidências; preservar clareza UX já melhorada. |
| Runtime e testes | `compose.yaml`, `scripts/smoke-*.ps1`, perfis `test` | Rodar build Java no container, pytest/Ruff no container configurado, scripts smoke e builds frontend conforme instruções atuais. Não apagar volumes. |
| OpenSpec | `openspec/specs/` e mudanças arquivadas; `openspec/changes/clear-deterministic-findings` apareceu ativo na inspeção | Verificar estado. Não reinitializar nem sobrescrever configuração. Abrir uma nova mudança pequena para Evolution Pack e validar antes de codificar. |

## Sequência de trabalho recomendada

1. Inspecionar status/diff, instruções, Blueprint, README/Plano, OpenSpec e pacote completo.
2. Revalidar Compose e os testes existentes; registrar baseline sem atribuir regressões à mudança.
3. Montar matriz `EXISTENTE / PARCIAL / AUSENTE / NÃO VERIFICADO` para cada capacidade P0/P1.
4. Definir e validar contratos de payslip, extrato, perfil financeiro, assessment, simulação e bureau em sandbox.
5. Escrever mudança OpenSpec enxuta e lista de arquivos a manter/alterar/adicionar; preservar compatibilidade.
6. Implementar primeiro engine Java puro com fixtures e testes determinísticos; depois persistência/API, extração e UI.
7. Só então conectar explicação Bedrock ao assessment. Para sandbox, provar contrato e credenciais antes do adapter remoto.
8. Rodar testes e jornada local em Docker, atualizar relatório de execução com comandos/evidências/gates e apontar limites restantes.

## Harnesses e ferramentas já descritos

Use os mecanismos que já existem no repositório e verifique sua disponibilidade antes de inventar novos:

- `docker compose` para runtime e build/teste Java.
- Perfil Compose `test` para `backend-test` (Maven/JUnit) e `document-intelligence-test` (pytest/Ruff).
- `scripts/smoke-proposals.ps1`, `scripts/smoke-analysis.ps1` e `scripts/smoke-bedrock.ps1` para fluxos HTTP; Bedrock smoke é opcional e pode gerar custo.
- `npm run build`, `npm run lint` e `npm run format:check` em `frontend/`.
- OpenSpec instalado/configurado e os critérios em `development/AGENTS.md`.
- Skill procedures `credit-implementation`, `credit-verification` e `credit-devops` somente se as skills estiverem de fato disponíveis no ambiente executor; não presumir que existe `.agents/` na raiz (ela não apareceu na listagem observada).

Não instalar ferramentas, reconfigurar OpenSpec ou trocar harnesses sem demonstrar que o mecanismo existente não atende.
