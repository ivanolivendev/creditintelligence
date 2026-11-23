# Prompt para Codex — verificação independente

Leia todos os relatórios de `quality-agents/reports/`, a matriz e o plano V3.4. Sem alterar código de produção, verifique na revisão atual:

1. O git diff não inclui mudanças indevidas no motor financeiro, API pública, Compose de produção, migrações aplicadas ou segredos.
2. Os 21 testes de integração executaram com Testcontainers real, com PostgreSQL e sem bypass.
3. A medição JaCoCo foi gerada após build limpo; confira XML e denominadores; destaque discrepâncias.
4. Testes Java, Python, React, lint e ArchUnit foram executados e estão verdes.
5. Python e frontend têm cobertura genuína (não apenas dependência instalada).
6. A chamada real AWS e Consult Center na mesma jornada foi evidenciada, com hipóteses Bedrock separadas dos cálculos Java.
7. Cenários de idempotência, concorrência, retry, falha externa e análise parcial foram testados.
8. Os relatórios registram bloqueios e não confundem mock com real.

Para cada item emitir PASS/FAIL/BLOCKED/NOT_RUN, evidência verificável (arquivo, comando, output sanitizado) e correção mínima. Não afirmar prontidão sem demonstrar P0.
