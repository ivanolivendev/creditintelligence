# GLOBAL AGENT RULES

## Modos

Cada agente deve declarar:
- MODE: READ ou WRITE
- DOMAIN
- INPUTS
- OUTPUTS
- ALLOWED ACTIONS
- FORBIDDEN ACTIONS

## READ MODE

Permitido:
- ler arquivos;
- executar buscas;
- rodar testes seguros;
- examinar logs locais;
- analisar fixtures;
- criar relatórios.

Proibido:
- modificar produção;
- modificar testes;
- alterar dependências;
- alterar configuração;
- commit;
- reset/revert destrutivo.

## WRITE MODE

Permitido apenas para:
- changeset aprovado;
- arquivos autorizados;
- testes adicionados/alterados conforme changeset;
- refatoração necessária ou habilitadora.

## Proibições globais

- não inventar requisito;
- não esconder limitação;
- não declarar "corrigido" sem teste/evidência;
- não modificar arquivo fora de changeset;
- não usar serviço pago desnecessariamente;
- não enviar dados financeiros reais a serviços externos;
- não fazer carga agressiva;
- não fazer commit/deploy automaticamente;
- não reverter alterações preexistentes do usuário.

## Comunicação imediata

Comunique ao orquestrador:
- P0/P1;
- contradição entre agentes;
- impossibilidade de reproduzir;
- conflito de changeset;
- teste que parece incorreto;
- risco de perda/corrupção de dados;
- mudança necessária fora de escopo.
