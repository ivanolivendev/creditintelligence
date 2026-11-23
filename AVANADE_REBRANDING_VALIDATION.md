# Validação do rebranding visual

Data: 21 de novembro de 2026
Escopo: camada `frontend/` do Credit Intelligence
Ambiente: Windows, React 19, TypeScript, Vite, Node/npm locais do projeto

## Plano executado

1. Registrar a referência visual e os limites de uso da marca.
2. Criar o Design System e tokens semânticos.
3. Aplicar a linguagem visual no shell, login, formulários, upload, dossiê, BI e histórico.
4. Reforçar foco de teclado, nomes acessíveis e `prefers-reduced-motion`.
5. Executar build, lint, testes, formatação e verificação de diff.
6. Abrir o frontend já executado no Chrome e verificar a árvore de acessibilidade.
7. Encerrar o servidor temporário criado para a validação.

## Resultado dos gates

| Critério | Resultado | Evidência |
| --- | --- | --- |
| Build TypeScript/Vite | PASSOU | `npm run build`; bundle em `frontend/dist` |
| ESLint | PASSOU | `npm run lint` com exit code 0 |
| Testes frontend | PASSOU | `npm test -- --run`: 1 arquivo, 2 testes |
| Prettier | PASSOU | `npm run format:check` |
| Integridade do diff | PASSOU | `git diff --check` nos arquivos da mudança |
| Renderização em navegador | PASSOU PARCIALMENTE | Chrome exibiu `Credit Intelligence` em `http://localhost:18081/` com dados do histórico |
| API/backend/auth/ACL | PRESERVADO | Nenhum arquivo backend, contrato ou client HTTP foi alterado funcionalmente |
| Estados acessíveis | PASSOU EM CÓDIGO | foco visível, `aria-label` na decisão, labels existentes e reduced motion |
| Capturas visuais automatizadas | BLOQUEADO | Chrome não expôs screenshot target; o sandbox também bloqueou acesso HTTP ao loopback |
| Jornadas E2E completas | NÃO EXECUTADO | exigiria mutação adicional de dados sintéticos no ambiente local |

## Arquivos desta entrega

- `AVANADE_DESIGN_SYSTEM.md`
- `AVANADE_REBRANDING_VALIDATION.md`
- `frontend/src/design-tokens.css`
- `frontend/src/styles.css`
- `frontend/src/App.tsx`
- `frontend/index.html`

## Riscos e limitações remanescentes

O worktree já estava sujo antes desta tarefa com mudanças amplas em backend, frontend, documentação e fixtures. Nenhuma dessas alterações preexistentes foi descartada ou reformatada fora dos arquivos editados para o rebranding.

A validação visual por screenshot em todos os viewports não pôde ser automatizada por restrição do ambiente. A interface foi verificada estruturalmente no Chrome e o CSS contém os breakpoints de QA definidos no Design System. Uma revisão visual humana em 320, 375, 768, 1024 e 1440 px continua sendo a evidência mais forte para o aceite final.
