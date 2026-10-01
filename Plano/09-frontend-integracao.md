# Fase 9 — Frontend Web Responsivo e Integração

## Objetivo

Entregar uma interface web responsiva para os fluxos do motorista e do
administrador, consumindo os endpoints reais das Fases 2 a 8. O frontend é
servido pelo Spring Boot a partir de `src/main/resources/static`, usa HTML
semântico, Vanilla JavaScript ES modules, Bootstrap 5.2.3 via CDN e Leaflet
1.9.4. A decisão de substituir Tailwind por Bootstrap foi aprovada; a migração
visual está em andamento e esta documentação distingue a direção aprovada do
que ainda precisa de validação funcional e visual.

## Referência visual e decisão de integração

A referência está em [`../Layout/`](../Layout/README.md), na raiz do
repositório. É o LUNO (WrrapTheme/ThemeMakker, 2022), um kit de interface
administrativa com mais de uma demonstração de dashboard, páginas de conta e
aplicação, telas de autenticação, documentação de componentes, fontes SCSS,
scripts e saída compilada. O `package.json` declara a faixa `^5.2.0` para
Bootstrap; como não há lockfile no diretório, a versão exata instalada não está
registrada. `src/`
contém fontes e parciais Gulp/SCSS, enquanto `dist/` contém HTML e CSS/JS
compilados. O projeto usa também jQuery e numerosos plugins opcionais.

Pontos de inspeção da referência: [`src/index.html`](../Layout/src/index.html)
e [`src/partials/`](../Layout/src/partials/) para shell e composição de tela;
[`src/auth-signin.html`](../Layout/src/auth-signin.html) para autenticação;
[`src/assets/scss/global/_variables.scss`](../Layout/src/assets/scss/global/_variables.scss),
[`src/assets/scss/theme/core.scss`](../Layout/src/assets/scss/theme/core.scss),
[`src/assets/scss/theme/_themes.scss`](../Layout/src/assets/scss/theme/_themes.scss)
e [`src/assets/scss/skeleton/_layout.scss`](../Layout/src/assets/scss/skeleton/_layout.scss)
para tokens, temas e breakpoints; [`src/docs/`](../Layout/src/docs/) para
exemplos de componentes Bootstrap; e [`package.json`](../Layout/package.json)
para versões e dependências declaradas.

Não se deve tratar cada uma das centenas de demonstrações como requisito, nem
copiar telas de CRM/e-commerce ou conteúdo ilustrativo. O objetivo é adaptar o
shell, o sistema visual e os componentes compatíveis com o FuelFinder. A
referência não fornece uma tela de mapa Leaflet nem lógica de domínio,
autenticação, integração com API ou autorização; essas partes permanecem
implementadas pelos módulos próprios já descritos neste documento.

### Identidade visual observada

- A variação selecionada nas páginas de entrada é `theme-blue`: azul
  (`#2794eb`) como primária e verde-água (`#00AC9A`) como secundária. O
  dashboard usa superfícies claras e discretas; a cascata SCSS para
  `data-luno="theme-blue"` resulta em fundo `#f4f7f6`, cartões brancos
  (`#ffffff`), borda `#e9e6e1` e texto principal `#464545`. Manter essa
  combinação, sem misturar as demais variações (blush, indigo, cyan, green,
  orange, red e dark) na mesma experiência.
- Os estados semânticos definidos no SCSS são success `#4DCA88`, danger
  `#FC5A69`, warning `#FFBA33` e info `#4FB8C9`. Cor não deve ser o único meio
  de indicar estado: incluir rótulo, ícone acessível ou texto.
- `core.scss` declara `data-luno="theme-blue"` duas vezes para o fundo; a
  segunda regra vence na cascata (`#f4f7f6`). A sidebar tem largura `280px` no
  layout, embora `$sidebar-width` declare `250px`; conferir o seletor efetivo
  antes de portar dimensões como tokens.
- A fonte padrão é Nunito; Open Sans, Quicksand e Raleway existem como
  alternativas globais, não como combinação simultânea. Manter Nunito em
  títulos e corpo para preservar a identidade. O SCSS define `$font-size: 14px`,
  mas o estilo geral aplica 16px ao corpo e 15px em algumas variantes; priorizar
  legibilidade móvel e não reduzir texto essencial para compensar densidade.
