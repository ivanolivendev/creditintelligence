# Plano de evolução — PIT e portas da aplicação

## Objetivo

Elevar a confiabilidade comportamental da POC e reduzir o acoplamento entre os módulos de aplicação e a persistência JPA, preservando as regras de negócio, os contratos HTTP e o escopo sandbox.

Estado de partida confirmado:

- PIT: 33% — 640 mutações geradas, 208 mortas, 53 sobreviventes, 380 sem cobertura e 1 timeout.
- Java: 57 testes unitários aprovados e 21 testes de integração aprovados.
- ArchUnit: aprovado.
- Portas existentes: `AnalysisDataPort`, `ProposalDataPort` e `PolicyDataPort`.
- Frontend: `App.tsx` já delega as telas para `steps.tsx` e componentes compartilhados.

## Princípios

1. Não aumentar percentual com testes artificiais.
2. Testar comportamento observável e invariantes de negócio.
3. Criar uma porta somente quando houver uma responsabilidade real de persistência ou integração a esconder.
4. Manter entidades JPA, repositories e detalhes de transporte dentro dos adapters de infraestrutura.
5. Fazer uma migração por módulo, compilando e testando após cada etapa.
6. Não alterar regras financeiras, contratos de API, migrations ou dados sintéticos sem evidência de necessidade.

## Trilha A — PIT e branches críticos

### A1. Criar uma baseline detalhada

Executar e guardar os resultados:

```powershell
docker compose --profile test run --rm backend-test mvn -B -ntp "-Djacoco.skip=true" -Pmutation org.pitest:pitest-maven:mutationCoverage
```

Classificar mutações por módulo e por causa:

- sem cobertura;
- sobrevivente por falta de asserção;
- sobrevivente equivalente ou irrelevante;
- timeout/instabilidade;
- comportamento externo que deve ser coberto por teste de integração.

Critério: nenhum mutante será excluído apenas para melhorar o score. Exclusões só serão aceitas quando equivalentes, geradas por código, ou fora do escopo demonstrável da POC, com justificativa no relatório.

### A2. Priorizar o domínio determinístico

Cobrir primeiro `FinancialEngine` e regras puras, porque são rápidos, determinísticos e concentram risco financeiro.

Casos mínimos:

- renda completa, incompleta e duplicada;
- variação dentro e fora do limite;
- crédito salarial ausente, divergente e duplicado;
- obrigações consignadas e não consignadas;
- renda zero e divisão por zero;
- taxa zero, taxa positiva, prazo inválido e valor solicitado inválido;
- limitações que impedem capacidade verificada;
- referências de evidência preservadas.

Critério: cada teste deve afirmar resultado, status, valores ou evidências; executar método sem verificar efeito não conta.

### A3. Cobrir aplicação e falhas externas

Adicionar testes através das portas dos módulos, sem acessar implementação interna:

- idempotência com mesma chave e payload diferente;
- lease/attempt antigo que não pode sobrescrever resultado novo;
- falha do cliente Python;
- resposta externa com contrato inválido;
- bureau indisponível;
- política ausente ou checksum inválido;
- documento inexistente, versão antiga ou storage indisponível;
- decisão não autorizada ou sem justificativa.

Critério: os testes devem verificar estado persistido, exceção HTTP, status final e auditoria quando aplicável.

### A4. Tornar o PIT um gate progressivo

Não saltar diretamente para 70% no build. Usar metas progressivas por escopo:

- etapa 1: eliminar timeout e reduzir mutações sem cobertura nos módulos críticos;
- etapa 2: atingir pelo menos 50% no escopo crítico;
- etapa 3: atingir 60–65% no escopo Java relevante;
- etapa 4: buscar 70–80% sem sacrificar clareza dos testes.

O `mutationThreshold` só deve ser aumentado quando a execução for estável e o score refletir comportamento relevante. O gate final deve falhar abaixo do limiar acordado.

### A5. Verificação da trilha A

Após cada lote:

```powershell
docker compose --profile test run --rm backend-test mvn -B -ntp test
docker compose --profile test run --rm backend-test mvn -B -ntp -Pintegration verify
docker compose --profile test run --rm backend-test mvn -B -ntp "-Djacoco.skip=true" -Pmutation org.pitest:pitest-maven:mutationCoverage
```

Registrar score, sobreviventes, sem cobertura, timeout e classes alteradas no relatório de auditoria.

## Trilha B — Migração para portas

### B1. Ordem de migração

Migrar nesta ordem, do menor risco ao maior:

1. `PolicyService` — concluído com `PolicyDataPort`.
2. `ProposalService` — concluído com `ProposalDataPort`.
3. `DocumentService` — próxima etapa; envolve metadata, versões imutáveis e storage.
4. `DecisionService` — depois de estabilizar proposta/análise; envolve transição de estado e auditoria.
5. `AnalysisService` — por último; é o módulo mais sensível, com lease, fingerprint, snapshot, bureau, Python e avaliação financeira.

