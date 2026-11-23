# Workflow: testes de regressão

## Estratégia
Use a matriz de rotas e papéis auditada como fonte. Capture baseline antes de implementar; execute cenários comparáveis depois. Não confunda screenshot com teste funcional.

## Matriz mínima por jornada crítica
- **Acesso:** anônimo, usuário com permissão, usuário sem permissão, sessão expirada, logout/login, redirecionamentos.
- **Dados:** lista carregada, vazia, filtros, ordenação, paginação, detalhes, edições, cancelamento, persistência, refresh, falha de rede e timeouts conforme comportamento real.
- **Formulários:** required/invalid/valid, erros backend, feedback de sucesso, duplicidade/envio repetido se aplicável, teclado.
- **Integrações:** endpoint/método, schema, código de status, headers, upload/download, WebSocket/stream se houver; não capturar credenciais.
- **UI:** 320/375/768/1024/1440 px, zoom, contrastes, foco, reduced motion, modal, toast, overflow e tabelas.
- **Qualidade:** build/lint/testes existentes; logs de console e rede, screenshots nas mesmas condições, performance se baseline disponível.

## Evidência
`Teste ID | rota/papel | precondições | dados sintéticos ou ambiente seguro | passos | esperado | observado | status | comando | captura/log | issue`. Não execute cenários que criem/alterem dados reais sem consentimento e ambiente apropriado.

## Triagem
P0 segurança/perda de dados/auth; P1 fluxo crítico quebrado; P2 acessibilidade/UX relevante; P3 detalhe visual. P0/P1 bloqueiam liberação. `NÃO EXECUTADO` nunca equivale a PASSOU.
