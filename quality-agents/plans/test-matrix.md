# Matriz mínima de testes (a ampliar com o código real)

| ID | Nível | Componente | Caso de teste / invariantes | Prioridade |
|---|---|---|---|---|
| INF-01 | ambiente | Testcontainers | Maven vê Docker e cria PostgreSQL isolado | P0 |
| INF-02 | integração | PostgreSQL | 21 testes existentes rodam sem erro de inicialização | P0 |
| DB-01 | integração | persistência | FK, round-trip JSONB, rollback e transações | P0 |
| ANA-01 | unitário | AnalysisService | sem bureau válido: bloqueio controlado | P0 |
| ANA-02 | integração | AnalysisService | snapshot da consulta na análise, histórico imutável | P0 |
| ANA-03 | integração | AnalysisService | idempotência: mesma chave/dados retorna resultado consistente | P0 |
| ANA-04 | integração | AnalysisService | fingerprint conflitante gera resposta de conflito | P0 |
| ANA-05 | integração | AnalysisService | concorrência simultânea e não duplicação de efeitos | P0 |
| ANA-06 | integração | AnalysisService | lease/attempt e conclusão tardia rejeitada | P0 |
| ANA-07 | integração | AnalysisService | falha Python/Bedrock sem marcar AI mock válido | P0 |
| FIN-01 | unitário | FinancialEngine | média (5800+6200+6600)/3=6200 | P0 |
| FIN-02 | unitário | FinancialEngine | capacidade max(0,6200*0.30-450)=1410 | P0 |
| FIN-03 | unitário | FinancialEngine | competência 6200 vs depósito 6050: 2,419% dentro 3% | P0 |
| FIN-04 | unitário | FinancialEngine | 3% exatos; acima da tolerância alerta | P0 |
| FIN-05 | unitário | FinancialEngine | consignado descontado do líquido não soma novamente | P0 |
| FIN-06 | unitário | FinancialEngine | UNKNOWN/hipótese IA não altera renda/obrigação | P0 |
| FIN-07 | unitário | FinancialEngine | renda essencial ambígua gera PARTIAL sem capacidade | P0 |
| BUR-01 | integração | bureau | consulta exclusivamente manual; sem consulta na criação/upload | P0 |
| BUR-02 | contrato | bureau | restrictions null não é lista vazia | P0 |
| BUR-03 | unitário | bureau | erro/timeouts e resposta inválida, persistência segura | P1 |
| AI-01 | contrato | Python | Bedrock configurado não retorna MOCK quando todos PASS | P0 |
| AI-02 | contrato | Java/Python | schema estruturado, hipóteses e evidenceRefs válidas | P0 |
| AI-03 | unitário | Python | resposta Converse com múltiplos content blocks / JSON inválido | P1 |
| DOC-01 | integração | documentos | 3 holerites+3 extratos de mesmas competências | P0 |
| DOC-02 | integração | documentos | evidências documento+versão+página+campo | P1 |
| UI-01 | frontend | React | criação com CPF sintético autopreenchido e renda declarada | P1 |
| UI-02 | frontend | React | fluxo bureau manual, hipótese IA visível, estados de erro | P1 |
| E2E-01 | real | sistema todo | Java+Python+PostgreSQL+Consult Center+Bedrock+dossiê+decisão | P0 |
| E2E-02 | real | Bedrock | caso todos findings PASS invoca AWS mesmo assim | P0 |
| SEC-01 | scan | repositório | segredos não rastreados/logados, dependências auditadas | P1 |

Para cada caso registrar: arquivo/teste, comando, execução real, esperado, observado, PASS/FAIL/BLOCKED/NOT_RUN, evidência sanitizada. Teste hipotético não equivale a teste implementado.
