# Estado atual observado — 20/11/2026

Este documento separa fatos observados em arquivos do repositório de resultados registrados anteriormente. Ele não substitui a auditoria inicial da próxima sessão.

## Produto e runtime

- Repositório: `C:\Users\siiva\OneDrive\Documentos\codex\creditintelligence`.
- Stack fechada em `development/AGENTS.md`: Java 21 + Spring Boot modular monolith; Maven; Spring Data JPA; PostgreSQL + Flyway; Python + FastAPI + Pydantic; React + TypeScript + Vite; Docker Compose.
- `compose.yaml` define PostgreSQL, backend Java, serviço Python de documentos, frontend Nginx e perfis de teste. PostgreSQL e Python não publicam portas no host; frontend e backend usam portas locais configuráveis, por padrão 18081 e 18080.
- O README descreve o runtime local, autenticação de demonstração, upload de PDF digital e comandos de smoke/build.
- A aplicação tem modo `mock` como padrão. A configuração `bedrock` chama Amazon Bedrock a partir do serviço Python quando as credenciais temporárias estão disponíveis. O perfil documentado usa Qwen `qwen.qwen3-next-80b-a3b`, região `us-east-1`; verificar configuração e acesso atuais antes de chamar.
- O arquivo `scripts/start-bedrock-local.ps1` documenta a renovação/injeção de credenciais STS temporárias para o Compose. Não registrar nem copiar valores de credenciais.

## Capacidades existentes registradas no projeto

O `README.md` e `development/docs/PLANO-IMPLEMENTACAO.md` descrevem:

- Propostas e upload/versionamento de documentos PDF sintéticos.
- Análise síncrona, snapshot de documentos/política, persistência, idempotência e recuperação por nova requisição.
- Extração Python com referências de evidência; Java valida a extração e é autoridade de regras, estado e decisões.
- Regras determinísticas de consistência de renda e dossiê.
- Explicação mock por padrão e integração opcional Bedrock, validada e persistida separadamente dos findings.
- Decisão humana autenticada, vinculada à análise e ao snapshot.
- UI para proposta, upload, evidências, findings, explicação e decisão; houve ajuste recente para mostrar mensagens em português, evidências e próximos passos de forma mais compreensível.
- PDFs exclusivamente sintéticos, incluindo `holerite-acme-demo.pdf`.

## Evidências previamente registradas (não assumir que continuam válidas)

O plano de implementação registra, em 20/11/2026, G0–G5 locais como PASS e uma jornada Bedrock Qwen como PASS, com comandos e resultados descritos no próprio arquivo. Também registra que o provisionamento de infraestrutura AWS foi bloqueado por uma SCP (`p-7gcyevpl`). Isso não impede a execução local, nem prova que o Docker atual está ativo nesta sessão.

Uma jornada com o PDF sintético Acme foi relatada anteriormente: renda declarada de R$ 8.000 e extraída de R$ 7.500, warning de consistência segundo política demonstrativa, evidência da página e explicação Bedrock disponível. Reproduzir e confirmar no ambiente atual antes de usar como evidência da nova evolução.

## Estado Git relevante

Na inspeção da sessão que criou este contexto, `git status --short` mostrou os arquivos do projeto como não rastreados (`??`), incluindo código e documentação. Não inferir que esses arquivos são mudanças descartáveis ou que a branch tem uma baseline limpa. Antes de editar, registrar status e diff, preservar todo o conteúdo e não executar reset, limpeza ou staging amplo.

## O que precisa ser revalidado na nova sessão

- Estado real dos containers, health checks, portas e logs.
- Comandos e resultados atuais dos testes, smoke scripts e builds.
- Contratos HTTP, DTOs, migrations e módulos realmente implementados.
- Estado da UI e o resultado do fluxo ponta a ponta com dados sintéticos.
- Acesso Bedrock, modelo/região/provider exibido e custo/chamadas necessários.
- Existência de provedor, documentação, credenciais e fixture oficial para sandbox de bureau. Não escolher fornecedor por suposição.
- Status do Git, arquivos modificados e OpenSpec ativo/arquivado.
