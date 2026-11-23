# Workflow: auditoria técnica (modo leitura)

## 1. Preparar baseline
Identifique raiz real, branch/commit, worktree sujo, package manager, scripts, CI, lockfile, monorepo e pastas de frontend. Comandos de leitura como listagem e busca podem ser usados quando disponíveis; **não execute instalações, migrations, scripts de seed ou scripts desconhecidos**. Inspecione scripts antes de rodar.

## 2. Traçar mapa de fronteiras
Siga entrada web → roteador → página → componente → estado/store → client HTTP → contrato. Liste verbos, URLs, headers, tratamento de erro e uso de session/token sem registrar valores secretos. Confira RBAC/guards tanto na UI quanto no comportamento já existente (sem alterar API).

## 3. Inventário de jornadas
Para cada rota e papel: autenticação, carregamento, vazio, sucesso, erro de validação, erro de rede, sem permissão e logout/expiração. Registre triggers e efeitos observáveis; se não puder executar, marque inferência do código.

## 4. Mapear testes e design legado
Identifique unit/integration/e2e, fixtures, selectors, cobertura, snapshots, CSS, biblioteca de componentes, temas, tokens, breakpoints e acessibilidade existente. Registre dependências/versões sem sugerir migração automática.

## 5. Saída
Preencha `templates/AUDIT-REPORT-TEMPLATE.md` e `templates/ROUTE-INVENTORY-TEMPLATE.md`; faça matriz de API/DTO/papel; identifique gaps e riscos. Diferencie `confirmado em execução`, `confirmado em código` e `não verificado`.
