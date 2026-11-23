# Credit Intelligence Visual Design System

## Escopo e posição

Este documento define o sistema visual original aplicado ao Credit Intelligence. A Avanade foi usada como referência de direção — composição luminosa, contraste escuro pontual e acentos quentes fluidos — sem reutilizar logotipo, slogan, ícone, fonte proprietária, captura ou ativo protegido. O produto não é afiliado, patrocinado ou endossado pela Avanade.

Data da investigação e implementação: 21 de novembro de 2026. A fonte de decisões do produto continua sendo `development/Credit_Intelligence_Master_Blueprint_V3.docx`; esta camada altera somente apresentação.

## Evidências de referência

| Evidência | Fonte | Classificação | Limitação |
| --- | --- | --- | --- |
| Navegação institucional por indústrias, serviços, insights, carreiras e sobre | [avanade.com](https://www.avanade.com/) e [Avanade Brasil](https://www.avanade.com/pt-br) | EXTRAÍDA | Conteúdo dinâmico; não é um manual de tokens |
| Direção contemporânea com fundos claros/escuros e chamadas de alto contraste | páginas institucionais oficiais | OBSERVADA | Inferência de composição, não especificação oficial |
| Ondas/ribbons e gradientes quentes em laranja, amarelo, vermelho e magenta | materiais públicos vinculados nas referências do kit | OBSERVADA | Peças editoriais não autorizam copiar arte ou trade dress |
| Proibição de copiar logos, ícones, trade dress e sugerir afiliação | [política de marcas Avanade](https://www.avanade.com/pt-br/utility/trademarks) | OFICIAL | Aplicação independente exige identidade própria |
| Família tipográfica usada pelo site atual | não localizada em documentação pública confiável nesta investigação | NÃO VERIFICADA | O projeto usa fallback licenciado de Google Fonts já existente |

## Princípios de interface

- **Optimistic:** fundos claros, estados positivos e acentos luminosos.
- **Fresh:** espaços generosos, cards com bordas suaves e hierarquia curta.
- **Bold:** gradiente quente restrito a superfícies de entrada, destaque e progresso.
- **Confident:** navy profundo para navegação e leitura executiva; dados continuam em superfícies neutras.
- O dashboard transacional não usa gradiente atrás de texto longo. As formas fluidas são CSS originais e decorativas.

## Tokens implementados

Os tokens vivem em `frontend/src/design-tokens.css`. Valores com origem `PROPOSTO` são decisões de produto inspiradas na pesquisa; não são alegados como HEX oficiais da Avanade.

| Token | Valor | Papel | Origem/confiança |
| --- | --- | --- | --- |
| `--ci-ink-950` | `#15132A` | texto e navegação profunda | PROPOSTO / média |
| `--ci-ink-700` | `#3D3853` | texto secundário | PROPOSTO / média |
| `--ci-muted` | `#716B80` | metadados | PROPOSTO / média |
| `--ci-paper` | `#FCFBF8` | fundo principal | PROPOSTO / média |
| `--ci-surface` | `#FFFFFF` | cards e formulários | PROPOSTO / alta |
| `--ci-line` | `#E8E3E6` | bordas e divisórias | PROPOSTO / alta |
| `--ci-orange` | `#F26A3D` | ação e destaque quente | INFERIDO da família observada / baixa |
| `--ci-coral` | `#E8455B` | alerta visual e gradiente | INFERIDO / baixa |
| `--ci-yellow` | `#FFC857` | destaque e ornamento | INFERIDO / baixa |
| `--ci-magenta` | `#C83B86` | acento secundário | INFERIDO / baixa |
| `--ci-teal` | `#0E8F86` | sucesso e confirmação | PROPOSTO / média |
| `--ci-focus` | `#6557D8` | foco de teclado | PROPOSTO / média |
| `--ci-warm-gradient` | `linear-gradient(120deg, #F26A3D 0%, #E8455B 48%, #C83B86 100%)` | superfícies de entrada e destaque | PROPOSTO / baixa |
| `--ci-shadow-card` | `0 18px 50px rgba(21,19,42,.08)` | elevação discreta | PROPOSTO / alta |

Contraste: texto normal usa `--ci-ink-950` ou `--ci-ink-700` sobre `--ci-paper`/`--ci-surface`; texto branco fica restrito a `--ci-ink-950` e ao gradiente escurecido. A validação automatizada e visual deve confirmar WCAG 2.2 AA após o build.

## Tipografia

O projeto já carregava `DM Sans` para texto e `Space Grotesk` para títulos. Mantemos essas famílias para evitar dependência nova e preservar acentos; a utilização pela Avanade não foi confirmada e, portanto, é uma **ALTERNATIVA** de implementação.

| Uso | Família | Peso | Tamanho base | Line-height |
| --- | --- | --- | --- | --- |
| Corpo | DM Sans | 400–600 | `1rem` | `1.55` |
| Títulos | Space Grotesk | 600–700 | `clamp(2.25rem, 5vw, 4.25rem)` | `1.02` |
| Labels e kicker | DM Sans | 700 | `.72rem` | `1.2` |
| Dados/KPI | Space Grotesk | 600–700 | `1.05–1.75rem` | `1.1` |

## Layout e componentes

- Container principal fluido com `max-width: 72rem` e padding responsivo.
- Sidebar de `17rem` no desktop; reduzida para navegação compacta em até `56rem`.
- Grid de trabalho em duas colunas; uma coluna abaixo de `56rem`.
- Breakpoints de QA: `20rem`, `23.4375rem`, `48rem`, `64rem`, `90rem`.
- Raios: `0.75rem` em controles, `1rem` em cards, `1.5rem` em hero/destaques.
- Botão primário: navy ou gradiente quente em superfícies de entrada; hover com elevação mínima; foco sempre visível.
- Botão secundário: superfície branca, borda neutra e texto navy.
- Cards: fundo branco, borda neutra, sombra curta; nenhum conteúdo funcional é substituído por imagem.
- Formulários: labels persistentes, campos com `min-height` de `2.9rem`, foco com halo violeta e erros textuais existentes preservados.
- Badges: estados success, warning, info e neutral com texto e ícone/label, nunca apenas cor.
- Estados vazios, erro, loading e disabled mantêm a semântica e os contratos existentes.

## Motion e acessibilidade

Transições ficam entre `160ms` e `220ms`, usadas para hover, foco e progresso. `prefers-reduced-motion: reduce` remove transformações e transições. A navegação continua baseada nos elementos existentes; foco de teclado tem outline de alto contraste. A validação final deve cobrir zoom 200%, reflow e os viewports definidos.

## Limitações

Não foram incorporados logotipos, ícones proprietários, imagens de campanhas, slogans ou fontes não licenciadas. A investigação pública não revelou um brand book/tokens oficiais completos; por isso, os valores cromáticos e métricos são tokens originais classificados como propostos ou inferidos. O backend, APIs, autenticação, contratos, dados sintéticos, regras determinísticas e estado de negócio não fazem parte desta mudança.