- A hierarquia combina breadcrumb discreto, título de página, texto auxiliar,
  ações alinhadas e conteúdo em cartões brancos. Há separação sutil entre áreas,
  sombras leves e cantos arredondados (por exemplo, `border-radius: .75rem`).
  Usar a escala de espaçamento do Bootstrap (múltiplos de 4px, especialmente
  `g-3`/`g-4`) e os respiros dos parciais `page-toolbar`/`page-body`, evitando
  densidade de dashboard administrativo em telas de motorista.
- O pacote inclui Bootstrap Icons, Font Awesome, Feather, Line Awesome e outros
  conjuntos. A implementação atual não carrega esses conjuntos: usar ícones
  somente após escolher e justificar uma única dependência; não carregar todos
  nem usar ícones como único rótulo de ação. Fotos e ilustrações da demonstração
  são decorativas/de prévia, não conteúdo do FuelFinder.

### Estado da migração visual e aplicação no FuelFinder

- **Decisão registrada:** Bootstrap 5 substitui Tailwind; versão adotada no
  frontend é 5.2.3, carregada pelo CDN jsDelivr. Vanilla JS e Leaflet são
  preservados. Não manter as folhas/frameworks Tailwind e Bootstrap em paralelo.
- **Implementado nesta etapa:** páginas HTML públicas e administrativas usam o
  CSS e JavaScript Bootstrap e o CSS próprio `static/css/styles.css` concentra
  os tokens LUNO (Nunito, primária `#2794eb`, secundária `#00ac9a`, fundo
  `#f4f7f6`, superfícies brancas e bordas claras). Cards, campos, botões,
  estados de formulário, grid responsivo e cabeçalho/menu usam componentes e
  classes Bootstrap 5. CSS original do tema e classes semânticas da aplicação
  continuam em uso apenas para os elementos de domínio sem equivalente pronto.
- **Por tela:** a busca usa formulário destacado antes do mapa; mapa Leaflet e
  lista são colunas empilháveis, com lista acessível e cards de posto. Login e
  cadastro mantêm cartão central sem opções não suportadas. Veículos preservam
  cadastro em cartão e lista em cards/linhas adaptáveis. Recomendações destacam
  entrada e resultado em conteúdo empilhado. Detalhe de posto organiza preço,
  rotas, avaliações e formulário em cartões. Administração mantém páginas
  focadas por tarefa; formulário de postos e filtros/filas ANP/preços/moderação
  usam grid e controles Bootstrap, e links do painel são cards.
- **Breakpoints já aplicados:** classes Bootstrap `sm` (576px), `md` (768px),
  `lg` (992px), `xl` (1200px) e `xxl` (1400px); regras próprias recolhem o menu
  abaixo de 1200px, empilham componentes em 768px ou menos e apertam o
  espaçamento em telas abaixo de 576px. Busca, cartões, formulários e ações
  ocupam largura disponível em celular; mapa e lista ficam empilhados, com
  altura mínima do mapa reduzida. Tabelas devem permanecer encapsuladas em
  `.table-responsive` quando surgirem listas realmente tabulares.
- **Não portado deliberadamente:** sidebar fixa/densa do LUNO é substituída por
  navegação FuelFinder em cabeçalho compacto, colapsada em larguras abaixo de
  1200px; plugins jQuery, exemplos de CRM/e-commerce, tabelas de demonstração,
  imagens e ícones/fontes adicionais não são copiados. Conteúdo da API e os
  fluxos de autenticação, GPS, Leaflet e autorização permanecem na lógica atual.
- **Ainda pendente:** teste visual e funcional em navegador; confirmar os
  estados renderizados dinamicamente em cada página, o mapa em larguras
  estreitas, a interação do menu, a legibilidade das filas administrativas e a
  experiência de teclado/touch. A carga por CDN depende de rede externa; a
  migração não deve ser considerada concluída antes da validação e da decisão
  operacional sobre a dependência externa.

### Estrutura e recursos que podem ser adaptados

