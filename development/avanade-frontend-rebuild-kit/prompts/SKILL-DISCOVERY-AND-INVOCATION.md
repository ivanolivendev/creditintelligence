# Descoberta e invocação real de skills

## Sequência sem suposições
1. Inspecione manifesto, catálogo ou mecanismo **realmente oferecido** pelo ambiente. Registre nome, origem, versão e permissão de cada skill. Não execute comandos para instalação ao acaso.
2. Procure `ui-forge` por identificação exata e aliases documentados. Se disponível, leia **toda a documentação relevante** (incluindo recursos referenciados) e anote pré-requisitos, entradas, saídas e mecanismo de chamada.
3. Invoque `ui-forge` **somente usando o mecanismo documentado**, em tarefa limitada de análise/design, sem editar repositório até aprovação. Guarde registro de invocação e saída.
4. Descubra outras capacidades reais: navegador/inspeção DOM, screenshots, design tokens, componentes, auditoria de acessibilidade, execução de testes, comparação de screenshots e análise de rede.
5. Para cada ferramenta, classifique: `DISPONÍVEL/EXECUTADA`, `DISPONÍVEL/NÃO EXECUTADA`, `NÃO DISPONÍVEL`, `SEM ACESSO` e cite evidência.

## Fallback se `ui-forge` faltar
Não invente seu CLI/API. Continue com leitura do código, navegador/DOM e capturas **se existentes**, heurísticas documentadas de UX, tokens próprios e avaliação manual de contraste. Marque ausência da skill no relatório e indique quais verificações ficaram limitadas. Não instale plugins/dependências sem aprovação.

## Registro mínimo
| Skill/ferramenta | Fonte/versão | Como foi descoberta | Documentação lida | Ação real | Resultado/evidência | Limitação |
|---|---|---|---|---|---|---|
| ui-forge | a descobrir | a descobrir | sim/não | nenhuma até verificar | — | — |

**Regra:** a descoberta não prova execução; execução não prova sucesso. Preserve os dois registros separadamente.
