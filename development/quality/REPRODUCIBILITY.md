# Reprodutibilidade

## Pré-requisitos

- Docker Desktop e Docker Compose
- Java 21 e Maven, ou o serviço `backend-test` do Compose
- Python 3.12 e dependências de `document-intelligence-python/requirements.txt`
- Node.js e npm para `frontend`
- Dados sintéticos do repositório

Não use credenciais reais ou documentos pessoais. O Compose usa providers mock por padrão. Bedrock real e bureau externo exigem configuração autorizada e conectividade, e seus resultados devem ser identificados separadamente.

## Comandos

### Java unitário, ArchUnit e cobertura

```text
docker compose --profile test run --rm backend-test mvn -B -ntp clean test
```

Relatórios em `backend-java/target/surefire-reports/` e `backend-java/target/site/jacoco/`. A fotografia atual registrada contém 276 casos aprovados.

### Java integração

```text
docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify
```

Esse perfil inicia PostgreSQL para Testcontainers, aplica migrations Flyway e grava resultados em `backend-java/target/failsafe-reports/`. A fotografia atual contém 24 casos aprovados.

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

A auditoria datada registra 17 testes aprovados e Ruff aprovado. A tentativa direta local de `python -m pytest -q` em 22/11 executou dez casos e permaneceu bloqueada em um caminho de integração. Esse resultado direto é inconclusivo e não substitui a execução Compose registrada.

### Frontend

```text
cd frontend
npm test -- --run
npm run test:coverage
npm run build
npm run lint
npm run format:check
```

A execução local desta missão aprovou 10 testes em dois arquivos. A cobertura fica em `frontend/coverage/` e deve ser lida separadamente da JaCoCo Java.

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
