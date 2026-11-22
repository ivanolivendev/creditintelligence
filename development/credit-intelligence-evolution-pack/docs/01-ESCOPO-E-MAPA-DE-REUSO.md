# 01 — Escopo, valor e mapa de reúso

## Problema/valor

O fluxo original do Blueprint V3 verifica informações declaradas contra documentos, gera achados e explicações; seu escopo financeiro é deliberadamente pequeno (seções 1–2, 17–20). O objetivo da evolução é **analisar conjuntos documentais** e preparar informação útil ao analista: renda mensal, estabilidade, conciliação salarial, compromissos conhecidos, capacidade simulada, achados, evidências, informações externas e limitações.

A PoC **não** aprova/nega crédito automaticamente, estima PD sem treinamento, oferece taxa real ao solicitante, verifica autenticidade documental em fonte oficial, identifica todas as dívidas existentes ou consulta CPFs sem base legal.

## Matriz de transição (proposta condicionada à auditoria)

| Camada/capacidade no Blueprint V3 | Ação | Mudança mínima | Por quê |
|---|---|---|---|
| React + TS/Vite | MANTER/ESTENDER | fluxo de 6 PDFs, indicadores e fontes, sandbox badge | UI existente é base da jornada |
| Java 21/Spring Boot, monólito modular | MANTER | novo core no domínio `analysis` ou módulo `creditanalysis` conforme dependências | preservar transações e segurança |
| PostgreSQL/Flyway | MANTER/ESTENDER | tabela ou JSONB versionado para assessment/consulta externa | histórico e rastreabilidade |
| Python/FastAPI | MANTER/ESTENDER | extratores por tipo, contratos tipados | especialização documental |
| Integração Bedrock | MANTER/ADAPTAR | enviar assessment + findings para explicação após regras Java | IA interpreta, não decide |
| Upload e storage | MANTER/ESTENDER | aceitar séries tipadas (3 competências e 3 meses) | preservar hash, versão e proveniência |
| Rule Engine inicial (`INCOME_CONSISTENCY` etc.) | REAPROVEITAR/EXPANDIR | novas regras de renda/conciliação/comprometimento | não jogar fora regra válida |
| Policy versionada | MANTER/ESTENDER | parâmetros configuráveis, assinatura/versão | reprodutibilidade |
| Idempotência/locks/snapshots | MANTER/REVALIDAR | incluir fontes novas e bureau no fingerprint | evitar análises contraditórias |
| Dossiê/decisão humana | MANTER/ESTENDER | seção de indicadores, simulação e review | aproveita ciclo atual |
| `FinancialProfileAssembler` | ADICIONAR | compor dados normalizados e evidências | contrato estável para engine |
| `CreditAnalysisEngine` | ADICIONAR | regras e cálculos puros Java | valor funcional novo |
| `CreditBureauPort` + adapter | ADICIONAR | fornecedor selecionado em sandbox | integração desacoplada |
| CSV/OFX/OCR universal | ADIAR | nenhum | dois dias e pouca previsibilidade |
| SQS/Kafka/RabbitMQ, novos microserviços | NÃO ADICIONAR | manter runtime atual | sem requisito que compense esforço |

## Precisa 'desplugar' algo?

**Não remover** upload, orquestrador, o motor de regras atual, cliente Bedrock, endpoints atuais ou dados históricos. O 'desplugue' é **substituir o ponto lógico em que a análise assume um único PDF/um único campo** por um pipeline capaz de aceitar um `FinancialProfile` e executar múltiplas regras. Se a versão antiga tiver consumidores, manter rota ou contrato legado com compatibilidade temporária e ativar o novo fluxo por configuração/versão.

## Entrega P0 definida

1. Uma proposta recebe 3 `PAYSLIP` e 3 `BANK_STATEMENT` (PDF digital, fixtures conhecidas).
2. Extrair campos com `status` e `evidenceRefs`; consolidar apenas os confiáveis.
3. Calcular renda base, faixas, variabilidade, correspondência de salários, obrigações declaradas e capacidade teórica.
4. Permitir valor/prazo/taxa e simular prestação e principal, com input e política persistidos.
5. Gerar assessment estruturado e explicação IA com provider REAL/MOCK visível.
6. Exibir dossiê, achados, origem e revisão humana; preservar versões.

## P1 opcional

Consulta externa sandbox por CPF sintético, com resposta normalizada, sem atribuir status `no restrictions` a erros. Se sandbox falhar, fixture controlada + relato honesto. Em produção, integração comercial e finalidade legal precisam ser validadas.
