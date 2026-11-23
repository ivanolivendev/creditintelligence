# Plano operacional — etapas e dependências

## Preparação / Fase 0: inventário (orquestrador)
- Registrar SHA/revisão Git, arquivos não rastreados e arquivos alterados ANTES de executar; não sobrescrever.
- Localizar documentação V3.4 e blueprint V3; identificar Compose/serviços reais; comandos disponíveis.
- Criar `quality-agents/reports/BASELINE.md`, contendo comandos e pré-condições.
- Não copiar resultados da auditoria como se fossem resultados atuais.

## Fase 1: ambiente de testes e baseline
**Dono:** `agents/test-infrastructure.md`.
- Diagnosticar Testcontainers: 21 errors `Could not find a valid Docker environment` no Maven dentro de Docker.
- Priorizar execução Maven host com Docker daemon acessível e JDK correto; alternativa: runner próprio para Testcontainers, com segurança e aprovação. NÃO conceder Docker socket a contêineres por padrão.
- Executar todos os testes de integração em PostgreSQL real e preservar logs sanitizados.
- Medir JaCoCo após `clean` e execução completa correta; reportar métricas do XML, em especial `LINE` e `BRANCH`, fórmula `covered / (covered + missed)`.
- Corrigir discrepância auditoria anterior: 808/(808+415)=66,07%? Não! = 66,07% **se total reportado for 1223**, mas 808 + 415 = 1223; texto declarou 1123. Conferir origem dos três números. Branches 288/(288+398)=41,98%.
**Gate:** ITs executam ou diagnóstico de ambiente BLOCKED claramente demonstrado; em BLOCKED não seguir declarando cobertura de integração.

## Fase 2: testes do backend Java
**Dono:** `agents/java-test.md`.
- Foco AnalysisService (transições, condições), AIExplanationService, ConsultCenterSandboxAdapter, DataStore, DocumentService, DecisionService.
- Testar 3 PDFs + 3 extratos, média líquida, 30%, tolerância salarial 3%, obrigação consignada não duplicada, bureau manual obrigatório, `restrictions=null` diferente de zero, IA sem efeito em cálculo.
- Testar idempotência, fingerprints, persistência atômica, rollback, execução concorrente, lease/attempt/stale completion, erros 4xx/5xx, timeouts e validação de evidências.
- Preferir testes comportamentais, mocks apenas nas unitárias; integração usa banco real e adapters adequados.
**Gate:** suíte Java verde, cenários P0 testados e evidenciados.

## Fase 3: qualidade e refatoração localizada
**Dono:** `agents/code-quality.md`.
- Identificar métodos reais mais complexos (o valor 185 do JaCoCo é complexidade agregada da classe, não de método).
- Só refatorar após testes de caracterização; preferir extrair lógica de fluxo ou value objects sem alterar regras, assinaturas públicas ou esquemas.
- Validar resultado financeiro pré/pós com fixtures idênticos, garantir semântica de estados e transação; medir cobertura novamente.
**Gate:** zero regressão, diff pequeno justificável, ArchUnit/checkstyle verdes.

## Fase 4: qualidade Python e React
**Dono:** `agents/python-frontend-qa.md`.
- Adicionar medição de cobertura com dependências de teste apropriadas (respeitando pin e lockfile), não mudar runtime sem necessidade.
- Python: extração, dados ausentes, validação Pydantic, blocos Converse, JSON inválido, tempo limite e ausência de mock silencioso.
- React: proposta/CPF sintético, bureau manual, upload, análise, dossiê com hipóteses, estados parciais/falhas, auth e mensagens de erro.
**Gate:** pytest/Vitest verdes e relatórios de cobertura reais.

## Fase 5: E2E (ambiente autorizado)
**Dono:** `agents/integration-e2e.md`.
- Somente dados sintéticos e CPF permitido pelo sandbox. Não assumir que o nome sintético é retornado pelo bureau.
- Provar HTTP real da Consult Center e Bedrock Converse com identifiers/timestamps sanitizados, comprovando provider/modelo efetivos; E2E all PASS também.
- Criar proposta, 6 PDFs, consultar bureau manualmente, analisar, gerar dossiê, revisão humana, verificar DB e UI.
- Com indisponibilidade externa: `BLOCKED`, nunca `PASS` com fixture.
**Gate:** jornada real executada e documentada.

## Fase 6: gate final e entrega
**Dono:** `agents/quality-gate.md`.
- Reexecutar suítes e estática; validar cobertura limpa e metas; SAST/SCA quando rede permitir; inventário de secrets sem divulgar valores.
- Relatório com antes/depois, comandos reais, bloqueios, diferenças e avaliação de prontidão.
- Se um P0 falhar, declarar NOT_READY ou BLOCKED; não compor porcentagem global sem denominador homogêneo.

## Sequenciamento e concorrência
Fases 1→2→3; fase 4 pode executar em paralelo com 2 apenas se não houver arquivo compartilhado; fase 5 após baseline e contratos; fase 6 final. Cada agente registra arquivos tocados e handoff para o próximo.