| Referência LUNO | Uso recomendado no FuelFinder | Adaptação necessária |
|---|---|---|
| Shell `layout-1`, `sidebar`, `wrapper`, cabeçalho sticky, `page-toolbar`, `page-body` e breadcrumb | Base comum das áreas autenticadas; diferenciação visual de motorista e administrador por itens de navegação autorizados | Simplificar a navegação para Mapa, Veículos, Recomendações e, somente para admin, Gestão/Moderação/Importação. Não reutilizar menus de demonstração, seletor de projeto ou conteúdo de marketing. |
| `auth-signin.html` e parciais de autenticação | Composição centrada em cartão para login e cadastro | Manter apenas fluxos oferecidos pela API; remover login social, “remember me” ou outros controles sem suporte. Traduzir todo texto visível para pt-BR. |
| Cards (`.card`, header/body/footer), badges e list groups | Resultados de postos, preços, avaliações, veículos e opções de recomendação | Exibir dados e ações reais do domínio, definir ordem de leitura e estados vazio/carregando/erro/sucesso. |
| Formulários Bootstrap (`.form-control`, `.form-select`, `.form-check`, input groups) | Busca por local/raio, veículos, avaliações, filtros e formulários administrativos | Labels explícitos, ajuda/validação associadas, mensagens em pt-BR e unidades de domínio; não depender de placeholder como label. Em telas pequenas, rótulo acima do controle. |
| `.table-responsive`, tabelas, badges e paginação documentadas em `src/docs/` | Listas administrativas de postos, preços, reviews e histórico ANP quando a tabela realmente facilitar comparação | Em celular, reduzir colunas secundárias ou transformar linhas em cards; permitir rolagem horizontal somente como fallback localizado em tabelas inevitavelmente tabulares, sem alargar a página inteira. |
| Alertas Bootstrap (`.alert-*`), toasts, spinners e feedback inline | Erros de API, GPS, importação e confirmações de operações | Usar mensagem acionável, texto além da cor e região acessível (`role="alert"`/`aria-live`) adequada à urgência; preferir feedback junto ao contexto. |
| Modal com backdrop e diálogo scrollable | Confirmações pontuais e criação/edição curta de veículo ou registro | Não colocar fluxo longo ou formulário essencial em modal pequeno. No celular usar diálogo responsivo/tela cheia e rolagem interna com botões acessíveis. |
| Collapse, dropdown, nav-tabs, navbar toggler e sidebar deslizante | Submenus de navegação, filtros avançados e alternância de lista/mapa | Preservar `aria-expanded`, foco, Escape e fechamento previsível. Evitar dropdown como único meio de navegação ou abas que escondam conteúdo crítico sem indicação. |
| Sistema de grid (`container-fluid`, `row`, `col-*`, `g-*`) | Reorganizar conteúdo sem layouts fixos; cartões podem ocupar uma, duas ou mais colunas conforme espaço | Não fixar larguras estreitas nem usar valores que provoquem overflow; deixar ações quebrarem/empilharem em ordem lógica. |
| Rodapé flexível (`page-footer`) | Rodapé discreto com identificação do FuelFinder, se necessário | Remover marcas, links comerciais, portfólio e licenças do template; em celular, empilhar conteúdo e centralizar quando apropriado. |

Não há um componente de mapa de postos pronto na referência. Leaflet 1.9.4 e
OpenStreetMap permanecem no mapa; o cartão do mapa deve seguir as superfícies,
bordas, cabeçalho e respiro LUNO, com controles do mapa próprios e utilizáveis
por toque. Cards de postos e painel/mapa precisam permanecer legíveis quando
reorganizados em celular.

### Bootstrap, dependências e limites de reutilização

- A fonte do template usa Bootstrap 5 (dependência `^5.2.0`) e o JavaScript
  correspondente, empacotado com Popper; há suporte a classes responsivas,
  grid, cards, forms, buttons, alerts, tables, collapse, dropdowns, modals,
  navs e pagination.
- O build LUNO agrega CSS/SCSS customizado, ícones, jQuery e plugins (por
  exemplo, DataTables, date pickers, Select2, SweetAlert2, charts). Não copiar
  o bundle inteiro nem inicializar plugins globais sem necessidade. O frontend
  atual é Vanilla JS com módulos ES e Bootstrap via CDN. Não introduzir
  Tailwind em paralelo: manter uma só base de componentes e CSS.
