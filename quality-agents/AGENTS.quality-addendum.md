# Instruções adicionais de qualidade — Credit Intelligence

Leia `development/AGENTS.md` e documentação V3/V3.4 ANTES de agir. Este arquivo é **complementar**, não substituto. Estrategia B: mudanças pequenas e testadas são autorizadas; reescritas arquiteturais e regras novas não.

## Protocolo obrigatório

1. **Inventariar**: paths reais, comandos, containers, configurações, estado Git e ferramentas disponíveis. Não presumir que arquivos/caminhos de relatórios antigos ainda existem.
2. **Baseline limpo**: garantir que build, classes e `.exec` JaCoCo correspondem à mesma revisão; medir coverage com numeradores/denominadores e origem; não interpretar `verify -DskipTests` com dados velhos como métrica.
3. **Testes antes de alterações**: caracterizar comportamento atual, escrever cenário que falha/reproduz ou teste de proteção, aplicar mudança mínima, repetir testes.
4. **Prioridades**: P0 Testcontainers/integração; P1 AnalysisService, consistência, concorrência, idempotência, Bedrock e bureau; P2 métricas React/Python, SAST/SCA e manutenção.
5. **Evidência**: cada tarefa tem comando, ambiente, resultado, logs sanitizados, arquivos tocados e regressão; registrar PASS / FAIL / BLOCKED / NOT_RUN, sem inflar números.
6. **Coordenação**: agentes são papéis especializados (podem ser executados sequencialmente pelo mesmo Codex). Não modificar os mesmos arquivos em paralelo; só chamar gate após fechamento das dependências.
7. **Sem exagero**: evitar cobertura artificial, assert sem significado, testes frágeis contra detalhes internos e refatorações amplas; meta numérica é alvo, não autorização para mudanças sem valor.
8. **Dados de teste**: somente sintéticos; chamadas Bedrock/Consult Center reais apenas quando autorização, credenciais e custos são adequados; nunca reusar segredos divulgados em conversas como fonte de verdade.

## Segurança e operações

- Jamais imprimir `.env`, chaves, headers de autorização, arquivos de credenciais, valores de secret em log.
- Sem `docker compose down -v`, `docker volume rm`, `git reset --hard`, `git clean -fd`, reescrita de migração aplicada, reset de banco, rotação unilateral de credenciais.
- Dependências externas: disponibilidade e custo precisam ser explicitados; erros externos não justificam fallback silencioso.
- Se Testcontainers não tiver Docker, investigar host e runner; não habilitar mount de `/var/run/docker.sock` automaticamente.
- Não comprometer segurança somente para o teste passar; não desabilitar auth nem checks.

## Gate obrigatório

- Java tests + integration realmente executados; 21 testes originais não podem permanecer bloqueados e ser declarados sucesso.
- Meta proposta: Java >=75% linhas e >=60% branches (base limpa). Ao não atingir, relatar desvio justificado sem fingir conclusão.
- Python e frontend cobertura medida, scripts reproduzíveis; sem meta numérica arbitrária por camada.
- E2E real: Consult Center sandbox e AWS Bedrock, distinguindo comprovado de histórico, sem mock na jornada principal.
- `quality-agents/reports/FINAL_QUALITY_REPORT.md` obrigatório e fundamentado.
