# Arquitetura do Sistema — FuelFinder

Este documento estabelece formalmente a arquitetura de software, modelo de dados, catálogo de endpoints REST, integrações externas e fluxos operacionais da plataforma **FuelFinder**.

Ele foi estruturado especificamente como a **especificação arquitetural executável e base de implementação para a próxima aula**.

---

## 1. Visão Geral da Arquitetura

O FuelFinder adota a arquitetura de **Monolito Modular em Camadas**, expondo uma **API REST stateless** consumida por uma interface **Web Responsiva** com visualização cartográfica interativa.

```text
[ Cliente Web Responsivo ] (HTML5 / Tailwind CSS / Vanilla JS / Leaflet 1.9.4)
              │
              │ HTTPS / JSON (Bearer JWT)
              ▼
[ Backend Monólito Modular ] (Spring Boot 3.4 / Java 21 LTS)
  ├── Security Filter (JWT & RBAC)
  ├── Controllers REST & DTOs (Records)
  ├── Services de Domínio & Recomendações (Spring AI)
  └── Repositories (Spring Data JPA)
              │
              │ JDBC / SQL
              ▼
[ Banco de Dados Relacional ] (PostgreSQL 16+)
```

### 1.1 Justificativa Arquitetural
* **Entrega Ágil no MVP:** O monólito modular permite desenvolver e testar rapidamente todas as funcionalidades em uma única base de código, com deploy simples e transações ACID nativas no PostgreSQL, sem o overhead operacional e latência de microsserviços.
* **Fronteiras Claras de Domínio:** Cada domínio de negócio (`auth`, `user`, `vehicle`, `station`, `fuel`, `price`, `review`, `recommendation`, `anp`) é isolado em seu próprio módulo, com interfaces públicas explícitas (`Service`), facilitando a futura extração para microsserviços caso a volumetria justifique.
* **Desacoplamento Total:** O backend é estritamente stateless e orientador das regras de negócio. O frontend é uma aplicação web leve e responsiva, focada na experiência do motorista e renderização de mapas via Leaflet 1.9.4.

---

## 2. Diagrama Geral da Arquitetura em Camadas

O diagrama abaixo ilustra a segregação entre as camadas de Frontend, Backend, Banco de Dados e Serviços Externos:

```mermaid
flowchart TB
    subgraph FRONTEND["1. Camada Frontend (Web Responsiva)"]
        UI["Interface do Usuário (HTML5 Semântico / Tailwind CSS)"]
        LEAFLET["Leaflet 1.9.4 (Renderizador de Mapa)"]
        OSM_TILES[("OpenStreetMap (Camada de Tiles Abertos)")]
        LEAFLET -. Consome Camada Cartográfica .-> OSM_TILES
    end

    subgraph BACKEND["2. Camada Backend (Spring Boot Monólito Modular)"]
        AUTH_FILTER["Filtro de Segurança JWT (OncePerRequestFilter)"]
        EX_HANDLER["Tratamento Centralizado de Erros (@RestControllerAdvice)"]
        
        subgraph MODULES["Módulos da Aplicação"]
            CTRL["Controllers REST (DTOs / Jakarta Validation)"]
            SRV["Services de Negócio (Transações / Regras / Fórmulas)"]
            REPO["Repositories (Spring Data JPA)"]
            AI["Spring AI (Mecanismo de Recomendação Inteligente)"]
        end

        AUTH_FILTER --> CTRL
        CTRL --> SRV
        SRV --> REPO
        SRV --> AI
        EX_HANDLER -. Intercepta Exceções .-> CTRL
    end

    subgraph DATABASE["3. Camada de Persistência"]
        POSTGRES[("PostgreSQL 16+ (Banco Relacional / Flyway)")]
        REPO --> POSTGRES
    end

    subgraph EXTERNAL["4. Integrações e Serviços Externos"]
        ANP_PORTAL["Portal ANP Dados Abertos (Série Histórica 2026/1)"]
        EXT_NAV["Aplicativos Externos de Navegação (Google Maps / Waze)"]
    end

    UI -- "Requisição HTTPS / JSON (Bearer Token)" --> AUTH_FILTER
    SRV -- "Pipeline de Ingestão Semestral (ETL)" --> ANP_PORTAL
    UI -- "Deep Link de Rota (Botão 'Rotas')" --> EXT_NAV
```

---

## 3. Tipos de Usuários e Suas Permissões (RBAC)

O controle de acesso é baseado em papéis (Role-Based Access Control):