- Os arquivos de fonte incluem imports externos de Google Fonts e CDNs para
  algumas famílias de ícones; revisar disponibilidade, privacidade, CSP e
  dependência de rede antes de os reutilizar. Preferir assets aprovados e
  controlados pelo projeto quando isso for permitido pela licença.
- Integração aprovada: usar Bootstrap 5.2.3 como base responsiva e traduzir os
  tokens/componentes visuais necessários do LUNO para CSS próprio enxuto;
  manter Vanilla JS, Leaflet e os contratos atuais. Não importar sem auditoria
  o CSS compilado completo nem dependências jQuery.
- O SCSS do template define breakpoints próprios além dos breakpoints nativos
  Bootstrap. As variáveis LUNO são 567, 640, 768, 992, 1024, 1200, 1280 e
  1440px; Bootstrap usa xs <576, sm 576, md 768, lg 992, xl 1200 e
  xxl 1400px. O shell oculta/desloca a sidebar até aproximadamente 1200px e
  usa toggles distintos a partir de `xl`; não generalizar esses números como
  breakpoints nativos nem duplicar regras incompatíveis.
- A folha global do template desativa seleção de texto e remove outline de
  foco em links, padrões que prejudicam usabilidade/acessibilidade. Não os
  herdar: texto deve continuar selecionável e foco de teclado precisa de
  indicador visível de alto contraste. Desativar animações não essenciais
  quando `prefers-reduced-motion` estiver ativo.
- O README do template identifica WrrapTheme/ThemeMakker e informa que imagens
  são apenas para prévia; a pasta também contém recursos e créditos de terceiros.
  Antes de copiar arquivos, fontes, imagens, ícones ou CSS compilado para os
  assets do FuelFinder, confirmar licença e redistribuição permitida. Preferir
  usar a referência visualmente, implementando componentes próprios.

### Aplicação por tela

- **Login/cadastro:** usar a proporção e o cartão centralizado de
  `auth-signin.html`, campos claros, hierarquia título/explicação/formulário e
  ação primária evidente. Usar logo FuelFinder e apenas opções de autenticação
  suportadas. Em celular, cartão com largura disponível, margens seguras e
  sem coluna decorativa que roube espaço.
- **Mapa e busca:** aplicar o shell autenticado, cabeçalho de página, cartão de
  busca/filtros e cartão de mapa Leaflet. Busca/localização e raio devem
  permanecer fáceis de alcançar em todas as larguras. A lista de postos é
  composta por cards concisos com preço, combustível, distância, nota e ações
  essenciais; mostrar preços e datas conforme os dados reais da API.
- **Detalhe do posto:** usar cabeçalho compacto com nome/localização e cards ou
  seções para preços, coleta, avaliações e rotas. Não simular conteúdo de
  demonstração. Reorganizar ações em linha apenas se couberem sem apertar área
  de toque.
- **Veículos:** usar cards/list-group para veículos e formulários responsivos
  para criar/editar; explicitar unidades (L/m³, km/L/km/m³). Confirmação de
  exclusão precisa ser clara e não depender apenas de modal.
- **Recomendações:** destacar resultado e explicação no primeiro card; opções
  comparáveis em lista/cards, com custo/unidade legível. Preservar dados e
  ordenação fornecidos pela API, sem métricas decorativas do dashboard.
- **Administração:** filtros no topo em formulário compacto e responsivo;
  tabelas/listas para postos, preços e moderação apenas quando colunas forem
  úteis; badges para status com texto; paginação quando suportada pelo contrato.
  Importação ANP em cartão com campos de período, estados do processamento e
  resumo acessível. Não integrar charts ou calendários do tema sem requisito
  funcional correspondente.

### Responsividade e experiência móvel

Usar o grid Bootstrap e validar os breakpoints nativos junto aos pontos próprios
do LUNO. O comportamento abaixo é o direcionamento a implementar e testar; não
declara que a interface atual já o cumpra integralmente.

