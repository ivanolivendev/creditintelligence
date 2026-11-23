#!/usr/bin/env python3
# One-command planning and implementation runner; requires Python and authenticated CLI.
import argparse
import datetime as dt
import os
import shutil
import subprocess
import sys
from pathlib import Path

KIT = Path(__file__).resolve().parent
DOCS = ["AGENT-INSTRUCTIONS.md", "prompts/MASTER-EXECUTION-PROMPT.md",
        "prompts/SKILL-DISCOVERY-AND-INVOCATION.md", "specs/AVANADE-VISUAL-REFERENCE.md",
        "specs/PRODUCT-AND-REFERENCE-SCOPE.md", "specs/DESIGN-SYSTEM-SPECIFICATION.md",
        "specs/ACCESSIBILITY-REQUIREMENTS.md", "specs/FRONTEND-REBUILD-ACCEPTANCE.md",
        "workflows/PHASED-EXECUTION.md", "workflows/FRONTEND-AUDIT.md",
        "workflows/VISUAL-RESEARCH.md", "workflows/IMPLEMENTATION-WORKFLOW.md",
        "workflows/REGRESSION-TESTING.md", "templates/AUDIT-REPORT-TEMPLATE.md",
        "templates/ROUTE-INVENTORY-TEMPLATE.md", "templates/DESIGN-TOKENS-TEMPLATE.md",
        "templates/IMPLEMENTATION-PLAN-TEMPLATE.md", "templates/FINAL-REPORT-TEMPLATE.md",
        "checklists/GATE-AND-EVIDENCE-CHECKLIST.md", "docs/REFERENCE-SOURCES.md",
        "docs/BRAND-USAGE-NOTES.md"]


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Avanade-inspired frontend rebuild runner")
    p.add_argument("--project", default=".", help="Path to the EXISTING application repository (default: current directory)")
    p.add_argument("--agent", choices=("auto", "codex", "claude"), default="auto")
    p.add_argument("--plan-only", action="store_true", help="Stop after the planning phase")
    p.add_argument("--yes", action="store_true", help="NOT SUPPORTED: explicit interactive approval is mandatory")
    p.add_argument("--dry-run", action="store_true", help="Print configuration and command preview; do not call an agent")
    return p.parse_args(argv)


def select_agent(wanted):
    for candidate in ([wanted] if wanted != "auto" else ["codex", "claude"]):
        path = shutil.which(candidate)
        if path:
            return candidate, path
    raise RuntimeError("Nenhuma CLI compatível encontrada no PATH. Instale e autentique Codex CLI ou Claude Code CLI; consulte docs/ONE-COMMAND-SETUP.md.")


def make_prompt(phase, project, report=None):
    parts = ["PROJETO EXISTENTE (diretório atual da CLI): " + str(project),
             "PACOTE DE INSTRUÇÕES: incluído integralmente abaixo; use somente ferramentas realmente disponíveis.",
             "Não confunda inspiração estética Avanade com ativos oficiais. Não altere backend ou contratos."]
    if phase == "plan":
        parts.append('''EXECUTE APENAS AS FASES 1–4. Inspecione o repositório em modo somente leitura.
Identifique stack, dependências, rotas, APIs, autenticação, design atual, testes e riscos.
Pesquise fontes públicas apenas se as ferramentas disponíveis permitirem; registre quando não permitir.
Verifique ui-forge, leia sua documentação se instalada e invoque conforme as instruções reais.
Escreva seu resultado NO TEXTO FINAL (o launcher o salvará fora do projeto).
Inclua um PLANO DE IMPLEMENTAÇÃO com rotas, arquivos exatos, abordagem incremental,
critérios de aceite, testes, risco e rollback, diferenciando fatos, estimativas e propostas.
NÃO crie, modifique, mova ou apague arquivos de projeto. NÃO inicie a implementação.
Não diga que há autorização; o launcher exigirá confirmação humana específica.''')
    else:
        parts.append('''FASES 5–7 LIBERADAS APÓS APROVAÇÃO HUMANA EXPLÍCITA DO PLANO ANEXO.
Implemente SOMENTE o escopo aprovado, de forma incremental, sem tocar backend, DB,
contratos, regras de negócio ou integrações, e sem trocar integração real por mock.
Valide build, lint, testes existentes, regressão, acessibilidade, responsividade,
console e rede sempre que o ambiente permitir. Não invente resultados de testes.
No resultado final descreva arquivos alterados, comandos executados, evidência,
bloqueios, pendências e rollback. Pare se exigir alterações fora do escopo aprovado.
PLANO APROVADO (cópia integral do resultado da fase de planejamento):
<PLANO_APROVADO>\n''' + report + "\n</PLANO_APROVADO>")
    for rel in DOCS:
        content = (KIT / rel).read_text(encoding="utf-8")
        parts.append("\n===== ARQUIVO: " + rel + " =====\n" + content + "\n===== FIM =====")
    return "\n\n".join(parts)


