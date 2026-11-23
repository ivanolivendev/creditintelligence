# Inicialização com um comando

## Pré-requisitos reais
- Python 3.9+ no PATH (biblioteca padrão apenas; **não há pip install**).
- Projeto existente acessível em disco.
- **Codex CLI** (`codex`) ou **Claude Code CLI** (`claude`), instalada e autenticada; o pacote não inclui os agentes, credenciais, modelos ou licenças.
- A CLI deve ter acesso às ferramentas necessárias e, para pesquisa web, acesso de rede autorizado.

## Executar no diretório do projeto
Windows (PowerShell; ajuste o caminho para a pasta do kit):

```powershell
py -3 .\avanade-frontend-rebuild-kit\run.py --project .
```

macOS/Linux:

```sh
python3 ./avanade-frontend-rebuild-kit/run.py --project .
```

Se o kit estiver fora do projeto, aponte o caminho correto para `run.py` e mantenha `--project .` no diretório do projeto. Ou `--project /caminho/do/projeto`.

## O que acontece
1. Detecta `codex` (prioritário) ou `claude` no PATH, **não instala nada** e não inventa autenticação.
2. Envia ao agente o conteúdo integral dos documentos relevantes no stdin; não exige copiar prompts.
3. Executa planejamento em modo somente leitura (`codex exec --sandbox read-only -` ou `claude --permission-mode plan -p ...`). O relatório textual é salvo em `kit/runs/`, fora do repositório do projeto quando o kit é mantido fora dele.
4. Exige a palavra **APROVAR** digitada pela pessoa na mesma sessão. Sem ela, não dispara implementação.
5. Com aprovação, executa as Fases 5–7 (`codex exec --sandbox workspace-write -` ou `claude -p ...` com permissões próprias da CLI).
6. Salva os registros de planejamento, aprovação e resultado em `runs/`.

**Atenção:** se o kit estiver dentro do projeto, os relatórios em `runs/` serão criados nessa pasta do projeto; mantenha a pasta fora do repo ou exclua `runs/` do versionamento. O modo de planejamento impede edições pelo agente dentro do sandbox/permissões do provedor, não oferece isolamento criptográfico contra ferramentas externas, plugins ou comandos manualmente executados. A aprovação autoriza somente o escopo definido; os mecanismos de permissão da CLI ainda se aplicam e podem pedir confirmações adicionais.

## Opções
- `--agent codex` ou `--agent claude`: escolha explícita.
- `--plan-only`: executa Fases 1–4 sem exibir gate.
- `--dry-run`: inspeciona a configuração sem chamar a CLI.
- `--project CAMINHO`: aponta ao projeto real.
- `--yes` é recusado deliberadamente: não é possível contornar o gate pelo script.

## Limitações claras
- O executor chama um agente externo e não reconstrói o frontend sozinho sem ele.
- Pesquisa, screenshots, skill `ui-forge`, build e testes dependem dos recursos e credenciais reais do ambiente.
- Se a CLI falhar, o executor encerra sem declarar sucesso e bloqueia implementação caso o erro ocorra no planejamento.
- O output da CLI não é garantia de que ela executou ferramentas; exigir evidências é responsabilidade das instruções e da revisão humana.
- Não há adaptador automático Gemini, pois uma execução não assistida sem garantia de leitura apenas poderia alterar o projeto antes da aprovação.

## Documentação de CLI consultada
- Codex: https://learn.chatgpt.com/docs/non-interactive-mode
- Claude: https://docs.anthropic.com/en/docs/claude-code/cli-usage