| Largura/contexto | Navegação e conteúdo | Componentes e interação |
|---|---|---|
| `xxl` (>=1400px), incluindo 1440px | Sidebar persistente e área de conteúdo ampla; usar limite de largura para textos e tabelas densas. Busca e título não devem ficar dispersos. | Mapa e lista podem ficar lado a lado com largura mínima útil; cards em grid com `g-3`/`g-4`; tabelas completas. |
| `xl` (1200–1399px) | No LUNO a sidebar está perto do ponto de transição para recolhida; conferir o shell na borda de 1200px. Manter área principal sem sobreposição. | Duas colunas somente se mapa, cards e controles mantiverem largura utilizável; caso contrário, priorizar mapa/lista em sequência. |
| `lg` (992–1199px) | Sidebar torna-se drawer/off-canvas recolhível conforme o SCSS; inicia fechada em toque e não cobre permanentemente o conteúdo. | Empilhar filtros ou usar grid de duas colunas para campos; mapa/lista em sequência ou alternância explícita, sem reduzir mapa a faixa estreita. |
| `md` (768–991px) | Drawer permanece sob demanda; título, ações e breadcrumb podem quebrar em linhas. | Cards em uma ou duas colunas conforme largura; formulários empilhados; tabelas simplificadas ou cards; popovers/dropdowns não podem sair da viewport. |
| 576–767px (Bootstrap `sm`; inclui o corte LUNO de 640px) | Tratar como faixa compacta: cabeçalho mínimo, busca essencial no corpo ou em controle claramente identificado; esconder apenas ações secundárias. | Cards ocupam largura total; inputs e botões com largura adequada; filtros avançados em collapse; navegação com rótulos ou ícones acompanhados por texto acessível. |
| <=567px, incluindo 375px e 360px | Drawer fechado por padrão; overlay/backdrop, controle de fechar e tecla Escape; restaurar foco ao botão de menu. Cabeçalho sem overflow. | Uma coluna; alvos de toque de pelo menos 44×44px como meta; botões de ação empilhados; formulário em coluna; tabela convertida em cards ou rolagem horizontal confinada; modal em tela cheia/altura disponível; nada de rolagem horizontal na página. |

Aplicar ainda estas regras em todas as faixas:

- Em celulares pequenos, manter conteúdo com padding lateral consistente (cerca
  de 16px como referência inicial), espaçamento entre controles e foco visível;
  não fixar altura de cards ou cortar rótulos/valores longos.
- Cabeçalho LUNO oculta a busca a partir de `md` para baixo. Para o FuelFinder,
  a busca de postos é função primária e não deve simplesmente desaparecer:
  posicioná-la no início do conteúdo ou oferecer alternância com nome/estado
  acessível.
- No mapa, não comprimir marcadores/controles nem deixar ações Leaflet sob o
  drawer. Dimensionar altura para toque e leitura, e oferecer alternativa de
  lista acessível. Ao alternar mapa/lista em telas estreitas, preservar filtros
  e seleção do posto.
- Formulários de várias etapas permanecem em fluxo vertical; labels acima dos
  inputs; mensagens de validação junto ao campo e resumo anunciado. Aumentar
  padding/altura de controles e evitar grupos de botões menores que a área de
  toque recomendada.
- Usar `.table-responsive` apenas como contenção para a tabela, com foco e
  indicação de rolagem; não fazer o `body` rolar na horizontal. Preferir cards
  para dados de posto/veículo/review que tenham poucas propriedades.
- Modais devem usar `modal-dialog-centered` quando adequado e
  `modal-dialog-scrollable`; em celular ocupar a largura/altura útil, manter
  cabeçalho/fechar e rodapé/ações alcançáveis, respeitar teclado virtual e não
  esconder o botão de confirmação.
- Validar dimensões em 360, 375, 567, 576, 640, 768, 992, 1024, 1200, 1280,
  1400 e 1440px, além de orientação paisagem; observar cortes, sobreposições,
  foco, rolagem, carregamento, vazio, sucesso, erro e permissões de GPS.
- Todo texto visível adaptado ou inspirado no LUNO — títulos, navegação,
  botões, rótulos, ajuda, mensagens e placeholders — deve ser escrito em
  português do Brasil (pt-BR). Manter em inglês somente identificadores
  técnicos, nomes próprios de APIs/bibliotecas e termos que não devam ser
  traduzidos.

