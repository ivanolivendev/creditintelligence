# DOMAIN GUARDRAILS — CRÉDITO INTELIGENTE

## Princípios funcionais

O sistema deve preservar distinção entre:

- zero;
- ausente;
- desconhecido;
- inválido;
- estimado;
- extraído;
- inferido.

Nunca converter ausência de informação em conclusão negativa ou valor zero sem regra explícita.

## Reconciliação

Verificar cuidadosamente:
- correspondência de períodos;
- salário bruto/líquido;
- depósitos recorrentes;
- créditos extraordinários;
- duplicidade;
- tolerâncias;
- dados conflitantes;
- períodos incompletos.

Evitar:
- double counting;
- associação a período incorreto;
- confundir salário com outra renda;
- tratar valores parecidos como equivalentes;
- concluir inconsistência sem evidência suficiente.

## Assessment e findings

Toda conclusão relevante deve, quando aplicável, permitir rastrear:

SOURCE
→ EXTRACTED FACT
→ NORMALIZED FACT
→ RULE
→ FINDING
→ ASSESSMENT
→ EXPLANATION

## IA

A IA:
- não é fonte de verdade;
- não pode inventar dados;
- deve distinguir evidência de inferência;
- deve trabalhar sobre fatos preservados;
- deve ter saída tratada como falível;
- não deve substituir regra determinística;
- não deve tomar decisão financeira automática se a POC foi desenhada para apoio à decisão humana.

Investigar:
- hallucination;
- prompt injection;
- excesso de confiança;
- inconsistência;
- interpretação indevida;
- conclusão sem fundamentação.

## Dados

Nunca usar dados financeiros pessoais reais em testes exploratórios.
Prefira fixtures sintéticas/anonimizadas.
