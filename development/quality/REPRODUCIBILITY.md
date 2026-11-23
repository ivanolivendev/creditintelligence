# Reprodutibilidade

## Pré-requisitos

- Docker Desktop e Docker Compose
- Java 21 e Maven, ou o serviço `backend-test` do Compose
- Python 3.12 e dependências de `document-intelligence-python/requirements.txt`
- Node.js e npm para `frontend`
- Dados sintéticos do repositório

Não use credenciais reais ou documentos pessoais. O runtime Compose usa `AI_PROVIDER=bedrock` e `BUREAU_MODE=sandbox` por padrão; o serviço `backend-test` seleciona providers mock explicitamente. Bedrock e bureau externo exigem configuração autorizada e conectividade, e seus resultados devem ser identificados separadamente.

## Comandos

### Java unitário, ArchUnit e cobertura

```text
docker compose --profile test run --rm backend-test mvn -B -ntp clean test
```

Relatórios em `backend-java/target/surefire-reports/` e `backend-java/target/site/jacoco/`. A execução no working tree de 23/11 registrou 276 casos aprovados.

### Java integração

```text
docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify
```

Esse perfil inicia PostgreSQL para Testcontainers, aplica migrations Flyway e grava resultados em `backend-java/target/failsafe-reports/`. O perfil usa o socket Docker local para Testcontainers; nao o trate como configuracao de deploy. A execucao de 23/11 aprovou 24 casos sem falhas, erros ou ignorados.

### PIT

```text
docker compose --profile test run --rm backend-test mvn -B -ntp -Djacoco.skip=true -Dthreads=4 -Pmutation org.pitest:pitest-maven:mutationCoverage
```

O relatório atual é `backend-java/target/pit-reports/mutations.xml`. O denominador é 677 mutações no namespace configurado. O threshold de 80 está no `pom.xml` e não foi modificado nesta missão.

### Python

```text
docker compose --profile test run --rm document-intelligence-test python -m pytest -q
docker compose --profile test run --rm document-intelligence-test python -m ruff check app tests
```

A auditoria de 23/11 registrou 17 testes aprovados e Ruff aprovado pelo Compose. Uma tentativa direta local anterior e seus resultados permanecem apenas como historico; nao substituem a execucao Compose.

### Frontend

```text
cd frontend
npm test -- --run
npm run test:coverage
npm run build
npm run lint
npm run format:check
```

A execução de 23/11 aprovou 10 testes em dois arquivos, alem de build, lint e formatacao. A cobertura fica em `frontend/coverage/` e deve ser lida separadamente da JaCoCo Java.

### CI e auditorias de dependência

`.github/workflows/quality.yml` define os jobs Java, Python e frontend. O SCA Java exige `NVD_API_KEY` e bloqueia CVSS igual ou superior a 7. Python usa `pip-audit`. Frontend usa `npm audit --audit-level=high`. A existência do workflow não prova execução bem-sucedida no provedor. `npm audit` também depende de acesso ao registry.

## Como validar a fotografia

1. Registrar `git rev-parse HEAD`, data UTC/local e `git status --short --branch`.
2. Executar os comandos em sequência, sem compartilhar o mesmo `backend-java/target` entre processos Maven concorrentes.
3. Somar os atributos `tests`, `failures`, `errors` e `skipped` dos XML Surefire e Failsafe.
4. Agregar `LINE_COVERED`, `LINE_MISSED`, `BRANCH_COVERED`, `BRANCH_MISSED`, `CLASS_COVERED` e `CLASS_MISSED` de `jacoco.csv`.
5. Agrupar `status` em `mutations.xml` e registrar o denominador.
6. Guardar os comandos, o ambiente e os relatórios junto do commit analisado.

Resultados antigos da auditoria permanecem históricos. Não devem ser somados a esta fotografia nem apresentados como execução atual.
