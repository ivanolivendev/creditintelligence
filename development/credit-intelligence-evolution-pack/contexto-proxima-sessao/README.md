# Contexto para executar a evolução

Esta pasta complementa o Evolution Pack 4.0. Ela registra o contexto do repositório e orienta a próxima sessão a executar a evolução sem confundir proposta com implementação existente.

## Leitura recomendada

1. `PROMPT-PARA-NOVA-SESSAO.md` — copie e cole na nova conversa/agente.
2. `ESTADO-ATUAL-OBSERVADO.md` — baseline do produto, evidências disponíveis e itens que precisam ser revalidados.
3. `DECISOES-E-LIMITES.md` — decisões arquiteturais e limites que não devem ser alterados sem fundamento.
4. `MAPA-EVOLUCAO-PARA-CODEBASE.md` — relação entre o objetivo do Evolution Pack e a implementação existente.
5. Leia integralmente os documentos originais do Evolution Pack na ordem indicada no `README.md` da pasta pai, incluindo o relatório de execução e os casos de teste.

## Precedência e certeza

- `development/AGENTS.md` e o Blueprint V3 definem as regras gerais e os invariantes do projeto.
- O Evolution Pack 4.0 define o objetivo e o escopo específico desta evolução.
- O código, as migrations, os contratos e os testes atuais definem o que realmente existe.
- Esta pasta é contexto de transferência, não especificação concorrente. Se algum detalhe divergir, o agente deve apontar a divergência, consultar as fontes acima e justificar a decisão no plano OpenSpec antes de implementar.
- O estado aqui descrito é uma baseline de 20/11/2026 e pode ficar desatualizado. Revalidar o repositório e o Docker antes de qualquer conclusão.

## Escopo da execução

Executar a evolução do pacote localmente em Docker Compose. Reutilizar o Bedrock já integrado. A integração de bureau em sandbox está no escopo previsto pelo Evolution Pack, mas só pode ser declarada funcional após validar documentação, credenciais, ambiente e uma resposta real de sandbox. Se isso não for possível, manter um adapter/fixture local claramente rotulado e registrar a integração remota como parcial ou bloqueada; não simular uma chamada real.

Não provisionar ou publicar infraestrutura AWS nesta execução. O serviço deve continuar executável localmente; Bedrock e o sandbox de bureau, quando verificados e habilitados, são integrações externas da aplicação local.
