# Critérios de aceite e severidade

## P0 (bloqueia pronto)
- Os 21 testes de integração existentes **realmente executados** com resultados de assertivas, não erro de Testcontainers.
- Fluxos críticos de AnalysisService, FinancialEngine, estado/análises, persistência, idempotência/concorrência comprovados.
- E2E com AWS Bedrock e Consult Center sandbox REAL na mesma jornada; não usar mock como substituto.
- Cálculos 30%, tolerância 3% por competência, três holerites e três extratos, bureau prévio, IA apenas hipótese/explicação e decisão humana preservados.
- Sem alteração que comprometa histórico, dados, segredos ou infraestrutura operacional.

## Metas de qualidade (B)
- Java >=75% linhas e >=60% branches medidos em JaCoCo com execução limpa; caso falte, explicitar razões, não falsificar gate.
- Python e React com relatórios reais de cobertura; verificar workflows críticos, sem percentual global enganoso.
- Unitários, integration, build, Ruff, ESLint, Prettier, Checkstyle, ArchUnit passam.
- SCA/SAST executados se ferramentas/rede disponíveis; caso bloqueados reportar como NÃO VALIDADO, nunca 'zero vulnerabilidades'.

## Severidade
- P0: integração indisponível, erro financeiro, dados incorretos, falha de persistência/consistência, fallback mock disfarçado, vazamento de segredo.
- P1: caminhos importantes sem cobertura, contratos/erros pouco testados, cobertura insuficiente, security scans sem evidência.
- P2: melhoria de métricas e manutenção não essencial à PoC.

## Definição dos estados
- PASS: testado nesta execução, saída verificável e esperado atendido.
- FAIL: executado, observado diverge do esperado.
- BLOCKED: impedimento demonstrado por ambiente/externo/autorização.
- NOT_RUN: não tentado; motivar.

## Critério do relatório
Registrar revisão SHA, versões das ferramentas, timestamps, ambiente, testes passados/falhados/bloqueados, cobertura por módulo, lista de arquivos modificados, diferenças contra baseline, riscos restantes e próximos passos. Não prometer execução posterior.
