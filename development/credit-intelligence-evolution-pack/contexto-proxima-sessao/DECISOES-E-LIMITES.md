# Decisões e limites para a evolução

As decisões abaixo combinam regras já fechadas no repositório com o escopo explicitamente proposto pelo Evolution Pack 4.0. Não são autorização para ampliar o produto.

## Arquitetura e responsabilidades

- Preservar Java 21/Spring Boot como monólito modular e a organização por módulos/camadas já usada. Não criar microserviços novos para a evolução.
- Java mantém autoridade sobre transações, snapshots, policy versionada, idempotência, regras financeiras determinísticas, simulações, findings persistidos, dossiê e decisões humanas.
- Python/FastAPI realiza extração tipada de documentos e integra a explicação Bedrock já existente. Não aprova, rejeita, calcula resultado financeiro autoritativo nem grava o schema transacional Java.
- React/TypeScript permanece a interface da pessoa analista; PostgreSQL/Flyway e storage local mantêm persistência e versões.
- Bedrock explica um `CreditAssessment` calculado pelo Java. A saída do modelo é não confiável, validada contra códigos/referências permitidos e não altera montantes, findings, política ou decisão.
- Bureau entra por porta de aplicação/domínio e adapter de infraestrutura, com modelo normalizado independente do DTO do fornecedor. Adapter/cliente e contrato reais devem seguir convenções observadas no código.

## Escopo do Evolution Pack 4.0

### P0 — núcleo documental demonstrável

- Três contracheques e três extratos, usando layouts sintéticos controlados.
- Extração estruturada por documento e competência/período, com status e evidências específicas.
- Perfil financeiro consolidado; renda de referência, variabilidade, conciliação salarial e compromissos sem dupla contagem.
- Regras determinísticas, capacidade teórica e simulação Price em Java com `BigDecimal`, precisão/moeda/arredondamento explícitos.
- Assessment, evidências, limitações e snapshots versionados; dossiê e revisão humana.
- Explicação Bedrock pós-cálculo, reutilizando a integração, com provider/modelo real ou mock claramente identificados.
- Cobertura de testes sem depender de chamadas pagas ao Bedrock.

### P1 — bureau de crédito em sandbox, condicionado à verificação

- Investigar documentação oficial do provedor e ambiente sandbox disponível antes de selecionar ou implementar fornecedor.
- Implementar um único adapter sandbox somente quando contrato, autenticação, credenciais de teste e casos de teste estiverem comprovados.
- Exibir provider e `SANDBOX` claramente; normalizar resposta e preservar status/fonte/versão.
- Timeout, erro HTTP, autenticação falha ou sandbox indisponível resultam em `UNAVAILABLE`, nunca em zero restrições.
- A indisponibilidade do bureau não bloqueia análise documental.
- Se não houver acesso verificável, concluir o P0 e manter adapter/fixture local (`MOCK`) identificado; registrar P1 como `PARTIAL` ou `BLOCKED` com a evidência concreta. Não inventar fornecedor, credencial, resposta ou integração.

## Limites da PoC

- Runtime e testes de Java devem usar Docker Compose; não exigir instalação local de JDK/Maven para o fluxo normal.
- Execução local em Compose. Não provisionar recursos, fazer deploy, publicar imagens/código ou rodar Terraform apply na AWS.
- Bedrock é uma chamada externa autorizada já existente; usar somente quando credenciais estiverem disponíveis e a verificação realmente precisar. Favorecer testes mock/determinísticos e evitar chamadas repetidas.
- Sandbox de bureau é a integração externa condicional explicitamente prevista no Evolution Pack; usar somente credenciais/documentação de sandbox e identificadores de teste fornecidos pelo próprio provedor.
- Apenas dados sintéticos. Não usar CPF/documento real nem afirmar autenticidade documental ou decisão bancária real.
- Não adicionar OCR universal, scraping, SCR/Open Finance, Kafka/RabbitMQ/SQS, Kubernetes, score de inadimplência treinado, aprovação automática ou nova infraestrutura/microserviço.
- Reutilizar endpoints e tabelas existentes quando comportarem o contrato sem perda de clareza. Migrations devem ser aditivas e preservar histórico.

## Regras de integridade financeira e evidência

- Dinheiro e cálculos persistidos: `BigDecimal`; entradas e saídas com moeda e arredondamento explícitos. Nunca `double` para resultado financeiro.
- Campos `MISSING`, `AMBIGUOUS` ou `INVALID` não são preenchidos por palpite do modelo.
- Depósito bancário não classificado não vira renda automaticamente; créditos desconhecidos seguem desconhecidos.
- Não reutilizar uma mesma transação para conciliar múltiplos salários sem regra explícita.
- Não descontar novamente do líquido uma obrigação já retida no contracheque; se a correspondência não for segura, exigir revisão.
- Guardar origem, documento/versão/página/trecho e snapshot/policy version. Nova versão documental ou novo relatório de bureau implica novo input/snapshot de análise; não sobrescrever o histórico.
- Taxa, renda, comprometimento e capacidade devem vir da regra/política e dados observáveis. Valores de exemplo do pacote não são automaticamente oráculos sem cálculo independente.
- O resultado é pré-análise para revisão humana. Não é aprovação/negação, score de inadimplência, oferta ou custo total (CET/IOF/tarifas).

## UX

- Mostrar campo declarado, valor extraído, diferença, fonte/evidência, regra/limite e próximo passo em português claro.
- Separar fatos extraídos, cálculos Java, contexto do bureau, explicação IA e decisão humana.
- Ausência/ambiguidade deve explicar o que falta e como resolver; nunca usar código cru ou nome interno de regra como única mensagem.
- Exibir provider e ambiente observados (`MOCK`, `SANDBOX`, `BEDROCK`/real conforme contrato atual), sem rótulo enganoso.
- Preservar acessibilidade e linguagem visual existente; inspecionar a UI real antes de propor redesign.