### B2. Porta de documentos

Criar uma interface de aplicação que exponha apenas operações semânticas:

- salvar metadata e nova versão;
- buscar documentos atuais da proposta;
- congelar snapshot para análise;
- obter versão e hash;
- salvar/ler resultado de extração;
- remover ou substituir somente conforme as regras existentes.

O adapter JPA traduz records da aplicação para `DocumentEntity`/repositories. O adapter de storage (`LocalDocumentStorage` ou S3) permanece atrás de uma interface de storage já existente ou consolidada.

Testes:

- versão antiga não é sobrescrita;
- hash e conteúdo permanecem associados;
- documento inválido não entra no snapshot;
- falha de storage não deixa metadata falsa;
- fluxo HTTP de upload continua igual.

### B3. Porta de decisão

Criar uma interface de aplicação para:

- buscar decisões por proposta;
- registrar decisão humana;
- verificar análise e snapshot correspondentes;
- atualizar status da proposta de forma transacional;
- registrar auditoria.

O `DecisionService` não conhecerá `DecisionEntity`, `DecisionRepository` ou detalhes JPA.

Testes:

- decisão aprovada/rejeitada/solicitação de informação;
- justificativa obrigatória;
- usuário não autorizado;
- análise inexistente ou snapshot divergente;
- repetição de decisão conforme regra atual;
- auditoria com autor, motivo e timestamp.

### B4. Porta de análise

Manter `AnalysisDataPort` e separar as responsabilidades de persistência hoje concentradas no `AnalysisService`:

- ciclo de vida da análise e lease;
- snapshot documental;
- dados extraídos e findings;
- avaliação financeira;
- fingerprint e idempotência.

A primeira versão deve manter uma única porta coesa se a interface continuar pequena e sem expor entidades. Só dividir em portas menores quando os testes demonstrarem responsabilidades independentes.

Adapters existentes devem absorver `AnalysisEntity`, `AnalysisRepository`, `AnalysisDataStore` e consultas JDBC/JPA.

Testes:

- duas requisições concorrentes;
- mesma chave com mesmo payload;
- mesma chave com payload diferente;
- tentativa antiga após lease expirado;
- falha externa sem perda de estado;
- resultado tardio não sobrescreve tentativa nova;
- snapshot e hash imutáveis.

### B5. Regra arquitetural

Fortalecer `ArchitectureTest` gradualmente:

- `..application..` não depende de `..infrastructure..`;
- `..domain..` não depende de Spring, JPA ou infraestrutura;
- controllers não dependem de repositories/entities;
- adapters de infraestrutura podem depender das portas de aplicação, mas não o inverso.

A regra deve ser ativada por módulo somente depois da migração daquele módulo. Não criar uma regra global que falhe por dezenas de violações conhecidas sem uma tranche de correção correspondente.

## Sequenciamento de execução

### Marco 1 — PIT domínio

- baseline detalhada;
- testes do `FinancialEngine`;
- PIT novamente;
- unitários e integração verdes.

### Marco 2 — Documentos

- `DocumentDataPort` e adapter;
- testes de versões, snapshot e storage;
- regra ArchUnit para document;
- PIT novamente.

### Marco 3 — Decisão

- `DecisionDataPort` e adapter;
- testes de autorização, transação e auditoria;
- regra ArchUnit para decision;
- PIT novamente.

### Marco 4 — Análise

- consolidar/refinar `AnalysisDataPort`;
- retirar os imports de infraestrutura do `AnalysisService`;
- preservar lease, idempotência e late-result guard;
- integração Testcontainers completa;
- PIT final.

## Critérios de conclusão

O trabalho só será considerado concluído quando:

- `mvn test` e `-Pintegration verify` passarem;
- ArchUnit comprovar as fronteiras migradas;
- nenhum serviço de aplicação migrado importar entidades/repositories de infraestrutura;
- PIT tiver execução estável, sem timeout não explicado, e score documentado;
- o limiar de mutation score estiver configurado de forma progressiva e justificável;
- frontend, Python, Compose e higiene de credenciais continuarem passando;
- `QUALITY_AUDIT_V3.md` registrar métricas antes/depois e pendências remanescentes.

## Riscos e contenções

| Risco | Contenção |
|---|---|
| Refatoração altera regra de negócio | testes de contrato e integração antes/depois de cada módulo |
| Portas viram pass-through de JPA | usar records sem tipos de infraestrutura |
| PIT incentiva testes artificiais | exigir asserções semânticas e revisar mutantes sobreviventes |
| Testes ficam lentos/instáveis | domínio em unitários; banco e adapters em integração |
| Migração parcial quebra Spring DI | manter um adapter concreto por porta e validar contexto Spring |
| Score aumenta por exclusão | registrar toda exclusão e rejeitar exclusões cosméticas |
