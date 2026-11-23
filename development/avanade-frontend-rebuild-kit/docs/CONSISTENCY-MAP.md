# Mapa de responsabilidades e consistência documental

| Tópico | Documento normativo | Consumidores |
|---|---|---|
| Restrições de mudanças e precedência | `AGENT-INSTRUCTIONS.md` | Todos prompts e workflows |
| Sequência e gate de aprovação | `workflows/PHASED-EXECUTION.md` | Prompt mestre, plano, checklist |
| Evidências de marca e licenças | `specs/AVANADE-VISUAL-REFERENCE.md` + `docs/BRAND-USAGE-NOTES.md` | Pesquisa e design system |
| Scope frontend/backend | `specs/PRODUCT-AND-REFERENCE-SCOPE.md` | Auditoria, implementação, QA |
| Tokens e acessibilidade | `specs/DESIGN-SYSTEM-SPECIFICATION.md` + `specs/ACCESSIBILITY-REQUIREMENTS.md` | Tokens, execução, aceite |
| Aceite e testes | `specs/FRONTEND-REBUILD-ACCEPTANCE.md` | Regressão, relatório final |

## Resolução de conflitos
Se um prompt resumido disser menos do que um documento normativo, a instrução mais restritiva prevalece. Se uma observação pública divergir de uma especificação proposta, não tratar a proposta como fato. Se houver conflito entre beleza e preservação funcional, preservar a funcionalidade e escalar. Documentos não concedem autorização implícita.
