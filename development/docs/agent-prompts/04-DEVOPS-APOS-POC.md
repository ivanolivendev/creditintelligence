# Prompt para fase posterior (somente depois da PoC local)

Use a skill credit-devops. Confira evidências de E2E local e gates P0/P1. Se insuficientes, não inicie cloud; indique os bloqueios. Se suficientes, prepare em uma mudança OpenSpec separada CI com GitHub Actions e Terraform versionado, valide estaticamente e documente deploy/rollback/custos. Não execute terraform apply, push ou crie recursos AWS sem autorização explícita.
