# Critérios objetivos de aceite

Cada critério é `PASSOU`, `FALHOU`, `BLOQUEADO` ou `NÃO EXECUTADO`, com evidência reproduzível. A entrega **não pode ser aceita** se qualquer bloqueador crítico falhar. Indicadores sugeridos são critérios de projeto PROPOSTOS, não padrões da marca.

| ID | Critério verificável | Evidência exigida | Severidade |
|---|---|---|---|
| G01 | Aprovação explícita do plano antes da primeira alteração ampla | Registro/escopo/data | Bloqueador |
| F01 | Nenhuma alteração a backend/DB/API/DTO/auth/ACL | Diff e verificação de contratos | Bloqueador |
| F02 | 100% das rotas e fluxos inventariados continuam acessíveis ao papel correto | Matriz antes/depois por papel | Bloqueador |
| F03 | Payloads/headers/códigos relevantes preservados | Testes de integração/rede sanitizados | Bloqueador |
| F04 | Dados reais seguem consumidos pelo client original, sem mocks substitutos | Inspeção de integração e execução | Bloqueador |
| V01 | Linguagem quente e fluida presente onde aprovada, sem copiar marca | Capturas pareadas e revisão de design | Alto |
| V02 | Tokens semânticos consistentes; sem cor hardcoded fora de exceções documentadas | Inventário/lint/revisão | Alto |
| R01 | Sem overflow horizontal não intencional em 320, 375, 768, 1024 e 1440 px | Capturas e inspeção por rota crítica | Alto |
| A01 | Fluxos críticos operáveis por teclado, foco visível e ordem lógica | Checklists e gravação/manual | Bloqueador |
| A02 | Contraste WCAG 2.2 AA: texto normal ≥4.5:1, grande ≥3:1, UI relevante ≥3:1 | Relatório de amostras reais | Bloqueador |
| A03 | Sem armadilhas de teclado; labels/erros anunciáveis; zoom 200% e reflow quando aplicável | Teste manual/automático | Alto |
| Q01 | Build e lint executados sem nova falha atribuível às mudanças | Comandos e logs | Bloqueador |
| Q02 | Testes existentes + regressões críticas executados; nenhuma nova falha crítica | Matriz e logs | Bloqueador |
| Q03 | Nenhum erro novo relevante de console/rede em fluxos críticos | Evidências antes/depois | Alto |
| Q04 | Novos componentes têm estados e testes pertinentes | Inventário componente/estado | Alto |
| D01 | Relatório completo com arquivos, riscos, pendências e rollback | Documento final | Bloqueador |

## Nota de comparação visual
Fidelidade significa coerência com a **direção aprovada**, não reprodução da marca; avalie side-by-side em viewport, dados e estado constantes. Diferenças intencionais devem estar documentadas.

## Saída de aceite
Uma tabela por critério com resultado, ambiente, captura ou log, responsável, data e desvio. `NÃO EXECUTADO` em item bloqueador impede aceite formal mesmo quando a tela 'parece certa'.