## Estrutura entregue

```text
src/main/resources/static/
├── index.html                 # Home pública (página padrão em /)
├── map.html                   # Busca e mapa para usuários autenticados
├── login.html
├── vehicles.html
├── station-detail.html
├── recommendations.html
├── admin/
│   ├── index.html
│   ├── stations.html
│   ├── prices.html
│   ├── reviews.html
│   └── anp-import.html
├── css/styles.css
└── js/
    ├── api.js
    ├── auth.js
    ├── public-home.js
    ├── shell.js
    ├── ui.js
    ├── map.js
    ├── home.js
    ├── station-detail.js
    ├── vehicles-page.js
    ├── recommendations-page.js
    └── admin-page.js
```

- `index.html` é a apresentação pública do produto e benefícios; visitantes
  recebem links públicos e o botão visível **Entrar**, que leva ao login.
- O Spring Boot serve `index.html` como welcome page em `/`; não é necessário
  encaminhar visitantes para o formulário de login.
- `login.html` não repete a ação Entrar no cabeçalho e não oferece navegação
  restrita a usuários autenticados. Após login/registro, o destino padrão é
  `map.html`; o parâmetro `next` continua limitado a caminhos internos.
- `map.html` contém a busca e o mapa que antes ocupavam `index.html`. A página
  exige sessão no controle do frontend. O menu e as ações mudam conforme a
  autenticação/papel e atualizam no evento de mudança de sessão. A autorização
  efetiva de páginas e APIs continua sendo responsabilidade do backend.
- Na home pública, usuários não autenticados veem somente navegação para
  conteúdo público e acesso ao login; com sessão válida, a navegação oferece
  áreas correspondentes ao papel e ações autenticadas. Ao sair, a sessão é
  limpa, a navegação é atualizada e o visitante retorna à home.

## Fluxos integrados

- **Busca entregue atualmente:** consulta por cidade/bairro/CEP com fallback textual e
  localização GPS com consentimento explícito, escolha de raio, mapa/lista de
  postos e marcadores. Ao abrir um popup, os preços são carregados pelo endpoint
  do posto; coordenadas nulas não geram marcadores.
- **Detalhe do posto:** preços e datas de coleta, avaliações publicadas,
  submissão de avaliação por `ROLE_DRIVER`, gestão da própria avaliação e links
  externos para Google Maps e Waze.
- **Home e navegação:** apresentação pública em `/` e `/index.html`, login em
  `/login.html` e mapa em `/map.html`. Menu/ações dependem da sessão válida e
  do papel; login omite o botão duplicado no cabeçalho; entrada e saída emitem
  mudança de sessão para atualizar a interface.
- **Autenticação:** login/registro, restauração via refresh cookie, logout,
  navegação e proteção de páginas por papel no cliente. A autorização dos dados
  permanece sempre no backend.
- **Veículos:** criação com unidades consistentes (`km/L`, `km/m³`, L e m³),
  consulta, edição do apelido e exclusão.
- **Recomendação:** seleção de veículo e coordenadas via GPS ou entrada manual;
  exibe combustível recomendado, explicação, paridade e todas as opções
  devolvidas pelo backend.
- **Admin:** gestão de postos e preços, fila de avaliações filtrável por status
  e importação ANP com histórico e detalhes do resultado.

## Sessão e segurança do navegador

- Access JWT fica somente na memória JavaScript, é enviado em `Authorization:
  Bearer ...` às chamadas protegidas e é renovado uma vez após resposta 401.
- Refresh token é exclusivamente cookie `HttpOnly`, transmitido com
  `credentials: include` somente nos endpoints de autenticação que o utilizam.
- Não há persistência de token/perfil em `localStorage` ou `sessionStorage`;
  tokens não são adicionados a URLs nem logados.
- Conteúdo vindo da API é inserido usando `textContent`/elementos DOM, não como
  HTML executável.
- **Situação atual da implementação:** páginas e assets estáticos podem ser
  carregados sem autenticação; APIs aplicam as regras por endpoint, e algumas
  rotas de leitura de postos/preços/avaliações ainda aceitam acesso público.
  A lista de origens padrão inclui
  `http://localhost:8080` para que refresh/logout com cookie funcionem quando a
  UI é servida pelo próprio backend. Em ambientes externos, definir
  `APP_SECURITY_ALLOWED_ORIGINS` explicitamente e configurar o cookie seguro
  conforme HTTPS.
