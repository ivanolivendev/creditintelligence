# Integrar explicação opcional via Bedrock no Docker local

## Why

O serviço Python já produz explicações mock validadas pelo Java. O projeto deseja habilitar Bedrock localmente sem exigir AWS no caminho padrão nem expor credenciais permanentes. Qwen3 Next 80B A3B foi selecionado por custo/latência esperada; acesso real depende das permissões/termos da conta e de validação via Docker.

## What Changes

- Adicionar um provider opcional Bedrock no serviço Python, mantendo `mock` como padrão.
- Restringir a explicação aos achados, políticas e referências já enviados pelo Java; a resposta não pode decidir crédito nem alterar fatos.
- Documentar e automatizar ativação local com credencial STS temporária e policy de invocação restrita.
- Manter os testes e a jornada local padrão sem credenciais ou chamadas AWS.

## Out of Scope

- Usar somente fixtures sintéticas.
- Não provisionar recursos AWS nem aceitar termos em nome do usuário.
- A integração só será considerada verificada após confirmar o acesso, Compose acessível, teste de contrato e smoke real.
