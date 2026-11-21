# Prompt para iniciar a próxima sessão

Copie o bloco abaixo para a nova sessão do Codex aberta no repositório.

```text
Vamos implementar a evolução documentada no Evolution Pack 4.0, nesta codebase:

C:\Users\siiva\OneDrive\Documentos\codex\creditintelligence

O pacote principal está em:

development\credit-intelligence-evolution-pack

Leia primeiro, sem pular:
1. AGENTS.md da raiz;
2. development\AGENTS.md;
3. development\Credit_Intelligence_Master_Blueprint_V3.docx;
4. development\docs\PLANO-IMPLEMENTACAO.md;
5. development\credit-intelligence-evolution-pack\README.md;
6. todos os arquivos do Evolution Pack na ordem definida pelo README, inclusive contratos, casos de teste e relatório de execução;
7. todos os arquivos de contexto em development\credit-intelligence-evolution-pack\contexto-proxima-sessao\.

Objetivo: executar a evolução prevista pelo pacote, não apenas revisar ou reescrever o plano. O pacote e as instruções do projeto são a fonte do escopo; não remova requisitos por preferência própria. Se encontrar conflito real entre fontes, mostre o trecho/fonte, explique o impacto e proponha a menor resolução antes de codificar.

CONTEXTO E LIMITES IMPORTANTES
- Esta é uma evolução de um produto já existente. Primeiro audite o código e preserve contratos e comportamento funcionando; não trate o Blueprint ou exemplos do pacote como prova de implementação.
- O runtime e todo build/teste Java devem continuar locais em Docker Compose. Não provisionar ou publicar infraestrutura AWS, não executar Terraform apply e não fazer deploy nesta tarefa.
- A integração Amazon Bedrock já existe no projeto como explicação opcional, chamada pelo serviço local com credenciais temporárias. Reutilize-a. Ela deve explicar um assessment determinístico já calculado em Java; não deve calcular renda/capacidade, mudar findings ou tomar decisão.
- O adapter de bureau em sandbox é parte do objetivo descrito no Evolution Pack. Investigue o fornecedor, documentação, ambiente e credenciais existentes antes de implementar. Faça a integração remota somente se contrato e acesso de teste forem verificáveis. Se não forem, implemente/preserve porta e fixture local claramente MOCK, conclua o que puder do P0 e registre P1 como PARTIAL/BLOCKED com evidência concreta. Não invente provedor, endpoint, credencial ou resposta real.
- Use exclusivamente dados sintéticos e fixtures. Não consulte CPF real nem afirme que documento foi autenticado em fonte oficial.
- Minimize custo: testes mock/determinísticos primeiro; nenhuma chamada Bedrock paga em cada teste. Use no máximo a chamada real necessária para confirmar a explicação final, se credenciais estiverem disponíveis. Chamada ao bureau apenas no sandbox oficial e com identificador de teste documentado.

ESCOPO FUNCIONAL DO PACOTE
- P0: suportar três contracheques e três extratos de layouts sintéticos definidos; extração tipada com status e evidência por campo/transação; consolidação de perfil financeiro; renda de referência/variabilidade; reconciliação salarial; deduplicação de obrigações; capacidade teórica; simulação Price; findings e dossiê versionados; revisão/decisão humana; UX mínima clara; testes sem dependência de serviço pago.
- P1: uma integração de bureau via porta e adapter sandbox, se documentação/credenciais oficiais estiverem disponíveis. Timeout/401/429/5xx viram UNAVAILABLE, nunca “zero restrições”; indisponibilidade do bureau não bloqueia análise documental.
- Siga critérios e cenários em `docs/05-PLANO-EXECUCAO-E-TESTES.md`, `docs/06-SEGURANCA-LIMITES-E-DEMO.md` e `examples/CASOS-DE-TESTE.md`.

INVARIANTES TÉCNICOS
- Java 21 + Spring Boot modular monolith, Maven, PostgreSQL/Flyway; Python FastAPI/Pydantic; React/TypeScript/Vite; Docker Compose. Não trocar stack nem criar novos microserviços.
- Java é autoridade transacional e de negócio. Python extrai/normaliza e integra explicação; LLM é saída não confiável. Não duplicar regras de negócio em prompt/controller/frontend.
- Dinheiro com BigDecimal, moeda e arredondamento explícitos. Campos ausentes/ambíguos/inválidos não podem ser adivinhados. Depósito desconhecido não se torna renda.
- Não contar transação duas vezes nem descontar novamente do líquido obrigação já descontada no contracheque; conflitos levam a revisão.
- Preservar documentos, snapshots, policy/version, idempotência, tentativas/leases, decisão humana e histórico. Novas migrations são aditivas. Não sobrescrever resultado histórico.
- Mock, Bedrock e bureau sandbox devem ser rotulados de acordo com o provider e ambiente realmente executados.
- Não implementar OCR universal, scraping, Open Finance/SCR real, filas, Kubernetes, score de inadimplência treinado ou concessão automática.

MODO DE TRABALHO
1. Dê uma atualização curta e comece pelo inventário. Registre `git status --short` e diffs sem apagar nem descartar trabalho pré-existente. Na baseline anterior, os arquivos apareceram como não rastreados; confirme o estado atual e não faça reset, limpeza, staging amplo ou commit sem conferir cada arquivo relacionado.
2. Verifique Docker/Compose, containers/health, portas, logs, OpenSpec atual e os harnesses disponíveis. Não presuma que a sessão anterior deixou serviços ativos.
3. Rode os smoke/testes já existentes antes de alterar código e registre baseline (PASS/PARTIAL/FAIL/BLOCKED). Falhas antigas ficam separadas de regressões.
4. Monte uma matriz EXISTENTE/PARCIAL/AUSENTE/NÃO VERIFICADO para o escopo, apoiada em arquivos/endpoints/testes. Mostre um plano vertical pequeno e arquivos previstos antes da primeira alteração.
5. Consulte os procedimentos `credit-implementation`, `credit-verification` e `credit-devops` se essas skills estiverem disponíveis no ambiente; use as instruções de AGENTS e OpenSpec. Não inicialize OpenSpec outra vez nem sobrescreva configuração existente.
6. Crie uma mudança OpenSpec estreita para a evolução após verificar o estado atual. Implemente incrementalmente começando por domínio Java determinístico e fixtures, depois contratos/extração, integração/persistência, UI e integrações opcionais.
7. Execute os gates relevantes em Compose e os scripts existentes. Faça revisão final do diff, segurança, compatibilidade e UX. Não marque teste não executado como PASS nem use `docker compose down -v`.
8. Preencha `agent/02-RELATORIO-EXECUCAO.md` com evidências reais, comandos e status por gate. Se alterar seu conteúdo atual, preserve informação útil e não invente resultados.

COMUNICAÇÃO E ENTREGA
- Mantenha atualizações visuais curtas durante o trabalho: fase atual, gate e resultado/impedimento. Mostre status com PASS/PARTIAL/FAIL/BLOCKED e evidência concreta.
- Interface e feedback devem ser em português claro para analistas; separar fatos extraídos, cálculos, bureau, explicação IA e decisão humana. Exibir evidência e próximo passo, não apenas códigos internos.
- Ao final, informe o que mudou, como rodar, comandos realmente executados, resultado dos gates, se Bedrock e bureau foram realmente chamados, o que está pendente e como ver a jornada local.
- Continue até concluir tudo que estiver verificável localmente. Pare apenas em bloqueio externo real (ex.: ausência de credencial sandbox), registre a evidência e prossiga com as partes independentes.

Comece agora pela auditoria do repositório e do ambiente local. Não faça deploy AWS.
```
