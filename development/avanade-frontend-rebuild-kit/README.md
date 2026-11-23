# Avanade Frontend Rebuild Kit — execução com um comando

Kit de reconstrução do frontend de um **projeto já existente**, com direção visual original inspirada em referências públicas da Avanade. Não é um produto oficial, afiliado ou autorizado pela Avanade. Não inclui logotipos/ativos proprietários.

## Comece aqui (sem copiar prompts)

Extraia `avanade-frontend-rebuild-kit.zip` no repositório da aplicação e, **dentro da raiz do projeto**, execute:

**Windows (PowerShell):**
```powershell
py -3 .\avanade-frontend-rebuild-kit\run.py --project .
```

**macOS/Linux:**
```sh
python3 ./avanade-frontend-rebuild-kit/run.py --project .
```

Requer Python 3.9+ e CLI **Codex** ou **Claude Code** instalada e autenticada. O kit **não** baixa ferramentas nem trabalha sem acesso ao código; veja `docs/ONE-COMMAND-SETUP.md` para alternativas e opções. Se já estiver com terminal na raiz do projeto, basta um comando. Não é necessário enviar prompts manualmente.

## Fluxo automático e controle
O `run.py` encontra uma CLI suportada → injeta os documentos → faz auditoria, pesquisa (se houver ferramenta) e plano **somente leitura** → salva relatório → pede que você digite **APROVAR** → implementa apenas após aprovação → registra validação e entrega. Nunca use `--yes`: o gate não pode ser ignorado pelo script. O agente pode solicitar permissões adicionais quando suas ferramentas exigirem.

## Conteúdo
- `run.py`, `start.bat`, `start.sh`: inicializador local (biblioteca padrão Python).
- `AGENT-INSTRUCTIONS.md`: regras permanentes.
- `prompts/`: orquestração por fase, descoberta de skills, auditoria, design, implementação, validação.
- `specs/`: identidade, escopo, aceite, acessibilidade e design system.
- `workflows/`: fluxos técnicos, qualidade e regressão.
- `templates/`: evidências e modelos de relatório.
- `docs/`: fontes, notas legais, execução e comandos.
- `checklists/`: gates e evidências.
- `tests/`: testes locais do orquestrador, sem dependências externas.

## Restrições fundamentais
Preserve backend, banco, APIs, contratos, auth, autorização, integrações e regras de negócio. Não substitua serviços reais por mocks. Não invente comando/skill, teste executado, especificação oficial Avanade ou direito de usar marca. Diferencie **observado**, **estimado** e **proposto**. Conclua auditoria e planejamento antes de qualquer implementação ampla.

## Ordem de leitura incorporada pelo executor
`AGENT-INSTRUCTIONS.md` → `MASTER-EXECUTION-PROMPT.md` → `SKILL-DISCOVERY-AND-INVOCATION.md` → specs → workflows → templates/checklist. Os textos são fornecidos pelo launcher; não é necessário abri-los individualmente.

**Observação:** o ZIP entrega um orquestrador, não o código do seu projeto ou um agente integrado. Se a CLI instalada não dispuser de pesquisa web/screenshot ou de `ui-forge`, o agente deve documentar o limite e seguir fallback.
