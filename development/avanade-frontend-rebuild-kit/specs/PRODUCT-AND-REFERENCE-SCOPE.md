# Escopo do produto vs. referência

## Fonte da verdade funcional
Repositório, contratos reais, requisitos aceitos e testes do produto vigente. **Nenhum** link da Avanade define comportamento de negócio da aplicação. Inventário de rotas e permissões deve ser obtido empiricamente.

## Camadas
| Camada | Permitido após aprovação? | Observação |
|---|---|---|
| Tokens visuais, estilos, layout, componentes de apresentação | Sim | Diffs pequenos e testes |
| Estados de UI preservando semântica existente | Sim | Erros, loading, vazio e permissão |
| Navegação e rotas | Somente apresentação, sem alterar contratos | Não remover rotas ou guards |
| Client HTTP, DTOs, endpoints, autenticação, ACL | **Não** | Preservar a integração real |
| Banco, backend, domínio, jobs, infraestrutura | **Não** | Fora de escopo |
| Integrações, analytics, i18n, testes funcionais | Preservar | Adaptar seletores de testes se necessário e aprovado |

## Riscos específicos
Acessibilidade de texto em gradiente; regressão em formulários; alteração inadvertida de props ou guards; SSR/hydration; diferenças de tema; dependências; métricas. Proteções: baseline, matriz de contratos, testes por rota/papel, revisão de diff e rollback.

## Perguntas resolvidas por evidência antes de propor solução
Qual stack? Quais papéis? Há design system legado? Os clientes usam mobile? Quais rotas são críticas? Quais endpoints e payloads? Que testes e telemetria existem? Que ativos podem ser legalmente usados? O que pode ser alterado com baixo risco?
