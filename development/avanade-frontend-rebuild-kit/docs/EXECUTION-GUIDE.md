# Guia de execução para humano e agente

## Iniciar
Abra o projeto real e este diretório. Use `prompts/INITIAL-USER-MESSAGE.md` como mensagem inicial. Antes de delegar, assegure que o agente tem permissão de leitura e que sabe onde pode guardar relatórios sem tocar no código. A missão inicial **não autoriza implementação**.

## Conferência das entregas iniciais
1. Auditoria com evidências e inventário de rotas, contratos e papéis.
2. Registro de fontes visuais verificadas, distinguindo peças institucionais de padrões de interface.
3. Design system com tokens propostos, origens, confiança e acessibilidade.
4. Plano versionado: cada arquivo e rota previstos, dependências, testes e rollback.

## Momento de decisão
Revisar proposta e riscos, então aprovar explicitamente ou solicitar revisão. Somente um plano aprovado permite escrever frontend. Autorização não vale para backend, APIs, DTOs, migrações, autenticação ou integrações.

## Acompanhar implementação
Pedir revisão por fatia com capturas comparáveis, contrato invariável e testes. Garantir que atualizações não substituem chamadas reais por mocks. Se surgirem diferenças fora do aprovado, exigir aditivo ao plano.

## Encerrar
Verificar tabela de critérios em `specs/FRONTEND-REBUILD-ACCEPTANCE.md`, registrar testes não executados e pendências, e só então dar aceite humano. Caso não haja ambiente para screenshots, testes de rede ou leitor de tela, registrar o bloqueio em vez de 'validado'.

## Exemplo de pedido de revisão
`Revise a Fase 4: quero menos decoração nos formulários, foco mais evidente e nenhuma dependência nova. Reapresente tokens, arquivos afetados, riscos e testes. Não implemente.`