def agent_command(agent, binary, phase):
    if agent == "codex":
        return [binary, "exec", "--sandbox", "read-only" if phase == "plan" else "workspace-write", "-"]
    # Claude's 'plan' permission mode prevents edits during phase 1–4.
    if agent == "claude":
        if phase == "plan":
            return [binary, "--permission-mode", "plan", "-p", "Leia as instruções fornecidas por stdin e execute somente o planejamento."]
        return [binary, "-p", "Execute as instruções fornecidas por stdin seguindo somente o plano aprovado."]
    raise ValueError(agent)


def execute(cmd, cwd, prompt, destination):
    print("\nExecutando:", " ".join([Path(cmd[0]).name] + cmd[1:]), flush=True)
    print("Relatório:", destination, flush=True)
    # Stdout is captured while stderr remains visible for progress and CLI errors.
    # No shell invocation; user-supplied paths cannot inject commands.
    with destination.open("w", encoding="utf-8") as f:
        proc = subprocess.Popen(cmd, cwd=str(cwd), stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE, stderr=None, text=True,
                                encoding="utf-8", errors="replace")
        try:
            proc.stdin.write(prompt)
            proc.stdin.close()
            for line in proc.stdout:
                f.write(line)
                f.flush()
                print(line, end="", flush=True)
            return proc.wait()
        except KeyboardInterrupt:
            proc.terminate()
            proc.wait()
            raise


def main(argv=None):
    a = parse_args(argv)
    if a.yes:
        print("ERRO: --yes é proibido; aprovação humana interativa é obrigatória.", file=sys.stderr)
        return 2
    project = Path(a.project).expanduser().resolve()
    if not project.is_dir() or project == KIT:
        print("ERRO: --project deve apontar para o repositório real da aplicação, não para o kit.", file=sys.stderr)
        return 2
    try:
        agent, binary = select_agent(a.agent)
    except RuntimeError as exc:
        print("ERRO:", exc, file=sys.stderr)
        return 2
    print("Projeto:", project)
    print("Agente:", agent, "(" + binary + ")")
    print("Etapas: leitura/auditoria/pesquisa/plano → aprovação humana → implementação/testes")
    if a.dry_run:
        print("Planejamento:", agent_command(agent, binary, "plan"))
        print("Implementação:", agent_command(agent, binary, "implement"))
        print("Nenhuma CLI executada; nenhum arquivo do projeto alterado.")
        return 0
    runroot = KIT / "runs"
    runroot.mkdir(exist_ok=True)
    run_dir = runroot / dt.datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    run_dir.mkdir()
    (run_dir / "project-path.txt").write_text(str(project) + "\n", encoding="utf-8")
    print("Registros (fora do projeto):", run_dir)
    plan_path = run_dir / "01-AUDITORIA-E-PLANO.md"
    code = execute(agent_command(agent, binary, "plan"), project, make_prompt("plan", project), plan_path)
    if code != 0:
        print("Planejamento falhou (exit code " + str(code) + "). Implementação BLOQUEADA.", file=sys.stderr)
        return code
    plan = plan_path.read_text(encoding="utf-8").strip()
    if len(plan) < 150:
        print("Planejamento insuficiente/vazio. Implementação BLOQUEADA.", file=sys.stderr)
        return 3
    print("\n=== PLANO SALVO ===\n", plan_path)
    if a.plan_only:
        print("Modo --plan-only: não houve autorização para implementação.")
        return 0
    print("\nREVISE O PLANO ACIMA ANTES DE AUTORIZAR MUDANÇAS NO PROJETO.")
    try:
        decision = input("Digite APROVAR para liberar a implementação (qualquer outra resposta cancela): ").strip()
    except (EOFError, KeyboardInterrupt):
        print("\nSem aprovação. Implementação cancelada.")
        return 0
    if decision != "APROVAR":
        print("Plano não aprovado. Projeto não liberado para edição.")
        return 0
    (run_dir / "02-APROVACAO.txt").write_text("APROVAR\n", encoding="utf-8")
    report_path = run_dir / "03-IMPLEMENTACAO-E-VALIDACAO.md"
    code = execute(agent_command(agent, binary, "implement"), project,
                   make_prompt("implement", project, plan), report_path)
    if code:
        print("Agente retornou erro. Confira o relatório; não presuma sucesso.", file=sys.stderr)
        return code
    print("\nExecução finalizada pela CLI; confira o relatório e valide as mudanças no repositório:", report_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
