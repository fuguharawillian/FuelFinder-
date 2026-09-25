# Arquitetura Planejada — FuelFinder

Este documento descreve a arquitetura planejada para a plataforma **FuelFinder**. 
O conteúdo aqui documentado representa o **estado planejado do sistema**, servindo como base técnica estrutural para futuras implementações, e não uma descrição de código pré-existente.

---

## 1. Visão Geral da Arquitetura

O FuelFinder é concebido inicialmente como um **Monólito Modular** que expõe uma **API REST**.

### 1.1 Motivação e Abordagem
* **Simplicidade Operacional:** A escolha do monólito modular para o Produto Mínimo Viável (MVP) visa manter uma base de código unificada, com baixo custo de infraestrutura e sem a sobrecarga operacional decorrente de arquiteturas de microsserviços (como orquestração distribuída, transações distribuídas e latência de rede interserviços).
* **Fronteiras Claras de Domínio:** O código é estruturado internamente em módulos funcionais com alto acoplamento interno e baixo acoplamento externo, permitindo futura evolução ou até mesmo extração de serviços isolados caso haja demanda de escala.
* **Comunicação Cliente-Servidor:** O monólito atua primordialmente como provedor de dados via API REST stateless para o cliente frontend responsivo.

---

## 2. Camadas da Aplicação

O fluxo de processamento de requisições segue a arquitetura em camadas tradicional do ecossistema Spring:

```text
[ Requisição HTTP ]
        ↓
    Security (Filtro JWT & RBAC)
        ↓
   Controller (Exposição REST & Validação de Entrada)
        ↓
    Service (Regras de Negócio & Orquestração)
        ↓
   Repository (Acesso a Dados & Consultas)
        ↓
    Database (PostgreSQL)
```

### 2.1 Detalhamento das Responsabilidades

#### Controller
* Ponto de entrada das requisições HTTP da API REST.
* Responsável por mapear rotas, receber payloads (Request DTOs), acionar validações e delegar o processamento para a camada de serviço.
* Retorna respostas padronizadas com os devidos códigos de status HTTP e Response DTOs.
* **Regra:** Não contém lógica de negócio nem regras de cálculo.

#### Service
* Camada central onde residem todas as **regras de negócio**, casos de uso e orquestrações do sistema (cálculo de consumo, comparação de preços, fórmulas de recomendação).
* Gerencia transações (`@Transactional`).
* Lança exceções de negócio em casos de violação de invariantes.

#### Repository
* Abstrai o acesso e persistência no banco de dados relacional (Spring Data JPA / Repositórios).
* Responsável pela execução de operações de CRUD e queries especializadas (ex.: busca de postos por coordenadas ou critérios de filtro).

#### Database
* Banco de dados relacional **PostgreSQL**, responsável pelo armazenamento persistente com integridade referencial e suporte a transações ACID.

#### DTO (Data Transfer Object)
* Objetos dedicados para transportar dados entre o cliente HTTP e a aplicação.
* Garante isolamento estrito entre o modelo de apresentação da API e o modelo de domínio interno.

#### Entity
* Classes de mapeamento objeto-relacional representando tabelas e relacionamentos no banco de dados.
* Contêm atributos persistíveis e anotações de integridade.

#### Security
* Intercepta todas as requisições HTTP para validar tokens JWT.
* Carrega o contexto de segurança e impõe verificações de autorização baseadas em papéis (RBAC - Motorista e Administrador).

#### Exception Handling
* Camada transversal de captura global de exceções (`@RestControllerAdvice`).
* Intercepta exceções de validação, de domínio e de infraestrutura, convertendo-as em respostas HTTP estruturadas com mensagens previsíveis para o cliente.

---

## 3. Domínios Principais (Módulos)

A aplicação é dividida logicamente nos seguintes domínios:

1. **Autenticação (`auth`):**
   * Emissão, validação e controle de tokens JWT; autenticação de credenciais.
2. **Usuários (`user`):**
   * Gestão de contas de usuários, perfis de acesso (Motorista, Administrador) e dados cadastrais.
3. **Veículos (`vehicle`):**
   * Registro e gestão de veículos dos usuários (marca, modelo, ano, tanque, combustível).
   * Registro do consumo médio informado pelo usuário e cálculo de consumo real baseado em abastecimentos.
