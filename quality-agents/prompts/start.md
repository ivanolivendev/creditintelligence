# Prompt para Codex — iniciar execução completa do pacote

Atue como coordenador de engenharia de qualidade do Credit Intelligence V3.4. Execute a estratégia **B — equilibrada** definida em `quality-agents/`.

Primeiro, leia obrigatoriamente:
- `development/AGENTS.md` existente (se houver; NÃO sobrescreva).
- `quality-agents/AGENTS.quality-addendum.md`.
- `quality-agents/plans/implementation-plan.md`, `test-matrix.md`, `acceptance-criteria.md`.
- Os seis arquivos de `quality-agents/agents/`.
- Blueprint V3 e Plano V3.4 presentes no repositório.

**Missão:** elevar qualidade da PoC existente SEM introduzir novos serviços, alterar infraestrutura de produção, regras financeiras ou contratos funcionais. Você PODE fazer pequenas refatorações justificadas após testes de caracterização (estratégia B).

**Execute, não apenas planeje:** inventário e git status; Fase 1 Testcontainers/Jacoco; Fase 2 testes Java; Fase 3 refatoração localizada; Fase 4 Python/frontend; Fase 5 E2E reais quando autorizado e configurado; Fase 6 quality gate. Faça mudanças pequenas e registre evidências. Não precisa pedir confirmação para testes locais não destrutivos; para permissões perigosas, mudança em secrets, gastos externos não autorizados ou socket Docker, peça aprovação ou marque BLOCKED.

**Instruções adicionais:**
- Não confunda a falha histórica Bedrock `UNAVAILABLE` com estado atual: teste hoje.
- O relatório da auditoria contém uma discrepância matemática: '1.123 linhas' contra 808 cobertas + 415 não cobertas; medir novo baseline JaCoCo.
- Os 21 ITs antes falharam por Docker indisponível no Maven container. Não aceite mocks ou skip como correção.
- 70 testes unitários foram reportados como passando; reexecutar e não supor que continuam passando.
- Branches reportados 41,98%, AnalysisService 29,43% linhas; priorizar riscos reais.
- Não produzir `READY` enquanto E2E real ou ITs críticos estiverem bloqueados.

Crie e atualize `quality-agents/reports/` durante execução com as saídas previstas. No final, gere `FINAL_QUALITY_REPORT.md` com comandos executados, resultados honestos, arquivos modificados, lacunas, cobertura por camada, próximos passos e veredito. Não invente logs, URLs, resultados de testes ou chamadas externas.