```mermaid
flowchart LR
    subgraph PERFIS[Perfis de Acesso]
        MOTORISTA["Motorista (ROLE_MOTORISTA)"]
        ADMIN["Administrador (ROLE_ADMIN)"]
    end

    subgraph FUNCS[Funcionalidades Permitidas]
        F1[Consultar postos no mapa e lista ordenada]
        F2[Comparar preços e obter rotas externas]
        F3[Cadastrar e gerenciar seus próprios veículos]
        F4[Registrar e editar suas próprias avaliações]
        F5[Receber recomendações personalizadas de combustível]
        F6[Cadastrar, editar e inativar postos]
        F7[Cadastrar e atualizar preços manualmente]
        F8[Moderar avaliações inadequadas]
        F9[Gerenciar usuários, papéis e status de contas]
        F10[Disparar e auditar cargas de dados da ANP]
    end

    MOTORISTA --> F1
    MOTORISTA --> F2
    MOTORISTA --> F3
    MOTORISTA --> F4
    MOTORISTA --> F5

    ADMIN --> F1
    ADMIN --> F2
    ADMIN --> F6
    ADMIN --> F7
    ADMIN --> F8
    ADMIN --> F9
    ADMIN --> F10
```

* **Motorista (`ROLE_MOTORISTA`):** Perfil atribuído no autocadastro. Pode consultar postos, comparar preços, registrar veículos com consumos médios informados, emitir avaliações e receber recomendações personalizadas.
* **Administrador (`ROLE_ADMIN`):** Gestão governamental da plataforma. Pode gerenciar postos, retificar preços, moderar avaliações, ativar/bloquear contas e acompanhar os processos de carga da ANP.

---

## 4. Entidades Principais e Relacionamentos (ERD)

O diagrama a seguir define as entidades, chaves primárias, chaves estrangeiras, restrições e relacionamentos:

```mermaid
erDiagram
    USER ||--o{ VEHICLE : "cadastra"
    USER ||--o{ REVIEW : "emite"
    USER ||--o{ ANP_IMPORT_LOG : "dispara_ou_audita"
    STATION ||--o{ REVIEW : "recebe"
    STATION ||--o{ FUEL_PRICE : "pratica"
    FUEL_TYPE ||--o{ FUEL_PRICE : "classifica"

    USER {
        uuid id PK
        string email UK "E-mail único cadastral"
        string password_hash "Hash BCrypt"
        string full_name "Nome completo"
        string role "ROLE_MOTORISTA, ROLE_ADMIN"
        string status "ACTIVE, INACTIVE, BLOCKED"
        timestamp created_at
        timestamp updated_at
    }

    VEHICLE {
        uuid id PK
        uuid user_id FK "Proprietário condutor"
        string nickname "Apelido amigável"
        string brand "Marca"
        string model "Modelo"
        int year_manufacture "Ano de fabricação"
        string fuel_type_accepted "GASOLINE, ETHANOL, FLEX, DIESEL, CNG"
        decimal tank_capacity "Capacidade em litros"
        decimal avg_consumption_gasoline "km/L com gasolina"
        decimal avg_consumption_ethanol "km/L com etanol (para Flex)"
        timestamp created_at
        timestamp updated_at
    }

    STATION {
        uuid id PK
        string cnpj UK "CNPJ único oficial da revenda"
        string corporate_name "Razão Social"
        string trade_name "Nome Fantasia"
        string brand "Bandeira (BR, Shell, Ipiranga, etc.)"
        string street "Logradouro"
        string number "Número"
        string neighborhood "Bairro"
        string city "Município"
        string state "UF (2 letras)"
        string postal_code "CEP"
        decimal latitude "Latitude decimal"
        decimal longitude "Longitude decimal"
        decimal average_rating "Nota média agregada (1 a 5)"
        int total_reviews "Total de avaliações recebidas"
        string status "ACTIVE, INACTIVE"
        timestamp created_at
        timestamp updated_at
    }

    FUEL_TYPE {
        uuid id PK
        string code UK "GASOLINE_REGULAR, ETHANOL, DIESEL_S10, etc."
        string name "Nome legível"
        string unit_of_measure "R$/litro ou R$/m³"
        boolean active
    }

    FUEL_PRICE {
        uuid id PK
        uuid station_id FK "Posto revendedor"
        uuid fuel_type_id FK "Combustível comercializado"
        decimal sale_value "Preço de venda praticado"
        date collection_date "Data oficial da coleta ANP"
        string data_source "ANP_IMPORT ou MANUAL_ADMIN"
        timestamp created_at
        timestamp updated_at
    }

    REVIEW {
        uuid id PK
        uuid user_id FK "Motorista avaliador"
        uuid station_id FK "Posto avaliado"
        int rating "Nota inteira de 1 a 5"
        string comment "Comentário textual opcional"
        string status "APPROVED, PENDING, REJECTED"
        timestamp created_at
        timestamp updated_at
    }

    ANP_IMPORT_LOG {
        uuid id PK
        string file_name "Nome do arquivo semestral"
        string reference_period "Período (ex.: 2026-1)"
        string source_url "URL do portal de dados abertos"
        timestamp import_start
        timestamp import_end
        int total_records_read
        int total_records_imported
        string status "SUCCESS, PARTIAL, FAILED"
        string error_details
        uuid triggered_by FK "Usuário que disparou"
    }
```

