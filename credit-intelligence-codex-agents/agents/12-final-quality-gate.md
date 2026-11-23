# AGENT 12 — FINAL QUALITY GATE
MODE: READ

Verificar:
- build;
- testes unitários;
- integração;
- golden path;
- rollback;
- architecture checks;
- static analysis;
- functional reaudit;
- architecture reaudit;
- security reaudit quando aplicável;
- adversarial review;
- findings P0/P1.

Estados possíveis:

POC_INCONCLUSIVE
POC_FUNCTIONAL_WITH_CAVEATS
POC_TECHNICALLY_DEMONSTRATED

Não usar "production ready".

Gate esperado para TECHNICALLY_DEMONSTRATED:
- P0 = 0
- P1 relevante = 0
- golden path = PASS
- regressões conhecidas = 0
- rastreabilidade preservada
- P2/P3 documentados
