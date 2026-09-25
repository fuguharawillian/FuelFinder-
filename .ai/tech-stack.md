# Stack Tecnológica e Bibliotecas Permitidas — FuelFinder

Este documento define expressamente as versões de runtime, frameworks, dependências aprovadas e ferramentas permitidas para o desenvolvimento da plataforma **FuelFinder**.

---

## 1. Runtime e Linguagem

| Componente | Versão Aprovada | Finalidade / Justificativa |
| :--- | :--- | :--- |
| **Java JDK** | **Java 25** (OpenJDK / Eclipse Temurin) | Runtime da aplicação com suporte a Virtual Threads de alta performance, Records modernos e Pattern Matching. |
| **Build Tool** | **Maven 3.9+** (ou Gradle 8.10+) | Gerenciamento de dependências e automação de build contínuo. |
| **Encoding** | **UTF-8** | Padrão obrigatório para todo o código-fonte e recursos. |

---

## 2. Framework Principal: Spring Boot Ecosystem

| Módulo / Starter | Versão | Descrição de Uso |
| :--- | :--- | :--- |
| **Spring Boot** | **3.4.x / 3.5.x** | Core do backend monolítico modular. |
| **spring-boot-starter-web** | Versão do BOM | Exposição das APIs REST HTTP com suporte a Tomcat embarcado. |
| **spring-boot-starter-security** | Versão do BOM | Autenticação, autorização de rotas e filtros de segurança RBAC. |
| **spring-boot-starter-data-jpa** | Versão do BOM | Mapeamento objeto-relacional com Hibernate 6.6+. |
| **spring-boot-starter-validation**| Versão do BOM | Validação declarativa de entrada via Jakarta Bean Validation. |
| **spring-boot-starter-actuator**  | Versão do BOM | Métricas, health checks (`/actuator/health`) e observabilidade. |

---

## 3. Spring AI (Mecanismo de Inteligência Artificial)

| Dependência | Versão | Finalidade |
| :--- | :--- | :--- |
| **spring-ai-bom** | **1.0.0-M6** (ou mais recente) | Alinhamento de dependências do ecossistema Spring AI. |
| **spring-ai-google-genai-spring-boot-starter** *(ou Vertex AI / OpenAI)* | Versão do BOM | Integração com Gemini API para geração de recomendações personalizadas, análise de sentimento em avaliações e comparações contextuais. |

### Configuração de IA Recomendada:
- **Modelo Base**: `gemini-1.5-flash` / `gemini-2.0-flash` para baixa latência em respostas da API.
- **Temperatura**: `0.2` a `0.4` para recomendações determinísticas e consistentes sobre custo-benefício.

---

## 4. Persistência de Dados e Suporte Geoespacial

| Tecnologia / Driver | Versão | Justificativa |
| :--- | :--- | :--- |
| **PostgreSQL** | **16+ / 17+** | Banco de dados relacional principal da aplicação. |
| **PostGIS** | **3.4+** | Extensão geoespacial para cálculos de raio (`ST_DWithin`) e ordenação esferoidal de distância. |
| **postgresql** (Driver JDBC) | Versão gerenciada | Driver oficial de conexão com o banco. |
| **hibernate-spatial** | Versão compatível com Hibernate 6.6+ | Suporte a tipos geométricos (`org.locationtech.jts.geom.Point`) em entidades JPA. |
| **jts-core** | **1.19+** | Biblioteca Java Topology Suite para manipulação de coordenadas espaciais. |
| **Flyway** (`flyway-core`, `flyway-database-postgresql`) | **10.x+** | Migrações e versionamento estruturado do esquema do banco de dados. |
| **HikariCP** | Gerenciado | Pool de conexões JDBC de alta performance padrão do Spring Boot. |

---

## 5. Segurança, Criptografia e Autenticação

| Biblioteca | Versão | Finalidade |
| :--- | :--- | :--- |
| **jjwt-api**, **jjwt-impl**, **jjwt-jackson** | **0.12.6+** | Criação, assinatura (HMAC-SHA256) e decodificação de tokens JWT. |
| **BCryptPasswordEncoder** | Nativo Spring Security | Algoritmo obrigatório para hash de senhas de usuários (fator de custo 12). |

---

## 6. Documentação da API

| Dependência | Versão | Finalidade |
| :--- | :--- | :--- |
| **springdoc-openapi-starter-webmvc-ui** | **2.7.0+** | Geração automática da especificação OpenAPI 3.0 e disponibilização do Swagger UI em `/swagger-ui.html`. |

---

## 7. Testes Automatizados e Qualidade de Código

| Ferramenta / Lib | Versão | Escopo | Finalidade |
| :--- | :--- | :--- | :--- |
| **JUnit Jupiter (JUnit 5)** | Gerenciado | `test` | Framework de testes unitários e de integração. |
| **Mockito** & **mockito-junit-jupiter** | Gerenciado | `test` | Mocks para serviços e dependências externas. |
| **AssertJ** | Gerenciado | `test` | Asserções fluentes e expressivas. |
| **Testcontainers PostgreSQL** | **1.20+** | `test` | Subida de contêiner real com PostGIS para testes de integração de repositório. |

---

## 8. Frontend e Recursos de Interface

| Recurso | Versão / Padrão | Finalidade |
| :--- | :--- | :--- |
| **HTML5 Semântico** | Standard W3C | Estruturação acessível das páginas web da aplicação. |
| **CSS3 / Tailwind CSS** | **3.4+** (ou 4.0 via CDN/CLI) | Estilização responsiva e ágil, com foco em usabilidade mobile. |
| **Vanilla JavaScript** | **ES2023+** | Manipulação do DOM, chamadas Fetch API assíncronas e integração de geolocalização. |
| **Leaflet.js** | **1.9.4+** | Biblioteca leve para renderização do mapa interativo e marcadores dos postos. |
| **OpenStreetMap Tiles** | Standard OSM | Camada gratuita de mapas sem necessidade de cartão de crédito no MVP. |

---

## 9. Diretrizes de Dependências e Proibições

### ⚠️ Práticas e Bibliotecas Proibidas:
1. **Pacotes `javax.*` obsoletos**: Usar exclusivamente a especificação moderna `jakarta.*` (Jakarta EE 10+).
2. **WebSecurityConfigurerAdapter**: Proibido estender esta classe descontinuada; utilizar a configuração baseada no bean `SecurityFilterChain`.
3. **Lógica de Banco no Código**: Não concatenar strings em consultas SQL para evitar injeção de SQL; sempre usar parâmetros nomeados (`:param`) ou Spring Data Method Queries.
4. **Dependências Não Homologadas**: Não adicionar bibliotecas externas adicionais (ex.: Apache Commons desnecessários ou bibliotecas de reflexão não auditadas) sem prévia revisão de segurança e performance.