---

## 5. Endpoints da API REST (Catálogo Completo com Contratos)

> [!IMPORTANT]
> **Correção Normativa de `PATH` para `PATCH`:**
> A especificação original grafou erroneamente o método como `PATH`. Esta arquitetura adota formalmente o método HTTP **`PATCH`** para atualizações parciais, em total conformidade com a RFC 5789.

### 5.1 Autenticação e Sessão (`/auth`)

| Método | Rota | Objetivo | Perfil Autorizado | Status Esperado |
| :---: | :--- | :--- | :--- | :--- |
| `POST` | `/auth/register` | Cadastra novo usuário motorista | Público | `201 Created` |
| `POST` | `/auth/login` | Autentica com e-mail/senha e emite token JWT | Público | `200 OK` |
| `POST` | `/auth/logout` | Encerra sessão ativa no cliente | Autenticado | `204 No Content` |
| `POST` | `/auth/refresh` | Renova token de acesso | Autenticado | `200 OK` |
| `GET` | `/auth/me` | Retorna dados e permissões do usuário autenticado | Autenticado | `200 OK` |
| `PATCH`| `/users/me` | Atualiza dados cadastrais do próprio usuário | Autenticado | `200 OK` |

#### Exemplo: `POST /auth/login`
**Request Payload:**
```json
{
  "email": "motorista@email.com",
  "password": "SenhaForte@2026"
}
```
**Response Payload (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "fullName": "Carlos Silva",
    "email": "motorista@email.com",
    "role": "ROLE_MOTORISTA"
  }
}
```

---

### 5.2 Veículos (`/vehicles`)

| Método | Rota | Objetivo | Perfil Autorizado | Status Esperado |
| :---: | :--- | :--- | :--- | :--- |
| `POST` | `/vehicles` | Cadastra veículo com consumo informado | `ROLE_MOTORISTA` | `201 Created` |
| `GET` | `/vehicles` | Lista veículos cadastrados pelo motorista | `ROLE_MOTORISTA` | `200 OK` |
| `GET` | `/vehicles/{id}` | Consulta detalhes de um veículo do motorista | `ROLE_MOTORISTA` | `200 OK` |
| `PATCH`| `/vehicles/{id}` | Atualiza dados cadastrais ou consumo do veículo | `ROLE_MOTORISTA` | `200 OK` |
| `DELETE`| `/vehicles/{id}` | Remove um veículo cadastrado | `ROLE_MOTORISTA` | `204 No Content` |

#### Exemplo: `POST /vehicles`
**Request Payload:**
```json
{
  "nickname": "Meu Onix",
  "brand": "Chevrolet",
  "model": "Onix 1.0 Flex",
  "yearManufacture": 2023,
  "fuelTypeAccepted": "FLEX",
  "tankCapacity": 54.0,
  "averageConsumptionGasoline": 13.5,
  "averageConsumptionEthanol": 9.2
}
```

---

### 5.3 Postos de Combustível (`/stations`)

| Método | Rota | Objetivo | Perfil Autorizado | Status Esperado |
| :---: | :--- | :--- | :--- | :--- |
| `GET` | `/stations` | Busca postos por geolocalização ou texto | Público / Autenticado | `200 OK` |
| `GET` | `/stations/{id}` | Consulta detalhes do posto e preços vigentes | Público / Autenticado | `200 OK` |
| `POST` | `/stations` | Cadastra novo posto revendedor | `ROLE_ADMIN` | `201 Created` |
| `PATCH`| `/stations/{id}` | Atualiza informações cadastrais do posto | `ROLE_ADMIN` | `200 OK` |
| `DELETE`| `/stations/{id}` | Inativa logicamente um posto | `ROLE_ADMIN` | `204 No Content` |

#### Exemplo: `GET /stations?latitude=-23.5505&longitude=-46.6333&radiusKm=5`
**Response Payload (200 OK):**
```json
[
  {
    "id": "c1f7b8a2-1234-4b5c-8901-abcdef123456",
    "corporateName": "AUTO POSTO CENTRAL LTDA",
    "tradeName": "Posto Central",
    "brand": "IPIRANGA",
    "address": "Av. Paulista, 1000 - Bela Vista, São Paulo - SP",
    "latitude": -23.5614,
    "longitude": -46.6558,
    "distanceKm": 2.45,
    "averageRating": 4.6,
    "totalReviews": 38,
    "prices": [
      {
        "fuelType": "GASOLINE_REGULAR",
        "saleValue": 5.79,
        "collectionDate": "2026-03-15",
        "dataSource": "ANP_IMPORT"
      },
      {
        "fuelType": "ETHANOL",
        "saleValue": 3.89,
        "collectionDate": "2026-03-15",
        "dataSource": "ANP_IMPORT"
      }
    ]
  }
]
```

---

### 5.4 Preços e Comparação (`/fuel-prices`)

| Método | Rota | Objetivo | Perfil Autorizado | Status Esperado |
| :---: | :--- | :--- | :--- | :--- |
| `GET` | `/stations/{id}/fuel-prices` | Lista preços cadastrados do posto | Público / Autenticado | `200 OK` |
| `POST` | `/stations/{id}/fuel-prices` | Registra novo preço de combustível | `ROLE_ADMIN` | `201 Created` |
| `PATCH`| `/stations/{id}/fuel-prices/{priceId}` | Atualiza preço existente | `ROLE_ADMIN` | `200 OK` |
| `GET` | `/fuel-prices/compare` | Compara postos ordenados por preço/distância | Público / Autenticado | `200 OK` |

---

### 5.5 Avaliações (`/reviews`)

| Método | Rota | Objetivo | Perfil Autorizado | Status Esperado |
| :---: | :--- | :--- | :--- | :--- |
| `GET` | `/stations/{id}/reviews` | Lista avaliações aprovadas do posto | Público / Autenticado | `200 OK` |
| `POST` | `/stations/{id}/reviews` | Registra avaliação com nota (1 a 5) | `ROLE_MOTORISTA` | `201 Created` |
| `PATCH`| `/reviews/{id}` | Edita a própria avaliação | `ROLE_MOTORISTA` | `200 OK` |
| `DELETE`| `/reviews/{id}` | Remove avaliação (autor ou moderação) | `ROLE_MOTORISTA` / `ROLE_ADMIN` | `204 No Content` |

---

### 5.6 Recomendações Inteligentes (`/recommendations`)

| Método | Rota | Objetivo | Perfil Autorizado | Status Esperado |
| :---: | :--- | :--- | :--- | :--- |
| `GET` | `/recommendations/fuel` | Retorna recomendação de melhor custo-benefício | `ROLE_MOTORISTA` | `200 OK` |

#### Exemplo: `GET /recommendations/fuel?vehicleId=...&latitude=-23.5505&longitude=-46.6333&radiusKm=5`
**Response Payload (200 OK):**
```json
{
  "vehicle": {
    "nickname": "Meu Onix",
    "fuelTypeAccepted": "FLEX"
  },
  "recommendedFuel": "ETHANOL",
  "explanation": "Com base no consumo informado (9.2 km/L no etanol vs 13.5 km/L na gasolina), o etanol tem custo de R$ 0,42/km contra R$ 0,43/km da gasolina no Posto Central, garantindo a maior economia.",
  "parityPercentage": 67.18,
  "topOptions": [
    {
      "stationId": "c1f7b8a2-1234-4b5c-8901-abcdef123456",
      "stationName": "Posto Central",
      "brand": "IPIRANGA",
      "fuelType": "ETHANOL",
      "price": 3.89,
      "distanceKm": 2.45,
      "costPerKm": 0.4228,
      "estimatedFullTankCost": 210.06,
      "estimatedRoundTripCost": 2.07
    }
  ]
}
```

---

## 6. Fluxos Principais do Sistema

### 6.1 Fluxo: Consulta Geolocalizada de Postos com Leaflet
```mermaid
sequenceDiagram
    autonumber
    actor Condutor as Motorista
    participant Browser as Web Browser (Leaflet 1.9.4)
    participant API as Backend (Spring Boot)
    participant DB as PostgreSQL

    Condutor->>Browser: Acessa tela de busca de postos
    Browser->>Browser: Solicita permissão de geolocalização via GPS
    alt Permissão Concedida
        Browser->>Browser: Obtém Latitude e Longitude
    else Permissão Recusada (Fallback)
        Condutor->>Browser: Digita bairro, cidade ou CEP
        Browser->>Browser: Converte endereço para coordenadas
    end
    Browser->>API: GET /stations?latitude=-23.55&longitude=-46.63&radiusKm=5
    API->>DB: Consulta postos e calcula distância em linha reta (Haversine)
    DB-->>API: Retorna postos dentro do raio
    API-->>Browser: Retorna lista ordenada de postos e preços (JSON)
    Browser->>Browser: Renderiza marcadores no mapa Leaflet e lista de opções
    Browser-->>Condutor: Exibe postos com preços, distâncias e botão 'Rotas'
