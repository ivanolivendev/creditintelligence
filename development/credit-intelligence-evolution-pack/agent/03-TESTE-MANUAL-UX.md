# Teste manual de UX - upload e analise de seis PDFs

**Data:** 20/11/2026  
**Ambiente:** aplicacao local em Docker Compose, navegador integrado  
**Proposta sintetica:** `a5b43f33-9549-4cae-bd14-b552b6028e69`  
**Analise:** `d3faf14b-0bfb-4f9e-aa80-1b524247b084`

## Objetivo

Reproduzir o fluxo como uma pessoa analista: entrar, criar uma proposta, selecionar e enviar tres contracheques e tres extratos, executar a analise e avaliar a resposta apresentada pela interface.

## Execucao observada

1. Login com o usuario local de demonstracao.
2. Criacao da proposta `Teste Manual Seis PDFs`, renda declarada de R$ 8.000,00.
3. Upload individual dos seis PDFs sinteticos de agosto, setembro e outubro de 2026.
4. Para cada arquivo, a interface exigiu quatro operacoes: tipo, competencia, arquivo e envio.
5. Ao selecionar o arquivo, a competencia previamente preenchida nao permaneceu confirmada no fluxo automatizado do navegador. Setembro e outubro foram registrados silenciosamente como agosto.
6. A lista exibiu seis `VERSOES`: tres versoes de contracheque de agosto e tres versoes de extrato de agosto, em vez de seis documentos distribuidos em tres competencias.
7. A interface permitiu executar a analise sem uma verificacao previa de cobertura e consistencia.
8. O backend recusou corretamente a divergencia entre competencia informada e conteudo extraido.

## Resposta apresentada

- Status: `FAILED_PERMANENT`.
- Codigo: `INVALID_EXTRACTION_RESPONSE`.
- Fatos extraidos: nenhum.
- IA: `NOT_REQUESTED`.
- Mensagem exibida: `Uma falha recuperavel pode ser reprocessada`, apesar do status permanente.
- A tela nao identificou o arquivo, campo ou competencia divergente e nao apresentou uma acao objetiva de correcao.

## Diagnostico priorizado

| Prioridade | Problema | Impacto |
|---|---|---|
| P0 | Competencia nao fica confiavelmente confirmada antes do upload | Documentos corretos entram no slot errado, viram versoes e inviabilizam a analise. |
| P0 | Ausencia de preflight antes da analise | A pessoa consegue iniciar uma operacao que o sistema ja poderia identificar como inconsistente. |
| P1 | Mensagem contraditoria: permanente versus recuperavel | A pessoa nao sabe se deve tentar novamente, trocar arquivos ou procurar suporte. |
| P1 | Codigo tecnico exposto sem traducao | `INVALID_EXTRACTION_RESPONSE` nao informa qual documento corrigir. |
| P1 | Contador de `VERSOES` mascara cobertura | Seis uploads parecem completos, embora existam somente dois slots logicos em uma competencia. |
| P2 | Upload repetitivo | Seis arquivos exigem 24 interacoes basicas, sem lote, classificacao automatica ou grade de revisao. |
| P2 | Tela longa e sem foco progressivo | Criacao, retomada, upload, bureau, simulacao, dossie e decisao competem pela atencao na mesma pagina. |

## Simplificacao recomendada

1. Substituir o formulario repetitivo por uma area unica de upload multiplo.
2. Extrair tipo e competencia do proprio PDF e apresentar uma grade de confirmacao antes de persistir.
3. Mostrar checklist visual: `Contracheques 3/3`, `Extratos 3/3`, competencias esperadas e divergencias.
4. Validar competencia informada contra competencia extraida no upload; bloquear e explicar imediatamente quando divergirem.
5. Antes de analisar, exibir um preflight com `Pronto para analisar` ou uma lista curta do que falta.
6. Traduzir falhas para linguagem operacional: arquivo afetado, causa e botao de correcao.
7. Mostrar codigos, hashes, politica e snapshot somente em `Detalhes tecnicos`.
8. Transformar a pagina em um fluxo guiado: `Proposta -> Documentos -> Revisao -> Resultado -> Decisao`.

## Conclusao

O motor protegeu a integridade ao rejeitar a extracao inconsistente. O problema principal esta na jornada: ela permite produzir o estado invalido, nao o sinaliza antes da analise e responde com terminologia contraditoria. A percepcao de fluxo confuso e complexo foi confirmada pelo teste manual.

## Correcao implementada e revalidada

**Proposta limpa:** `65920432-eb63-427c-8cc6-5508a1d712d5`
**Analise concluida:** `e12c287f-1716-4bd7-b7c3-4c092aa92bd3`

- Os seis PDFs foram selecionados em uma unica acao.
- A grade detectou corretamente contracheques e extratos de `2026-08`, `2026-09` e `2026-10`.
- Todos os itens ficaram visiveis para revisao antes do envio.
- Duplicatas, conflitos entre nome/tipo/competencia, arquivos nao PDF e tamanho acima de 10 MiB agora bloqueiam o lote com mensagem por arquivo.
- O preflight mostrou `3/3` contracheques, `3/3` extratos e `3/3` meses conciliaveis antes de liberar a analise completa.
- A analise terminou `COMPLETED`, com renda liquida media de R$ 7.733,33, tres conciliacoes `MATCH`, capacidade teorica de R$ 2.706,67 e IA claramente identificada como `MOCK`.
- Criacao/retomada de proposta, opcoes avancadas, rastreabilidade, fatos e verificacoes tecnicas foram recolhidos progressivamente para reduzir o comprimento e a carga visual da pagina.
- Erros permanentes e temporarios agora possuem mensagens operacionais diferentes; codigos internos ficam em detalhes tecnicos.

## Regressao apos a correcao

| Gate | Resultado |
|---|---|
| Frontend unitario | 5/5 testes da fila, inferencia, duplicidade, cobertura, versao atual e falha permanente. |
| Frontend estatico | ESLint, TypeScript/Vite e Prettier aprovados. |
| Java | 23/23 testes aprovados. |
| Python | 10/10 testes e Ruff aprovados. |
| OpenSpec | 10/10 specs aprovadas em modo estrito. |
| Smokes | Proposta, documentos, analise, financeiro, bureau, idempotencia, snapshot e decisao aprovados. |
| E2E UI | Selecao unica de seis PDFs, revisao da grade, upload, preflight completo e dossie concluido. |

**Classificacao final:** PASS.
