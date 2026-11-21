# Pacote de preparação Codex + OpenSpec — Credit Intelligence

Este ZIP é um **kit de configuração**, não é a aplicação implementada e não contém o Blueprint, pois você disse que ele já está no repositório. Foi desenhado para extrair na **raiz** desse repositório. Não há execução remota, nem AWS, nem instalação automática de ferramentas neste arquivo.

## Conteúdo
- `AGENTS.md`: instruções persistentes do Codex (arquitetura, qualidade, commits, segurança, Docker e AWS).
- `.agents/skills/credit-implementation/SKILL.md`: execução em pequenas fatias testáveis.
- `.agents/skills/credit-verification/SKILL.md`: validação independente e matriz de testes.
- `.agents/skills/credit-devops/SKILL.md`: fase Docker/CI/Terraform/AWS controlada.
- `scripts/setup-openspec.ps1`: verifica ferramentas e inicializa OpenSpec se ainda não existe.
- `docs/agent-prompts/`: quatro prompts prontos para usar no Codex.

**Atenção:** as skills `openspec-*` não são incluídas no ZIP: o próprio OpenSpec as gera com a versão instalada, evitando cópias desatualizadas.

## Passo a passo (Windows PowerShell)
1. Faça backup/commit das alterações atuais do repositório. Se já existir `AGENTS.md` ou alguma pasta igual, revise antes de substituir; não sobrescreva conteúdo útil.
2. Extraia o ZIP na raiz do repositório, mantendo pastas ocultas como `.agents`.
3. Tenha instalados Git, Node.js compatível (OpenSpec exige Node >= 22.12), Docker Desktop/WSL2, JDK 21, Python 3, e instale os CLIs:
   ```powershell
   npm install -g @openai/codex
   npm install -g @fission-ai/openspec@latest
   ```
4. Na raiz do repositório, execute:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\setup-openspec.ps1
   ```
5. Inicie `codex`, faça login com ChatGPT se solicitado. Cole o texto de `docs/agent-prompts/01-PRIMEIRA-EXECUCAO.md`.
6. Depois de revisar e aprovar o plano, use o segundo prompt e/ou `$openspec-apply-change`. Use `03-VERIFICAR.md` para a revisão com evidências; só depois o `04-DEVOPS-APOS-POC.md`.

## Não instalar por enquanto
- Superpowers, Spec Kit, frameworks de múltiplos agentes, Kubernetes, Kafka, Jenkins, Terraform, AWS CLI. Terraform/AWS CLI serão úteis apenas ao iniciar a fase cloud. GitHub Actions é um serviço configurado no repositório.

## Convenções e riscos
- Commits **locais** pequenos após verificações relevantes; sem push/PR/deploy por padrão.
- Todos os dados de demonstração devem ser sintéticos.
- Sem criar recursos AWS pagos sem aprovação explícita.
- Se o Codex não conseguir ler completamente o DOCX, converter fielmente para Markdown antes de seguir.
- Ferramentas de teste/qualidade são dependências do projeto a serem configuradas e executadas pelo Codex, não programas globais a instalar manualmente.