```

### 6.2 Fluxo: Redirecionamento de Rotas para Waze / Google Maps
```mermaid
sequenceDiagram
    autonumber
    actor Condutor as Motorista
    participant Browser as Web Browser (FuelFinder)
    participant App as Waze / Google Maps (App Externo)

    Condutor->>Browser: Clica no botão 'Rotas' no posto selecionado
    Browser->>Browser: Monta deep link com as coordenadas de destino do posto
    Browser->>App: Abre URL externa de navegação
    App-->>Condutor: Apresenta rota viária calculada com trânsito em tempo real
```

### 6.3 Fluxo: Ingestão de Dados Públicos da ANP (ETL com Resiliência)
```mermaid
sequenceDiagram
    autonumber
    participant Admin as Agendador / Admin
    participant ANP_Module as Módulo ANP Integration
    participant Portal_ANP as Portal de Dados Abertos ANP
    participant DB as PostgreSQL

    Admin->>ANP_Module: Dispara processo de carga do arquivo semestral 2026/1
    ANP_Module->>Portal_ANP: Download do arquivo CSV da Série Histórica
    Portal_ANP-->>ANP_Module: Retorna arquivo bruto
    ANP_Module->>ANP_Module: Valida estrutura, layout e colunas obrigatórias
    alt Arquivo Inválido ou Falha de Rede
        ANP_Module->>DB: Registra falha em anp_import_logs (Status: FAILED)
        ANP_Module-->>Admin: Notifica erro e PRESERVA última base válida intacta
    else Arquivo Íntegro
        ANP_Module->>ANP_Module: Limpeza, normalização de CNPJ, nomes e preços
        ANP_Module->>DB: Carga em lote (Batch) com idempotência contra duplicatas
        ANP_Module->>DB: Registra auditoria em anp_import_logs (Status: SUCCESS)
        ANP_Module-->>Admin: Carga concluída com sucesso
    end
