# COMANDO MESTRE — PARA O AGENTE QUE VAI ALTERAR O REPOSITÓRIO

Você é o agente executor do **Credit Intelligence Evolution Pack 4.0**. Sua tarefa é **alterar incrementalmente o repositório existente**, preservando a arquitetura hexagonal/modular e a integração já disponível com Bedrock. **Leia integralmente todos os arquivos deste pacote antes de codificar.**

## Missão

Entregar uma PoC local verificável de **pré-análise financeira documental**: 3 contracheques e 3 extratos de layouts sintéticos controlados, consolidação em perfil tipado, regras e simulação em Java, dossiê com evidências e explicação via Bedrock já existente; adicionar uma porta de bureau de crédito independente de fornecedor e **um** adapter sandbox real somente se autenticação e contrato forem comprovados. Caso contrário, manter fixture local claramente sinalizada.

## Etapa 0 obrigatória — descoberta, sem suposições

1. Inventarie estrutura, módulos, endpoints, DTOs, migrations, testes, Docker Compose, env/config, políticas, fluxo Java ↔ Python ↔ Bedrock e frontend.
2. Execute smoke test e testes existentes; anote comandos, saídas e falhas. Não apresente falhas pré-existentes como causadas pela mudança.
3. Compare Blueprint V3 e pacote 4.0 com código real; produza uma matriz `EXISTENTE / PARCIAL / AUSENTE / NÃO VERIFICADO` por capacidade.
4. Identifique as adequações já realizadas no repositório: **código observado prevalece sobre nomes hipotéticos neste pacote**, desde que não viole invariantes de segurança.
5. Proponha o menor caminho de alteração **antes** de escrever novos componentes. Não reescreva integrações funcionais.

## Prioridade de execução (obrigatória)

**P0:** múltiplos contracheques e extratos sintéticos; extração/evidência; perfil financeiro; regras calculadas; simulação; persistência/versionamento e UI mínima; testes críticos.

**P1:** adapter HTTP sandbox de bureau com contrato validado, mapping, timeout, auth, tratamento de erro e identificação visível `SANDBOX`; integração da informação no dossiê.

**P2:** layout visual refinado, métricas adicionais, ampliação de layouts e cloud.

Se houver conflito de tempo, **reduza P1 antes de sacrificar P0**. Não implemente OCR universal, scraping, score preditivo, filas, Kubernetes, integração SCR real, Open Finance, consulta real de pessoa física sem autorização ou sistema de concessão autônoma.

## Orientações de arquitetura

- **Java 21/Spring Boot:** autoridade transacional e de negócio, regras determinísticas, simulação Price com `BigDecimal`, policies versionadas, idempotência, decisões, auditoria e dossiê estruturado.
- **Python/FastAPI:** extração de PDF, normalização textual e cliente Bedrock existente; não toma decisões nem altera resultados determinísticos.
- **Bedrock:** reutilizar cliente, config, controle de erro e schema quando compatíveis; **ajustar o fluxo** para explicar o `CreditAssessment` produzido pelo Java, não inferir montantes diretamente do PDF ou substituir regras.
- **Bureau:** porta `CreditBureauPort` no lado de aplicação/domínio (conforme convenções reais), adapter HTTP de infraestrutura, mapping anticorrupção. Não deixar DTO do fornecedor atravessar a porta.
- **Banco:** manter migrations existentes; criar apenas migrations aditivas necessárias, sem perder histórico. Dado externo é uma fonte versionada, não um PDF fictício.
- **Frontend:** distinguir campo declarado, extraído, inferência/classificação, regra, simulação, consulta externa sandbox e decisão humana.

## Regras de execução e transparência

- Nunca afirmar que IA REAL, consulta REAL, OCR, AWS, score ou qualquer integração está ativa sem teste verificável.
- Não hardcodar credenciais, CPFs reais, tokens em prints, logs ou Git.
- Não criar respostas sintéticas que se apresentem como resposta real de terceiros.
- PDF com `MISSING`, `AMBIGUOUS` ou `INVALID`: nunca preencher o dado financeiro por palpite de LLM.
- Ausência/timeout do bureau **não** significa `restrictionCount=0`.
- Não misturar score de provedores sem guardar escala/modelo; score não é PD.
- Guardar sourceRefs e policyVersion; nova versão documental ou novo relatório externo requer nova análise/snapshot.
- Não contar parcelas duas vezes quando já descontadas no líquido do contracheque; usar reconciliação e incerteza explícitas.
- Não criar novos endpoints e tabelas se os existentes comportarem evolução tipada e versionada.

## Plano de entrega

1. Descrever diff planejado: arquivos a **manter/alterar/adicionar**, migrations, efeitos em APIs e compatibilidade.
2. Implementar uma fatia vertical por vez; testes e logs de resultado após cada fatia.
3. Primeiro testar core Java com fixtures sem serviços externos; só então integrar extração e Bedrock.
4. Testar bureau sandbox via requisição HTTP documentada **se** credentials/test-cases oficiais estiverem efetivamente disponíveis; verificar campos antes de mapear. Sem isso: fixture com marca MOCK e status claro.
5. Rodar smoke E2E em Compose e validar ambiente de IA real/mock no resultado e na UI.
6. Registrar em `agent/02-RELATORIO-EXECUCAO.md` status e evidências finais, pendências e limitações.

## Entrega obrigatória

Código alterado, migrations reversíveis/aditivas, testes, fixtures sintéticas, OpenAPI/DTOs documentados, tela funcional mínima, README atualizado, matriz Blueprint → implementação e roteiro de demonstração. A definição de pronto é `docs/05-PLANO-EXECUCAO-E-TESTES.md`. Não anunciar conclusão sem executar os testes.
