# Agente 5 — End-to-End REAL

**Objetivo:** demonstrar proposta → 6 PDFs → bureau manual → Java/Python → FinancialEngine → AWS Bedrock → dossiê → decisão humana com persistência verificável.

## Preparação
- Usar arquivos sintéticos autorizados em `documentos-para-analise` se existirem, com prévia inspeção dos campos; não alterar documentos para 'passar' testes.
- Usar CPFs sintéticos disponibilizados pelo sandbox e identidade correspondente do cadastro demo; não supor nome por CPF ou usar CPF real.
- Exigir `BUREAU_MODE=sandbox` e `AI_PROVIDER=bedrock` **efetivamente no processo em execução**, sem imprimir tokens.
- Confirmar autorização e limites de custo AWS antes de chamadas reais; limitar volume a poucos testes úteis.

## Cenário principal
1. Criar proposta com renda declarada (exemplo 8000) para identidade sintética válida.
2. Upload de 3 contracheques e 3 extratos do mesmo período, conferir evidências e extração.
3. Consulta bureau acionada manualmente, retorno HTTPS real persistido, inclusive `restrictions=null` preservado.
4. Iniciar análise, validar média e consistência, 30%, 3%, duplicidade consignado.
5. Bedrock `Converse` real, verificar provider/model ID, parser, hipóteses, evidenceRefs; não confundir modelo usado com label comercial.
6. Dossiê/UI, histórico e decisão humana, com análise vinculada.
7. E2E adicional com todos findings PASS; IA ainda deve ser chamada.

## Erros controlados
- Sem bureau: bloqueio; documentos incompletos: bloqueio ou partial conforme regra V3.4; falha AWS: UNAVAILABLE sem MOCK; falha bureau: indisponibilidade explícita.
- Capturar HTTP, logs sanitizados, IDs internos, timestamps e DB. Não emitir headers auth/chaves, CPF real, dados sensíveis.
- Caso externo indisponível: BLOCKED, não PASS por fixture; mock tests ficam em relatório separado.

## Entrega
`reports/05_E2E_REAL.md`: cenário, esperado, observado, ambiente, status e prova; E2E não é só `curl` isolado.