```

---

## 7. Registros de Decisão de Arquitetura (ADRs)

| ADR | Título | Decisão | Consequência |
| :--- | :--- | :--- | :--- |
| **ADR-001** | Monólito Modular | Adotado monólito modular com Spring Boot 3.4. | Simplicidade operacional no MVP, facilidade de deploy e integridade transacional. |
| **ADR-002** | Java 21 LTS e Records | Homologado Java 21 LTS como runtime oficial. | Suporte nativo a DTOs com Java Records, alta performance com Virtual Threads. |
| **ADR-003** | PostgreSQL 16 + Flyway | SGBD relacional com migrações declarativas. | Consistência ACID, cálculos trigonométricos de distância e rastreabilidade com Flyway. |
| **ADR-004** | Leaflet 1.9.4 + OpenStreetMap | Mapa gratuito com tiles abertos. | Custo zero de licença no MVP, visualização leve e compatível com navegadores móveis. |
| **ADR-005** | Distância em Linha Reta + Rotas Externas | Distância por Haversine e deep link para Waze/Google Maps. | Sem custo com APIs de roteamento; o condutor usa seu navegador GPS habitual. |
| **ADR-006** | Ingestão ANP com Resiliência | Pipeline ETL do arquivo semestral 2026/1 com fallback. | Alta disponibilidade; preservação da base anterior em caso de erro na fonte governamental. |
| **ADR-007** | Consumo Informado pelo Motorista | Consumo médio cadastrado diretamente pelo condutor. | Não depende de manuais ou tabelas externas no MVP; suporta paridade flex precisa. |
