# Especificação de design system — a ser materializada a partir do projeto

## Metadados obrigatórios de todo token
`nome`, `valor`, `unidade`, `papel semântico`, `origem {OBSERVADO|ESTIMADO|PROPOSTO}`, `fonte/evidência`, `confiança {alta|média|baixa|não verificada}`, `decisão/autor`, `data`, `status {rascunho|aprovado|depreciado}`. **Valores abaixo são exemplos de decisão PROPOSTA**, jamais oficiais Avanade.

## Arquitetura de tokens
1. **Primitivos:** paleta base, unidades de espaço, famílias e pesos de fontes licenciadas, durations e curvas.
2. **Semânticos:** `color.text.primary`, `.secondary`, `.inverse`; `color.surface.base`, `.raised`, `.subtle`; `color.action.primary`, `.hover`, `.disabled`; `color.border.default`, `.focus`; `color.feedback.error|warning|success|info`; `color.decorative.warmGradient`.
3. **Componentes:** `button.primary.bg`, `input.border.error`, `card.padding`, `modal.elevation`; alias a tokens semânticos, não valores soltos.

## Cores e gradientes
A direção colorida é contextual: tons quentes em cabeçalhos/ilustrações discretas/CTA selecionado; neutralidade em dashboards, tabelas e formulários. Nunca aplicar gradiente atrás de texto sem testar o pior ponto do fundo. Estados não dependem só da cor. Para cada combinação de foreground/background, guardar razão de contraste verificada. Paleta HEX a ser **proposta** depois de pesquisar o projeto, não fornecida como oficial.

## Tipografia
Detectar fontes instaladas e licenças; prever fallback de sistema. Definir escala de heading/body/label/data, line-height e peso, wrapping e tratamento de números. Checar idioma/acentos, legibilidade em 200% zoom e diferenciação de links.

## Espaçamento, grade, elevação
Definir escala simples (ex.: múltiplos de unidade-base **proposta**), larguras de conteúdo, layout fluido, densidades para tabelas, raio de componentes, sombras discretas. Justificar cada escolha pela densidade real do produto.

## Breakpoints
Derivar pontos onde o conteúdo quebra, não adotar número sem teste. Verificar pelo menos 320, 375, 768, 1024 e 1440 px como **alvos de QA propostos**; usar CSS responsivo e container queries se compatíveis. Navegação, tabelas e formulários precisam estratégias próprias.

## Componentes mínimos a avaliar
App shell; navegação lateral e superior; breadcrumbs; headings; botões e links; campo de texto/select/checkbox/radio; data e busca; feedback e validação; alertas/toasts; card; tabs; modal/dialog; dropdown; tabela/paginação/filtros; skeleton; empty/error/forbidden states; gráficos se existirem. Para cada: variantes, props, keyboard, ARIA apenas quando necessária, erro, desabilitado, foco, loading, testes e responsividade.

## Motion
Animações só para orientação, com duração curta proposta a validar; respeitar `prefers-reduced-motion`; nenhum conteúdo importante revelado apenas com movimento. Evitar animações constantes em área de trabalho.

## Governança
Auditar duplicidades antes de criar biblioteca; documentar alterações; visualizar componentes com as ferramentas reais do projeto; tokens aprovados têm revisão versionada. Modelo: `templates/DESIGN-TOKENS-TEMPLATE.md`.
