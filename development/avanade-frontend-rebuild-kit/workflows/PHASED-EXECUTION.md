# Protocolo de execução por gates

| Fase | Entrada mínima | Ação | Saída / evidência | Gate |
|---|---|---|---|---|
| 1 Auditoria | Repositório acessível | Ler e mapear sem alterações | Auditoria + inventário + baseline | Cobertura conhecida |
| 2 Pesquisa | Fontes e ferramentas reais | Verificar páginas e capturas | Registro visual com proveniência | Sem especificação oficial inventada |
| 3 Sistema | Auditoria + pesquisa | Propor sem implementar | Tokens, componentes, acessibilidade | Proposta revisável |
| 4 Plano | Artefatos 1–3 | Definir mudanças por arquivo, risco e teste | Plano completo | **APROVAÇÃO EXPLÍCITA** |
| 5 Implementação | Gate 4 aprovado | Migrar incrementalmente | Diffs e testes por fatia | Sem regressões críticas |
| 6 Validação | Implementação revisada | Testar matrizes | Evidência e falhas | Bloqueadores resolvidos |
| 7 Entrega | Validação | Consolidar e documentar | Relatório e rollback | Aceite humano |

## Controle da fase 4
A aprovação precisa identificar versão do plano, data, escopo e restrições. Silêncio, 'continue pesquisando' ou aprovação estética isolada não autorizam codificação. Se o plano mudar materialmente (arquivos, dependências, contratos, risco), peça reaprovação.

## Registro de decisão
`DEC-ID | data | proposta | alternativas | impacto | aprovação | evidência`. Dependências externas exigem consentimento próprio. Não usar screenshot como prova de comportamento de backend.

## Rollback
Trabalhe em branch isolada quando autorizado. Registre commit inicial e ponto seguro por fatia. Reverter frontend não deve acionar migração ou apagar dados. Não execute reset destrutivo sem autorização.
