# Stack Tecnológica — FuelFinder

Este documento detalha o conjunto oficial de tecnologias, frameworks, bibliotecas homologadas e decisões de infraestrutura para o desenvolvimento da plataforma **FuelFinder**. 

O objetivo desta revisão é consolidar as escolhas técnicas para fornecer uma **base pronta para implementação na próxima aula**, sanando indefinições e respeitando rigorosamente a especificação do projeto.

---

## 1. Classificação Geral das Tecnologias

A matriz tecnológica está estruturada em três níveis:

1. **Definidas na Especificação:** Tecnologias e bibliotecas explicitamente determinadas na especificação e diretrizes da aula.
2. **Homologadas para Implementação (Base da Próxima Aula):** Escolhas de engenharia padronizadas para sanar as decisões técnicas em aberto (versões de Java, Spring Boot, PostgreSQL, libs de JWT e migração), permitindo iniciar o desenvolvimento sem bloqueios.
3. **Pós-MVP / Em Avaliação:** Recursos e componentes reservados para expansões futuras (ex.: PostGIS avançado, OCR com IA para fotos de totens de combustível).

---

## 2. Matriz Consolidada da Stack Tecnológica

| Categoria | Tecnologia | Versão Homologada | Status | Finalidade / Justificativa |
| :--- | :--- | :--- | :--- | :--- |
| **Linguagem / Runtime** | **Java (JDK)** | **21 LTS** *(ou 25)* | Homologada | Recursos modernos da linguagem (Records, Pattern Matching, Virtual Threads), suporte LTS estável e compatibilidade plena com o ecossistema Spring. |
| **Framework Backend** | **Spring Boot** | **3.4.x** | Homologada | Núcleo do monólito modular, suporte nativo a REST, injeção de dependência e alta produtividade. |
| **Build & Dependências** | **Maven** | **3.9+** | Homologada | Gerenciamento padronizado de ciclo de vida, dependências e plugins. |
| **Banco de Dados Relacional**| **PostgreSQL** | **16+** | Definida | SGBD relacional para consistência ACID, alta performance em cargas volumosas (ANP) e suporte a funções matemáticas de distância. |
| **Migração de Banco** | **Flyway** | **10.x+** | Homologada | Versionamento declarativo e reprodutível do esquema de banco de dados (`V1__initial_schema.sql`). |
| **Biblioteca de Mapas** | **Leaflet** | **1.9.4** | Definida | Visualização cartográfica interativa, leve, gratuita e responsiva no navegador. |
| **Provedor de Mapas** | **OpenStreetMap** | — | Definida | Camada de tiles gratuita e aberta consumida diretamente pelo Leaflet sem custos de licença no MVP. |
| **Estrutura Frontend** | **HTML5 Semântico + CSS3** | Padrão W3C | Definida | Interface web responsiva para dispositivos móveis e desktop, com conformidade WCAG/W3C. |
| **Estilização de UI** | **Tailwind CSS** | **3.4+** | Homologada | Estilização utilitária de alta velocidade, mobile-first e design limpo sem overhead de build pesado. |
| **Scripts Frontend** | **Vanilla JavaScript** | **ES2023+** | Homologada | Modularidade nativa, consumo da API Fetch, manipulação do Leaflet e captura da Geolocation API. |
| **Autenticação & Token** | **JWT (JJWT)** | **0.12.6+** | Homologada | Padrão stateless para controle de sessão, com assinatura criptográfica HMAC-SHA256. |
| **Segurança Backend** | **Spring Security** | **6.4+** | Homologada | Proteção de rotas com RBAC (`ROLE_MOTORISTA` e `ROLE_ADMIN`), filtros de segurança e hash de senhas (BCrypt). |
| **Persistência / ORM** | **Spring Data JPA / Hibernate**| **6.6+** | Homologada | Mapeamento objeto-relacional, repositórios com queries derivadas e projeções DTO de alta performance. |
| **Validação de Entrada** | **Jakarta Bean Validation** | **3.0+** | Homologada | Validação declarativa de invariantes de negócio nos DTOs de entrada (`@NotNull`, `@Size`, `@Positive`). |
| **Documentação da API** | **Springdoc OpenAPI (Swagger)** | **2.7.x** | Homologada | Documentação viva interativa acessível via `/swagger-ui.html` para testes e alinhamento de contratos REST. |
| **Navegação Veicular** | **Google Maps / Waze** | Deep Links | Definida | Redirecionamento externo via botão "Rotas" com coordenadas geográficas do posto selecionado. |
| **Fonte Primária de Dados** | **ANP - Dados Abertos** | **1º Semestre de 2026** | Definida | Série Histórica semestral oficial de postos e preços revendedores em formato CSV/TSV. |
| **IA / Recomendações** | **Spring AI** | **1.0.0-M6+** | Homologada | Integração para geração de explicações em linguagem natural, pareceres de custo-benefício e análise inteligente. |
| **Testes Automatizados** | **JUnit 5, Mockito, AssertJ** | Versões Spring BOM | Homologada | Testes unitários de serviços e integração de controladores. |
| **OCR / Fotos de Totem** | **Processador de Imagens** | — | Pós-MVP / Em Avaliação | Extração de preços e validação de metadados em fotos enviadas por condutores (conceito de aula SOC). |
| **Extensão Espacial** | **PostGIS** | — | Pós-MVP / Em Avaliação | Avaliação posterior caso haja necessidade de índices espaciais GIST complexos além da fórmula de Haversine. |