4. **Postos (`station`):**
   * Cadastro, localização geográfica (latitude e longitude), dados cadastrais e status de postos de combustível.
5. **Combustíveis (`fuel`):**
   * Gestão dos tipos de combustíveis aceitos na plataforma (Gasolina Comum, Aditivada, Etanol, Diesel, etc.).
6. **Preços (`price`):**
   * Registro de preços de combustíveis vinculados a cada posto, com histórico e data/hora de atualização.
7. **Avaliações (`review`):**
   * Avaliações numéricas e comentários de motoristas sobre os postos; cálculo da nota média de cada posto.
8. **Recomendações (`recommendation`):**
   * Algoritmos de comparação de preços, ponderação de distância versus consumo e sugestão de melhor custo-benefício.
9. **Administração (`admin`):**
   * Painel de operações administrativas: gerenciamento de postos, preços oficiais e moderação de avaliações.

---

## 4. Frontend e Comunicação

* **Tecnologia:** Interface web responsiva baseada em HTML (acompanhado de CSS e JavaScript para interação e consumo de dados).
* **Responsividade:** O layout deve adaptar-se adequadamente tanto a dispositivos móveis quanto a desktops.
* **Comunicação com o Backend:** 
  * Totalmente desacoplada, consumindo a API REST do Spring Boot através de chamadas assíncronas (ex.: `fetch`).
  * O frontend gerencia localmente o token JWT recebido no login, enviando-o no cabeçalho `Authorization: Bearer <token>` em todas as requisições autenticadas.
* **Renderização de Mapas:** O frontend incorpora o componente de mapa Leaflet para visualização interativa dos postos e localização do usuário.

---

## 5. Banco de Dados e Evolução Geoespacial

* **Banco Principal:** **PostgreSQL** é o banco relacional adotado para toda a aplicação.
* **Persistência de Coordenadas no MVP:** As coordenadas geográficas dos postos (e as enviadas pelo usuário) são armazenadas como valores numéricos de ponto flutuante/precisão dupla (`latitude` e `longitude`).
* **Evolução Geoespacial com PostGIS:**
  * O uso da extensão **PostGIS** é reconhecido como a evolução natural para indexação espacial (GIST) e consultas espaciais nativas de alta performance.
  * Para o MVP inicial, a dependência direta do PostGIS permanece em avaliação, enquanto o cálculo de distância em linha reta pode ser executado diretamente via query matemática ou serviço da aplicação.

---

## 6. Geolocalização e Mapas

* **Autorização do Dispositivo:** O sistema solicita a geolocalização do dispositivo do usuário via API do navegador (Geolocation API) mediante consentimento explícito.
* **Busca Manual Alternativa:** Caso o usuário negue a permissão de geolocalização ou o dispositivo não forneça coordenadas, o sistema deve permitir a entrada manual de localização (endereço, CEP ou cidade/bairro).
* **Camada de Visualização de Mapas:**
  * Utilização da biblioteca **Leaflet 1.9.4**.
  * Utilização de camada de tiles gratuitos e abertos fornecidos pelo **OpenStreetMap**.
  * Exibição de marcadores para a posição do usuário e para os postos de combustível na região.
* **Cálculo de Distância:**
  * A distância entre o usuário e os postos é calculada em **linha reta** baseada nas coordenadas geográficas (fórmula trigonométrica de Haversine ou euclidiana esférica).
  * O sistema não calcula distâncias considerando trajetos viários no backend durante o MVP.

---

## 7. Rotas e Navegação Externa

* O FuelFinder **não implementa nem manterá um sistema próprio de navegação ponto a ponto por rotas viárias**.
* Para navegação até o posto selecionado, o sistema disponibilizará um botão de rota na interface.
* **Mecanismo:** Ao clicar no botão, o usuário será redirecionado para aplicativos externos de navegação estabelecidos (**Google Maps** ou **Waze**), repassando as coordenadas de destino via URL estruturada (ex.: `https://www.google.com/maps/dir/?api=1&destination=lat,lng` ou `waze://?ll=lat,lng&navigate=yes`).

---

## 8. Registros de Decisão Arquitetural (ADRs)