- Bootstrap, Nunito, tiles OpenStreetMap e Leaflet carregam de CDNs externos. O mapa usa
  marcador de localização desenhado (não precisa de imagens de marcador
  adicionais).

## Endpoints administrativos

Além dos endpoints de postos, preços e importação ANP já existentes, a UI usa
`GET /admin/reviews?status=PENDING|APPROVED|REJECTED`. A rota é restrita a
`ROLE_ADMIN` e retorna os nomes dos autores para a fila de moderação. A API
existente de moderação continua sendo `PATCH /reviews/{id}/moderation`.

## Acessibilidade e responsividade

- Formulários associados a labels, estados de carregamento/erro em regiões
  `aria-live`, navegação identificada por `aria-label` e elementos semânticos.
- Foco visível no teclado e controles com tamanho mínimo de toque.
- Layout mobile-first: busca e mapa empilham em telas estreitas; lista e mapa
  ficam lado a lado em telas maiores.

## Pendências após validação de uso

Os itens desta seção permanecem pendentes; os fluxos implementados estão
descritos acima.

### Acesso, autenticação e navegação

- A rota raiz `/` apresenta a home pública com informações e benefícios do
  produto; o botão Entrar leva ao login. O mapa fica em `/map.html`.
- Login e cadastro, e os endpoints mínimos de registro, login e renovação de
  sessão, são públicos. A interface guarda o mapa e as ações autenticadas,
  atualiza navegação após login/logout, filtra menu por papel, e preserva o
  destino interno solicitado após autenticar.
- A proteção deve existir no backend para páginas/recursos internos e APIs de
  dados, não somente por ocultação de navegação ou guardas JavaScript.
- O arquivo HTML do mapa continua acessível como asset estático público sob a
  configuração atual; a guarda de sessão do navegador não substitui
  autorização server-side.
- Atualmente, algumas APIs `GET` de domínio são públicas conforme o contrato
  herdado das Fases 4 a 6; migrá-las para autenticação faz parte deste trabalho
  planejado.

### Layout e estados de interface

