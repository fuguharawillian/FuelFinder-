# Fase 9 — Frontend Web Responsivo e Integração

## Objetivo

Entregar uma interface web responsiva para os fluxos do motorista e do
administrador, consumindo os endpoints reais das Fases 2 a 8. O frontend é
servido pelo Spring Boot a partir de `src/main/resources/static`, usa HTML
semântico, Vanilla JavaScript ES modules, Tailwind CSS via CDN e Leaflet 1.9.4.

## Estrutura entregue

```text
src/main/resources/static/
├── index.html
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
    ├── shell.js
    ├── ui.js
    ├── map.js
    ├── home.js
    ├── station-detail.js
    ├── vehicles-page.js
    ├── recommendations-page.js
    └── admin-page.js
```

## Fluxos integrados

- **Busca entregue atualmente:** consulta por cidade/bairro/CEP com fallback textual e
  localização GPS com consentimento explícito, escolha de raio, mapa/lista de
  postos e marcadores. Ao abrir um popup, os preços são carregados pelo endpoint
  do posto; coordenadas nulas não geram marcadores.
- **Detalhe do posto:** preços e datas de coleta, avaliações publicadas,
  submissão de avaliação por `ROLE_DRIVER`, gestão da própria avaliação e links
  externos para Google Maps e Waze.
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
- Tiles OpenStreetMap, Leaflet e Tailwind carregam de CDNs externos. O mapa usa
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

## Ajustes planejados após validação de uso (não implementados)

Os itens desta seção especificam trabalho futuro. Não alteram a descrição do
que já foi entregue e não significam que o código tenha sido modificado.

### Acesso, autenticação e navegação

- A rota raiz `/` deve apresentar/encaminhar para a tela de login, nunca abrir
  diretamente o mapa.
- Login e cadastro, e os endpoints mínimos de registro, login e renovação de
  sessão, são públicos. Mapa, postos, preços, avaliações, veículos,
  recomendações e painéis administrativos são áreas internas autenticadas;
  operações administrativas exigem adicionalmente `ROLE_ADMIN`.
- Uma rota interna acessada sem sessão encaminha para o login e preserva apenas
  um caminho interno permitido. Após autenticar, retornar à rota solicitada se
  o usuário estiver autorizado; bloquear destinos externos e tratar rota ou
  papel inválido com mensagem acessível e navegação segura.
- A proteção deve existir no backend para páginas/recursos internos e APIs de
  dados, não somente por ocultação de navegação ou guardas JavaScript.
- Atualmente, algumas APIs `GET` de domínio são públicas conforme o contrato
  herdado das Fases 4 a 6; migrá-las para autenticação faz parte deste trabalho
  planejado.

### Layout e estados de interface

- Usar a referência [Plano/img_exemplo.png](./img_exemplo.png) como inspiração
  visual limitada aos aspectos visíveis: cartões, hierarquia, respiro,
  composição móvel e uso de cor. Não assumir ou copiar conteúdo de outro
  domínio que não esteja representado.
- Planejar navegação em menu lateral em telas maiores e menu lateral recolhível
  por botão hambúrguer em telas menores. Em telas menores, iniciar recolhido;
  expor o estado pelo botão (`aria-expanded`) e permitir fechar pelo controle,
  ao navegar e pela tecla Escape. Definir e testar estados aberto e fechado,
  indicação da seção atual, foco do teclado, retorno de foco e fechamento
  acessível. O breakpoint exato permanece a validar com o conteúdo e testes
  responsivos.
- Aplicar espaçamento consistente e suficiente entre textos, campos, cartões
  e botões; dimensionar áreas clicáveis para toque móvel sem controles
  excessivamente próximos. A escala final de design deve ser validada nas
  larguras de teste existentes (375, 768 e 1440 px).
- Cada tela/ação relevante deve oferecer estados de carregamento, vazio,
  sucesso e erro, com mensagens acionáveis e regiões acessíveis (`aria-live`).

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

- Definir opções de raio além do padrão atual, breakpoint do menu e escala final
  de espaçamento após testes de usabilidade.
- Planejar testes de autorização server-side para navegação direta,
  redirecionamento de retorno, sessão expirada e papel insuficiente, além de
  testes visuais desktop/mobile e dos estados da geolocalização.

## Critérios de aceitação

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

## Verificações executadas

```powershell
mvn -q "-Dtest=ReviewServiceTest,ReviewControllerIntegrationTest,StationControllerIntegrationTest,SecurityConfigTest,OriginValidationFilterTest" test
```

Verificação manual recomendada: conferir GPS (permitido/negado), login e
refresh/logout com navegador em `http://localhost:8080`, fluxos administrativos
com conta `ROLE_ADMIN` e layout em larguras 375, 768 e 1440 px.
