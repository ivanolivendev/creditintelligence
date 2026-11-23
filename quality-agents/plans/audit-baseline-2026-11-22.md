# Baseline histórica — segunda auditoria de 22/11/2026

**Fonte:** relatório fornecido pelo usuário, NÃO reexecutado neste ZIP.

- Java 54 testes unitários PASS; Python 10 PASS; frontend 6 PASS.
- 21 testes integration identificados; 21 erros Testcontainers sem Docker environment no contêiner Maven.
- JaCoCo declarava 66,07% linhas e 41,98% branches, mas dizia 1123 linhas instrumentadas, 808 cobertas e 415 descobertas: **808+415=1223**, portanto o total escrito está errado; 808/1223=66,07%.
- Pacote `analysis.application`: 29,43% de linhas, 17,38% branches.
- `AnalysisService` com complexidade JaCoCo agregada 185, 11 cobertas — não confundir com complexidade de um método.
- Sem frontend coverage devido a `@vitest/coverage-v8`; sem Python coverage declarado.
- `npm audit` bloqueado por DNS/registry; SAST/SCA não testados.
- Backend JaCoCo alertou sobre mismatch entre execution data e classes; limpeza essencial.
- E2E AWS + Consult Center não foi reproduzido na auditoria; em conversa posterior houve relato de execução com Bedrock AVAILABLE, mas relatório novo deve validar independentemente.
- `.env`, arquivo de credenciais e chave AWS local foram reportados: não imprimir conteúdo, recomendar higiene de segredos.

Uso: orientar prioridades, **não marcar testes como PASS com base nesta baseline**.
