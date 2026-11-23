# Agente 1 — Infraestrutura de testes

**Objetivo:** tornar a bateria Testcontainers + PostgreSQL reproduzível sem tocar no runtime de produção.

## Checagens
- Ler README, POM, docker-compose, Dockerfiles e perfil `integration`.
- Executar `docker version`, `docker info`, inspeção do Docker context e permissões adequadas (sem expor ambiente sensível).
- Reproduzir `mvn -B -ntp verify -Pintegration` no contexto apropriado. Se Maven for executado no host Windows, confirmar JDK e daemon Docker Desktop; se em container Linux, documentar ausência do socket.
- **Preferir** host Maven + Testcontainers, runner de CI que suporte Docker ou estratégia equivalente segura. Não fazer docker socket mount sem autorização explícita, por ampliar controle do host.
- Não 'resolver' trocando PostgreSQL real por H2, nem saltando integração com `-DskipITs`/`-DskipTests`.
- Preservar Compose e volumes de produção. Alterar configuração apenas no escopo de teste quando necessário e justificado.
- Reexecutar 21 testes e salvar resumo (erros de assertion ≠ erros de inicialização).

## Baseline JaCoCo
- Invalidar dados de execução antigos por build limpo adequado (`mvn clean ...` no módulo certo), sem apagar DB/volumes.
- Extrair `LINE` e `BRANCH` de `jacoco.xml` e calcular `covered/(covered+missed)`; verificar discrepância relatório `1123` vs `808+415=1223`.
- **Não** usar relatório velho gerado após `-DskipTests` para declarar cobertura recente.

## Entrega
`reports/01_INFRA_BASELINE.md`: comandos, ambiente, `PASS/FAIL/BLOCKED`, testes IT, métricas e riscos.
