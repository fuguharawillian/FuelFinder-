# Fase 0 — Visão Geral do Plano de Desenvolvimento

## 1. Sobre o FuelFinder

O **FuelFinder** é uma plataforma web responsiva para consulta, localização e comparação de preços de combustíveis no Brasil. Permite que motoristas encontrem postos próximos com os melhores preços, recebam recomendações personalizadas de combustível (etanol vs gasolina) e avaliem os estabelecimentos.

---

## 2. Decisões Técnicas Consolidadas

| Decisão | Escolha | Justificativa |
|---------|---------|---------------|
| **Linguagem / Runtime** | Java 21 LTS | Suporte a Records, Pattern Matching, Virtual Threads. Versão LTS estável. |
| **Framework Backend** | Spring Boot 3.4.x | Monólito modular, REST nativo, alta produtividade |
| **Banco de Dados** | PostgreSQL 16+ | ACID, funções trigonométricas nativas para Haversine |
| **Migrações** | Flyway 10.x+ | Versionamento declarativo do esquema |
| **Build** | Maven 3.9+ | Gerenciamento padronizado de dependências |
| **Frontend** | HTML5 + Tailwind CSS 3.4+ + Vanilla JS ES2023+ | Leve, responsivo, sem overhead de SPA |
| **Mapas** | Leaflet 1.9.4 + OpenStreetMap | Gratuito, leve, compatível com mobile |
| **Autenticação** | JWT (JJWT 0.12.6+) + Spring Security 6.4+ | Stateless, RBAC com BCrypt |
| **Validação** | Jakarta Bean Validation 3.0+ | Validação declarativa nos DTOs |
| **Documentação API** | Springdoc OpenAPI (Swagger) 2.7.x | Swagger UI interativo |
| **Recomendações** | Algoritmo puro (paridade + custo/km) | Sem dependência de modelo de IA |
| **Geocodificação** | Geoapify (plano gratuito) | Para endereços ANP sem coordenadas |
| **Navegação** | Deep links Google Maps / Waze | Sem custo com APIs de roteamento |
| **Testes** | JUnit 5 + Mockito + AssertJ | Unitários + integração |

---

## 3. Arquitetura de Alto Nível

```text
[ Cliente Web Responsivo ] (HTML5 / Tailwind CSS / Vanilla JS / Leaflet 1.9.4)
              │
              │ HTTPS / JSON (Bearer JWT)
              ▼
[ Backend Monólito Modular ] (Spring Boot 3.4 / Java 21 LTS)
  ├── Security Filter (JWT & RBAC)
  ├── Controllers REST & DTOs (Records)
  ├── Services de Domínio & Recomendações (Algoritmo Puro)
  └── Repositories (Spring Data JPA)
              │
              │ JDBC / SQL
              ▼
[ Banco de Dados Relacional ] (PostgreSQL 16+)
```

---

## 4. Fases do Desenvolvimento

O projeto está organizado em **9 fases incrementais**, cada uma com escopo definido, entregáveis e critérios de aceitação.

| Fase | Nome | Descrição |
|------|------|-----------|
| **1** | [Setup & Infraestrutura](01-setup-infraestrutura.md) | Projeto Spring Boot, PostgreSQL, Flyway, configs globais |
| **2** | [Autenticação & Usuários](02-autenticacao-usuarios.md) | JWT, registro, login, RBAC, gestão de contas |
| **3** | [Veículos](03-veiculos.md) | CRUD de veículos com consumo informado |
| **4** | [Postos & Geolocalização](04-postos-geolocalizacao.md) | CRUD de postos, busca por raio (Haversine) |
| **5** | [Preços de Combustíveis](05-precos-combustiveis.md) | Gestão de preços, tipos de combustível, comparação |
| **6** | [Avaliações & Moderação](06-avaliacoes-moderacao.md) | Reviews 1-5 estrelas, nota média, moderação admin |
| **7** | [Recomendações](07-recomendacoes.md) | Paridade etanol/gasolina, custo/km personalizado |
| **8** | [Integração ANP](08-integracao-anp.md) | Pipeline ETL do CSV semestral, idempotência, auditoria |
| **9** | [Frontend & Integração](09-frontend-integracao.md) | Interface web, Leaflet, Tailwind, deep links |

---

## 5. Diagrama de Dependências entre Fases

