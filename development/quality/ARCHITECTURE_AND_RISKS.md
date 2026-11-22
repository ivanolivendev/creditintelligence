# Arquitetura, segurança e riscos

## Arquitetura implementada

O sistema é um monólito modular Java 21 com módulos de proposta, documento, análise, política, decisão e bureau. Cada módulo usa separação API, aplicação, domínio e infraestrutura. PostgreSQL/Flyway guardam estado transacional. O armazenamento documental possui adapter local e opção S3. Python/FastAPI trata extração e explicação. React/TypeScript oferece a mesa do analista.

O Java mantém a autoridade sobre estados, regras financeiras, snapshots, leases, persistência e decisão. Python recebe contratos delimitados e retorna dados que são validados. O modelo generativo é interpretação contextual, não comando de negócio.

## Evidência arquitetural

`backend-java/src/test/java/com/creditintelligence/ArchitectureTest.java` verifica que o domínio não depende de framework, controllers não dependem diretamente de infraestrutura, domínio não importa API ou infraestrutura e aplicações migradas usam portas. A suíte ArchUnit passou junto dos 276 casos Surefire atuais.

O resultado demonstra fronteiras relevantes, mas não elimina todo acoplamento. `AnalysisService` ainda concentra preparação, fingerprint, lease, chamada externa e conclusão. A cobertura de aplicação e infraestrutura é melhor que a baseline histórica, porém a simplicidade estrutural não foi medida por ferramenta de complexidade cognitiva.

## Segurança

Há autenticação e autorização verificadas para o papel `ROLE_ANALYST`, correlation ID, validação de contratos e higiene de credenciais. O workflow configura Dependency-Check Java, `pip-audit`, `npm audit` e checagem de credenciais.

Limitações comprovadas incluem Basic Auth, credencial demo única, CSRF desabilitado e defaults previsíveis no Compose. A execução real do SCA no provedor CI depende de `NVD_API_KEY` e não foi observada neste working tree. SAST dedicado não está configurado. Nenhuma conclusão regulatória ou de segurança bancária é sustentada.

## Mutation testing

O PIT atual contém 677 mutações. 668 foram mortas, 6 sobreviveram e 3 ficaram sem cobertura. O `pom.xml` define `mutationThreshold=80` e `coverageThreshold=0`, com escopo `com.creditintelligence.*` e exclusão das classes de integração do conjunto mutado.

Os sobreviventes concentram-se em `AnalysisService`, `AIExplanationService`, `ConsultCenterSandboxAdapter`, `PythonDocumentClient` e `PythonExplanationClient`. Eles representam limites temporais, duração de backoff, timeouts de estabelecimento de conexão e uma condição equivalente no ramo aceito. O resultado é uma medida de força dos testes no escopo mutado, não uma prova de correção total.

## Riscos residuais e critérios de produção

| Prioridade | Risco | Evidência atual | Requisito adicional |
|---|---|---|---|
| P1 | SCA real no CI não comprovado | Workflow exige segredo, sem execução observada | Executar no provedor com segredo válido e preservar log do gate |
| P1 | Integrações externas condicionais | Bedrock e bureau não são garantidos pelo perfil local | Ambiente sandbox controlado, contratos e observabilidade remota |
| P2 | Segurança de demonstração | Basic Auth, CSRF desabilitado, usuário único | OIDC ou mecanismo corporativo, gestão de sessão, segredo e auditoria |
| P2 | Cobertura de timeout e fronteiras | Seis mutantes sobreviventes e três sem cobertura | Testes de conexão, relógio controlado e falhas de infraestrutura |
| P2 | Acoplamento residual | `AnalysisService` concentra coordenação | Decomposição orientada a casos de uso com contratos observáveis |
| P3 | Escala e operação | Sem carga, SLO, outbox, DLQ ou operação 24x7 | Ensaios de carga, recuperação, retenção e operação antes de produção |

## Decisão de encerramento

Para a POC sandbox, o ciclo atual pode ser encerrado como **APTA COM RESSALVAS**. Para MVP compartilhado e produção bancária, os riscos P1 continuam bloqueadores de prontidão. Não há autorização nem evidência para refatorar código ou alterar thresholds nesta missão.