---

## 3. Detalhamento e Justificativas das Decisões Homologadas

### 3.1 Backend: Java 21 LTS e Spring Boot 3.4
- **Justificativa:** Java 21 é uma versão LTS de longo prazo que introduz **Records** (fundamentais para DTOs imutáveis), **Pattern Matching** e suporte maduro a **Virtual Threads** no Spring Boot 3.4. Permite uma arquitetura moderna sem risco de incompatibilidades em bibliotecas de terceiros.

### 3.2 Frontend: HTML5 + Tailwind CSS + Vanilla JS + Leaflet 1.9.4
- **Justificativa:** Atende perfeitamente ao requisito da especificação de aplicação web responsiva leve. Elimina a complexidade de configuração de SPAs pesadas (React/Angular) para a aula inicial, permitindo que a equipe foque nos contratos da API, na renderização do mapa com Leaflet 1.9.4 e no redirecionamento para Waze/Google Maps.

### 3.3 Banco de Dados: PostgreSQL 16 com Flyway
- **Justificativa:** PostgreSQL oferece suporte nativo robusto a cálculos trigonométricos (`acos`, `cos`, `sin`, `radians`) para o cálculo de distância em linha reta via Haversine sem exigir a instalação obrigatória da extensão PostGIS no primeiro dia de aula. O Flyway garante que o esquema de tabelas e as cargas de teste sejam versionados no Git.

### 3.4 Segurança: Spring Security + JJWT (Java JWT)
- **Justificativa:** A biblioteca `io.jsonwebtoken:jjwt-api:0.12.6` é o padrão da indústria para emissão, validação e extração de claims de tokens JWT em arquiteturas stateless com Spring Boot.

### 3.5 Integração de IA: Spring AI
- **Justificativa:** Permite plugar modelos como Gemini de forma padronizada via `ChatClient` para gerar explicações personalizadas no endpoint `/recommendations/fuel`, recomendando a melhor opção de combustível e posto com base no consumo do condutor.

---

## 4. Proibições e Diretrizes de Qualidade Técnica

* **OWASP Top 10:** Proibida concatenação de strings em consultas SQL (obrigatório uso de parâmetros com JPA); proteção contra ataques de injeção e validação estrita de dados recebidos da ANP.
* **W3C / WCAG:** Código HTML semântico com tags `<main>`, `<section>`, `<article>`, atributos `aria-label` nos botões de rotas e contraste visual acessível.
* **IETF:** Comunicação via HTTPS; conformidade com os verbos HTTP (utilizando `PATCH` para atualizações parciais, corrigindo a divergência do documento base).
* **Idempotência na Carga ANP:** A rotina de importação deve utilizar chaves de deduplicação (CNPJ + Produto + Data da Coleta) para evitar registros duplicados.