```mermaid
flowchart LR
    F1["Fase 1: Setup"] --> F2["Fase 2: Auth"]
    F2 --> F3["Fase 3: Veículos"]
    F2 --> F4["Fase 4: Postos"]
    F4 --> F5["Fase 5: Preços"]
    F4 --> F6["Fase 6: Avaliações"]
    F3 --> F7["Fase 7: Recomendações"]
    F5 --> F7
    F5 --> F8["Fase 8: ANP"]
    F1 --> F9["Fase 9: Frontend"]
    F2 --> F9
    F4 --> F9
    F5 --> F9
    F7 --> F9
```

**Leitura do diagrama:**
- Fase 1 é pré-requisito de todas as demais
- Fases 3 e 4 podem ser desenvolvidas em paralelo (ambas dependem apenas da Fase 2)
- Fases 5 e 6 podem ser desenvolvidas em paralelo (ambas dependem da Fase 4)
- Fase 7 requer Fases 3 e 5 concluídas
- Fase 8 requer Fase 5 concluída
- Fase 9 (frontend) é a integração final e depende de múltiplas fases

---

## 6. Perfis de Usuário (MVP)

| Perfil | Role | Descrição |
|--------|------|-----------|
| **Motorista** | `ROLE_MOTORISTA` | Consulta postos, compara preços, registra veículos, avalia postos, recebe recomendações |
| **Administrador** | `ROLE_ADMIN` | Gerencia postos e preços, modera avaliações, gerencia usuários, dispara cargas ANP |

> **Nota:** O perfil `ROLE_OPERADOR` (Operador de Posto) está previsto para o Pós-MVP.

---

## 7. Convenções de Desenvolvimento

### 7.1 Branches

| Tipo | Padrão | Exemplo |
|------|--------|---------|
| Principal | `main` | Código estável homologado |
| Feature | `feature/<nome>` | `feature/autenticacao-jwt` |

### 7.2 Conventional Commits

| Prefixo | Uso |
|---------|-----|
| `feat:` | Nova funcionalidade |
| `fix:` | Correção de bug |
| `docs:` | Documentação |
| `style:` | Formatação sem alteração semântica |
| `refactor:` | Refatoração sem alteração funcional |
| `test:` | Adição ou modificação de testes |
| `chore:` | Configuração, dependências, infraestrutura |

### 7.3 Idioma

- **Código-fonte:** Inglês (classes, métodos, variáveis, tabelas, colunas)
- **Documentação e UI:** Português

### 7.4 Nomenclatura

| Elemento | Convenção | Exemplo |
|----------|-----------|---------|
| Classes, Records, Enums | `PascalCase` | `StationService`, `FuelType` |
| Métodos, variáveis | `camelCase` | `calculateHaversineDistance` |
| Constantes, enum values | `UPPER_SNAKE_CASE` | `ROLE_MOTORISTA`, `GASOLINE_REGULAR` |
| Tabelas do banco | `snake_case` plural | `fuel_prices`, `anp_import_logs` |
| Colunas do banco | `snake_case` | `tank_capacity`, `user_id` |
| Rotas REST | `kebab-case` plural | `/fuel-prices/compare` |

---

## 8. Estrutura de Pacotes

```text
com.fuelfinder/
├── config/                  # Security, WebMvc, Swagger/OpenAPI, CORS
├── common/
│   ├── exception/           # GlobalExceptionHandler, exceções base
│   ├── util/                # HaversineCalculator, utilitários
│   └── dto/                 # DTOs compartilhados (paginação, ProblemDetail)
└── modules/
    ├── auth/                # Autenticação, JWT, registro/login
    ├── user/                # Gestão de usuários, perfis, status
    ├── vehicle/             # Veículos e consumo informado
    ├── station/             # Postos, localização, geolocalização
    ├── fuel/                # Catálogo de tipos de combustíveis
    ├── price/               # Preços históricos e correntes
    ├── review/              # Avaliações e moderação
    ├── recommendation/      # Comparação de preços e paridade
    └── anp/                 # Pipeline ETL da base ANP
```

### Estrutura interna de cada módulo:

```text
modules/<dominio>/
├── controller/              # @RestController
├── service/                 # Regras de negócio
├── repository/              # Spring Data JPA
├── entity/                  # @Entity JPA
├── dto/                     # Records (Request/Response)
├── mapper/                  # Entity ↔ DTO
└── exception/               # Exceções do domínio
```

---

## 9. Funcionalidades Pós-MVP (Fora do Escopo)

As seguintes funcionalidades estão documentadas mas **não fazem parte** deste plano:

- `ROLE_OPERADOR` (Operador de Posto)
- Histórico de abastecimentos e cálculo de consumo real
- OCR / Processamento de imagens de totens de preço
- PostGIS para índices espaciais avançados
- Notificações push de variação de preços