- Seguir as orientações e o mapeamento de componentes da seção
  [Referência visual e decisão de integração](#referência-visual-e-decisão-de-integração)
  desta fase. O LUNO orienta composição e adaptação; telas/conteúdo de outros
  domínios não são requisitos do FuelFinder.
- A navegação atual é um cabeçalho FuelFinder compacto (em vez da sidebar
  administrativa LUNO), com menu colapsável por controle em larguras abaixo de
  1200px. Em telas menores, iniciar recolhido;
  expor o estado pelo botão (`aria-expanded`) e permitir fechar pelo controle,
  ao navegar e pela tecla Escape. Definir e testar estados aberto e fechado,
  indicação da seção atual, foco do teclado, retorno de foco e fechamento
  acessível. Usar os breakpoints Bootstrap e customizações LUNO documentados na
  seção de responsividade, sem presumir equivalência entre eles.
- Aplicar espaçamento consistente e suficiente entre textos, campos, cartões
  e botões; dimensionar áreas clicáveis para toque móvel sem controles
  excessivamente próximos. Aplicar pt-BR a todo texto de interface e validar
  nas larguras enumeradas na seção de responsividade.
- Cada tela/ação relevante deve oferecer estados de carregamento, vazio,
  sucesso e erro, com mensagens acionáveis e regiões acessíveis (`aria-live`).
- A decisão de substituir Tailwind foi tomada e a versão Bootstrap 5.2.3 está
  fixada nas páginas. Antes de distribuir assets do LUNO, verificar direitos e
  licença; assets da referência ainda não foram copiados.

### Mapa e localização

- Após login e ao abrir a área do mapa, solicitar consentimento de
  geolocalização. Se concedido, mostrar estado de carregamento, centralizar o
  mapa na posição atual e buscar automaticamente postos próximos, sem exigir
  uma primeira busca manual.
- O raio da busca deve ser configurável. Preservar como referência o padrão de
  5 km documentado na Fase 4; opções adicionais e eventual configuração
  administrativa ainda precisam de decisão.
- Em permissão negada, localização indisponível ou erro, informar a causa sem
  bloquear a aplicação e permitir pesquisa ou seleção manual de localização.
  Nunca obter coordenadas sem consentimento.

### Pendências de decisão e validação

- Definir opções de raio além do padrão atual, validar o breakpoint final do
  menu e a escala de espaçamento após testes de usabilidade.
- Definir se os recursos de Bootstrap e Nunito permanecerão em CDN ou serão
  fornecidos localmente, considerando rede, CSP, disponibilidade e privacidade.
- Validar licença antes de redistribuir qualquer asset do LUNO.
- Executar validação visual em todas as larguras documentadas, em paisagem e
  nos estados de carregamento/vazio/sucesso/erro; garantir interface pt-BR sem
  cortes nem rolagem horizontal global.
- Planejar testes de autorização server-side para navegação direta,
  redirecionamento de retorno, sessão expirada e papel insuficiente, além de
  testes visuais desktop/mobile e dos estados da geolocalização.

## Critérios de aceitação

- [x] Home pública padrão em `/` e `/index.html` com benefícios e CTA para login.
- [x] Cabeçalho da tela de login sem botão Entrar duplicado nem menu sem opções úteis.
- [x] Navegação e conteúdo atualizados ao entrar/sair e filtrados por autenticação/papel.
- [x] Mapa separado em `map.html`, com guarda de sessão no frontend.
- [ ] Proteger páginas internas também no servidor; HTML estático e algumas leituras continuam públicos conforme configuração atual.
- [x] Leaflet 1.9.4 e tiles OpenStreetMap com marcadores baseados nos resultados.
- [x] GPS e fallback por pesquisa textual.
- [x] Preços e data de coleta disponíveis no detalhe e no popup selecionado.
- [x] Deep links seguros para Google Maps e Waze.
- [x] Fluxos de login/registro, veículos, avaliações e recomendações integrados.
- [x] Painel admin com postos, preços, moderação por status e importação ANP.
- [x] JWT somente em memória e refresh cookie limitado aos endpoints necessários.
- [x] Assets estáticos acessíveis sem autenticação; APIs permanecem protegidas.
- [x] Estrutura responsiva, semântica e com feedback acessível para operações.
- [x] Teste de integração confirma entrega das páginas/assets sem autenticação e
  autorização administrativa para a fila de moderação.
- [ ] Migração visual LUNO/Bootstrap 5.2.3 aprovada e aplicada; falta revisão
  visual/funcional completa e validação integrada antes de considerá-la concluída.

## Verificações executadas

```powershell
mvn -q "-Dtest=ReviewServiceTest,ReviewControllerIntegrationTest,StationControllerIntegrationTest,SecurityConfigTest,OriginValidationFilterTest" test
```

Verificação manual recomendada: conferir GPS (permitido/negado), login e
refresh/logout com navegador em `http://localhost:8080`, fluxos administrativos
com conta `ROLE_ADMIN` e layout em larguras 375, 768 e 1440 px.

Nesta etapa, `mvn -q -f app\pom.xml test`, verificação sintática dos módulos
JavaScript (`node --input-type=module --check`) e `git diff --check` foram
executados com sucesso. Smoke test visual via servidor estático local confirmou
que o mapa não causa rolagem horizontal nas larguras verificadas (360, 375,
576, 640, 768, 992, 1024, 1200, 1280, 1400 e 1440px), que o menu recolhe em
larguras menores que 1200px e que o menu móvel abre/fecha por Escape. Como o
servidor estático não implementa a API, estados de sessão e dados reais não
foram validados nessa verificação visual.

Para a home/login/mapa, o navegador confirmou a landing pública em `/`, CTA
para `/login.html`, ausência do botão Entrar no cabeçalho de login, entrada
simulada redirecionando para `/map.html`, navegação de motorista mostrando
mapa/veículos/recomendações sem link administrativo, saída simulada
restaurando as opções de visitante e menu móvel expandindo sem overflow
horizontal em 360px. A autenticação foi simulada no navegador; não substitui o
teste com backend e sessão reais.