### ADR-001 — Adoção de Monólito Modular
* **Contexto:** O projeto FuelFinder necessita de velocidade de entrega na fase inicial de concepção e MVP, mantendo facilidade de implantação e manutenção sem incorrer na complexidade de microsserviços.
* **Decisão:** Adotar a arquitetura de monólito modular baseado em Spring Boot, segregando lógica de domínios em pacotes isolados com comunicação controlada entre serviços.
* **Consequências:** Simplificação do pipeline de build e deploy; simplificação do gerenciamento de transações; possibilidade de extração futura de domínios caso surja necessidade de escala.

---

### ADR-002 — Backend Baseado em Spring Boot e API REST
* **Contexto:** Necessidade de um backend robusto, tipado e com suporte consolidado para segurança, persistência e construção de APIs web.
* **Decisão:** Utilizar o framework Spring Boot expondo exclusivamente endpoints em padrão RESTful com comunicação em JSON.
* **Consequências:** Alto desacoplamento entre frontend e backend; facilidade de consumo por múltiplos clientes futuros (web, mobile); ecossistema consolidado de validação e tratamento de exceções.

---

### ADR-003 — Banco de Dados Relacional PostgreSQL
* **Contexto:** Os dados do FuelFinder possuem natureza altamente relacional (postos associados a combustíveis e preços históricos; veículos associados a usuários; avaliações vinculadas a usuários e postos), demandando integridade referencial estrita e transações ACID.
* **Decisão:** Adotar o PostgreSQL como banco de dados relacional principal do sistema.
* **Consequências:** Garantia de integridade e consistência relacional; suporte futuro transparente à extensão PostGIS para consultas geoespaciais avançadas.

---

### ADR-004 — Autenticação Stateless via JWT e Controle de Acesso Baseado em Papéis (RBAC)
* **Contexto:** O sistema atende a diferentes tipos de usuários (Motoristas e Administradores) e deve operar de forma desacoplada com o frontend responsivo sem reter sessão em memória no servidor.
* **Decisão:** Adotar autenticação stateless utilizando tokens JWT assinados pelo backend, com autorização baseada em papéis (RBAC: `ROLE_MOTORISTA` e `ROLE_ADMIN`).
* **Consequências:** Escalabilidade horizontal facilitada do backend; ausência de gerenciamento de sessão distribuída; o cliente frontend é responsável por armazenar e encaminhar o token em cada requisição.

---

### ADR-005 — Visualização de Mapas com Leaflet 1.9.4 e OpenStreetMap
* **Contexto:** A plataforma precisa apresentar visualmente a localização de postos e do motorista em um mapa interativo responsivo sem custos proibitivos de licenciamento no MVP.
* **Decisão:** Adotar a biblioteca Leaflet na versão 1.9.4 no frontend, consumindo a camada de mapas e tiles do OpenStreetMap.
* **Consequências:** Solução aberta, leve, livre de taxas por requisição de mapa no MVP e com excelente compatibilidade com navegadores móveis e desktop.

---

### ADR-006 — Distância em Linha Reta no MVP e Redirecionamento de Rotas para Serviços Externos
* **Contexto:** Calcular rotas viárias em tempo real exige algoritmos complexos de grafos ou consumo de APIs de roteamento proprietárias com custo e latência elevados.
* **Decisão:** O FuelFinder calculará a distância de proximidade em linha reta via coordenadas no MVP e delegará a navegação veicular por rota para aplicativos especializados externos (Google Maps e Waze) via links parametrizados.
* **Consequências:** Redução drástica da complexidade algorítmica e custo de infraestrutura no MVP; entrega imediata de valor ao usuário, permitindo utilizar seu aplicativo de GPS preferido no dispositivo.

---

## 9. Decisões em Aberto e Pendências Arquiteturais

```text
DECISÃO EM ABERTO
- Adoção imediata da extensão PostGIS no PostgreSQL para cálculo geoespacial no banco versus cálculo inicial de distância euclidiana/Haversine na camada de aplicação.
- Definição do mecanismo de cache (ex.: Redis ou cache em memória local) para mitigar consultas frequentes de preços e postos por coordenadas.
- Escolha da estratégia de deploy e empacotamento da aplicação (Docker, JAR único contendo frontend estático ou servidores web separados).
- Especificação de serviço de geocoding reverso para suporte à busca manual de localização por endereço textual.
```
