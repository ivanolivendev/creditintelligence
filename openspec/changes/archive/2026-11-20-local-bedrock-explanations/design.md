# Design: explicação Bedrock local

O Java continua autoridade sobre snapshot, extração, findings, referências aceitas e decisão humana. Python seleciona `mock` por padrão ou `bedrock` mediante `AI_PROVIDER`; no modo Bedrock usa Converse com o modelo regional Qwen3 Next 80B A3B (`qwen.qwen3-next-80b-a3b`) e valida a resposta contra o contrato. O Java valida novamente referências antes de persistir a explicação.

O serviço Python se conecta à rede `private` e à rede de saída `edge`, sem publicar porta no host. O script PowerShell solicita token STS temporário limitado por `infra/aws-demo/bedrock-invoke-policy.json`, injeta o token no Compose e apaga o arquivo temporário em bloco `finally`. Não se monta `~/.aws` nem se grava chave permanente no `.env`. Após expiração, os containers devem ser recriados com novas credenciais.

## Verificação

- A conta reportou modelo `ACTIVE`, acordo `AVAILABLE`, autorização `AUTHORIZED`, entitlement `AVAILABLE` e região `AVAILABLE`.
- Python `pytest` 7/7 e Ruff passaram; Java 12/12 passou no Compose.
- Smoke mock e smoke Bedrock sintéticos passaram; Qwen retornou provider `BEDROCK` e finding `WARNING`.
- Após a chamada real, o ambiente foi recriado em `mock`; credenciais temporárias e `.bedrock.env` foram removidos.
