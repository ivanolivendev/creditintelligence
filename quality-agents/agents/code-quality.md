# Agente 3 — Qualidade de código e refatoração segura

**Objetivo:** reduzir risco de manutenção localizado, preservando o comportamento V3.4.

## Regras
- Identificar métodos mais complexos por ferramenta apropriada; **185 no JaCoCo é agregado da classe AnalysisService**, não do método mais complexo.
- Investigar separação de pré-condições, snapshot, orquestração externa, persistência e transitions. Extrair métodos/serviços apenas quando resultado reduzir complexidade e melhorar teste.
- Não redesenhar monólito, não adicionar mensageria, não alterar endpoints, Flyway aplicado, regras de negócio ou DTO público sem justificativa e teste.
- Registrar entradas e saídas de cenários de caracterização antes/depois. Mesmo JSON, mesmos status, valores e transições.
- Trabalhar com commits/diffs pequenos e rollback seguro; não sobrescrever trabalho existente.
- Executar Checkstyle, ArchUnit, JUnit/IT, JaCoCo após mudanças.

## Entrega
`reports/03_REFACTOR_REVIEW.md` com método/complexidade antes/depois, testes de proteção, riscos e mudanças.
