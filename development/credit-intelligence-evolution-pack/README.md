# Credit Intelligence — Evolution Pack 4.0 (PoC)

**Objetivo:** evoluir o Credit Intelligence existente de uma validação documental simples para uma **pré-análise financeira documental demonstrável**, com três contracheques, três extratos bancários, uma consulta externa de bureau em sandbox (condicional), motor Java determinístico, simulação e explicação pelo Bedrock já integrado.

**Não é:** plataforma de concessão automática, score real de inadimplência, proposta bancária vinculante, substituição de um motor bancário de produção ou recomendação para consultar CPFs arbitrários.

## Leia nesta ordem

1. `agent/01-COMANDO-MESTRE.md` — instrução pronta para colar no agente executor.
2. `docs/01-ESCOPO-E-MAPA-DE-REUSO.md` — o que manter, adaptar, adicionar e não construir.
3. `docs/02-ARQUITETURA-E-FLUXOS.md` — componentes, hexágono, ordem correta do pipeline e falhas.
4. `docs/03-DOMINIO-REGRAS-E-CALCULOS.md` — contrato conceitual do novo core e matemática verificável.
5. `docs/04-DOCUMENTOS-E-BUREAU.md` — extração, reconciliação, evidências, adapter genérico e sandbox.
6. `contracts/CONTRATOS-EXEMPLO.md` — objetos, payloads e endpoints ilustrativos.
7. `docs/05-PLANO-EXECUCAO-E-TESTES.md` — execução em dois dias, prioridades, testes e gates.
8. `docs/06-SEGURANCA-LIMITES-E-DEMO.md` — requisitos de segurança, linguagem da demo e limitações.
9. `agent/02-RELATORIO-EXECUCAO.md` — formulário obrigatório para registrar estado real do código e resultados.
10. `examples/CASOS-DE-TESTE.md` — dados sintéticos e gabaritos.

## Fontes e níveis de certeza

- **Fonte de arquitetura:** `Credit_Intelligence_Master_Blueprint_V3.docx`, de 19/11/2026, fornecido pelo usuário. É uma **baseline de proposta**, não comprovação de código entregue. Referências explícitas: seções 4–5 (prioridades/stack), 7–14 (módulos, modelos e contratos), 15–20 (pipeline e IA), 21–24 (idempotência/falhas/segurança), 29–33 (execução, testes, gates).
- **Contexto declarado pelo usuário:** já existe um sistema rodando localmente, foi utilizada arquitetura hexagonal e a integração Bedrock foi implementada, com adequações durante a codificação.
- **Ainda não verificado:** árvore e implementação efetivas do repositório, contratos runtime, schemas Flyway, UI, qualidade da extração, credenciais/acesso do sandbox e capacidade de modelos reais.
- **Decisões novas propostas pelo pacote:** Credit Analysis Engine, consolidação de múltiplos documentos, reconciliação, adaptador de bureau e simulações. São **modificações desejadas**, não funcionalidades existentes.

## Regra de ouro

**Inspecionar → preservar → estender → testar → evidenciar**. Não apagar o fluxo anterior nem trocar stack sem confirmar o estado do código. Preferir P0 completo a integração externa vistosa que não funciona.

## Critério de sucesso mínimo

Com **dados completamente fictícios**, executar uma proposta end-to-end: 3 contracheques + 3 extratos suportados → campos/evidências → renda de referência + conciliação → compromissos sem dupla contagem → capacidade + simulação → dossiê com explicação do Bedrock real (se acesso configurado) ou mock claramente identificado → revisão humana. Consulta de bureau externa por CPF sintético somente se contrato e sandbox verificados; indisponibilidade não bloqueia cálculo documental.
