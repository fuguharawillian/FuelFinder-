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
| **Linguagem / Runtime** | **Java (JDK)** | **21 LTS** | Homologada | Recursos modernos da linguagem (Records, Pattern Matching, Virtual Threads), suporte LTS estável e compatibilidade plena com o ecossistema Spring. |
| **Framework Backend** | **Spring Boot** | **3.4.x** | Homologada | Núcleo do monólito modular, suporte nativo a REST, injeção de dependência e alta produtividade. |
| **Build & Dependências** | **Maven** | **3.9+** | Homologada | Gerenciamento padronizado de ciclo de vida, dependências e plugins. |
| **Banco de Dados Relacional**| **PostgreSQL** | **16+** | Definida | SGBD relacional para consistência ACID, alta performance em cargas volumosas (ANP) e suporte a funções matemáticas de distância. |
| **Migração de Banco** | **Flyway** | **10.x+** | Homologada | Versionamento declarativo e reprodutível do esquema de banco de dados (`V1__initial_schema.sql`). |
| **Biblioteca de Mapas** | **Leaflet** | **1.9.4** | Definida | Visualização cartográfica interativa, leve, gratuita e responsiva no navegador. |
| **Provedor de Mapas** | **OpenStreetMap** | — | Definida | Camada de tiles gratuita e aberta consumida diretamente pelo Leaflet sem custos de licença no MVP. |
| **Estrutura Frontend** | **HTML5 Semântico + CSS3** | Padrão W3C | Definida | Interface web responsiva para dispositivos móveis e desktop, com conformidade WCAG/W3C. |
| **Estilização frontend** | **Bootstrap 5 via CDN + CSS próprio** | **5.2.3** | Migração visual em andamento | Substitui Tailwind em todas as telas; adaptação da identidade visual LUNO conforme [`../Plano/09-frontend-integracao.md`](../Plano/09-frontend-integracao.md). Não carregar os dois frameworks simultaneamente. |
| **Scripts Frontend** | **Vanilla JavaScript** | **ES2023+** | Homologada | Modularidade nativa, consumo da API Fetch, manipulação do Leaflet e captura da Geolocation API. |
| **Autenticação & Token** | **JWT (JJWT)** | **0.12.6+** | Homologada | Access JWT de curta duração com assinatura HMAC-SHA256 e sessões server-side para rotação e revogação de refresh tokens. |
| **Segurança Backend** | **Spring Security** | **6.4+** | Homologada | Proteção de rotas com RBAC (`ROLE_DRIVER` e `ROLE_ADMIN`), filtros de segurança e hash BCrypt com custo mínimo 12; senha exige maiúscula, minúscula, número e caractere especial. |
| **Persistência / ORM** | **Spring Data JPA / Hibernate**| **6.6+** | Homologada | Mapeamento objeto-relacional, repositórios com queries derivadas e projeções DTO de alta performance. |
| **Validação de Entrada** | **Jakarta Bean Validation** | **3.0+** | Homologada | Validação declarativa de invariantes de negócio nos DTOs de entrada (`@NotNull`, `@Size`, `@Positive`). |
| **Documentação da API** | **Springdoc OpenAPI (Swagger)** | **2.7.x** | Homologada | Documentação viva interativa acessível via `/swagger-ui.html` para testes e alinhamento de contratos REST. |
| **Navegação Veicular** | **Google Maps / Waze** | Deep Links | Definida | Redirecionamento externo via botão "Rotas" com coordenadas geográficas do posto selecionado. |
| **Fonte Primária de Dados** | **ANP - Dados Abertos** | Séries semestrais | Definida | Arquivo histórico oficial de postos e preços revendedores, disponível como CSV/TSV ou ZIP contendo CSV; o período será informado por ano e semestre em campos separados. |
| **IA / Recomendações** | **Spring AI (adaptador opcional)** | Provedor/modelo pendentes | Pendente de aprovação | Integração desacoplada e configurável para explicações complementares. Não fixa fornecedor ou credencial; inicialização e fluxos essenciais funcionam sem IA configurada. |
| **Geocodificação** | **Geoapify Geocoding API** | Plano gratuito | Opcional | Para testar chamadas reais, ler `GEOAPIFY_API_KEY` exclusivamente do ambiente e chamar somente pelo backend; sem a variável, a aplicação inicia e os demais fluxos permanecem disponíveis. |
| **Testes Automatizados** | **JUnit 5, Mockito, AssertJ** | Versões Spring BOM | Homologada | Testes unitários de serviços e integração de controladores. |
| **OCR / Fotos de Totem** | **Processador de Imagens** | — | Pós-MVP / Em Avaliação | Extração de preços e validação de metadados em fotos enviadas por condutores (conceito de aula SOC). |
| **Extensão Espacial** | **PostGIS** | — | Pós-MVP / Em Avaliação | Avaliação posterior caso haja necessidade de índices espaciais GIST complexos além da fórmula de Haversine. |

---

## 3. Detalhamento e Justificativas das Decisões Homologadas

### 3.1 Backend: Java 21 LTS e Spring Boot 3.4
- **Justificativa:** Java 21 é uma versão LTS de longo prazo que introduz **Records** (fundamentais para DTOs imutáveis), **Pattern Matching** e suporte maduro a **Virtual Threads** no Spring Boot 3.4. Permite uma arquitetura moderna sem risco de incompatibilidades em bibliotecas de terceiros.

### 3.2 Frontend: HTML5 + Vanilla JS + Leaflet 1.9.4
- **Base funcional preservada:** HTML semântico, Vanilla JS em módulos ES, Leaflet 1.9.4 para o mapa e integrações existentes com a API.
- **Referência visual:** O template [`../Layout/`](../Layout/README.md) declara Bootstrap `^5.2.0`, usa SCSS próprio, Gulp e um conjunto amplo de plugins. Não há lockfile no diretório da referência para determinar a versão instalada exata. A análise e os mapeamentos de componentes estão em [`../Plano/09-frontend-integracao.md`](../Plano/09-frontend-integracao.md).
- **Decisão de migração:** aprovada pelo usuário; Bootstrap 5.2.3 via CDN substitui Tailwind. O CSS próprio implementa tokens LUNO, sem importar os bundles de plugins, jQuery ou os assets demonstrativos do template.
- **Estado da migração:** páginas de busca/mapa, login/cadastro, veículos, recomendações, detalhe do posto e administração estão adaptadas ao grid/componentes Bootstrap; revisão visual mais ampla e validação funcional ainda estão pendentes.
- **Limites:** não copiar bundles de plugins, jQuery, conteúdo de demonstração, fontes ou imagens antes de avaliar necessidade, acessibilidade e licença. Toda interface derivada deve estar em português do Brasil (pt-BR); detalhes responsivos devem seguir a Fase 9.

### 3.3 Banco de Dados: PostgreSQL 16 com Flyway
- **Justificativa:** PostgreSQL oferece suporte nativo robusto a cálculos trigonométricos (`acos`, `cos`, `sin`, `radians`) para o cálculo de distância em linha reta via Haversine sem exigir a instalação obrigatória da extensão PostGIS no primeiro dia de aula. O Flyway garante que o esquema de tabelas e as cargas de teste sejam versionados no Git.

### 3.4 Segurança: Spring Security + JJWT (Java JWT)
- **Justificativa:** A biblioteca `io.jsonwebtoken:jjwt-api:0.12.6` é utilizada para emissão, validação e extração de claims dos access tokens JWT. O estado das sessões e os hashes dos refresh tokens são persistidos no servidor para permitir rotação e revogação imediata.

### 3.5 Integração Opcional de IA: Spring AI
- **Decisão pendente:** provedor e modelo ainda precisam de aprovação. Não fixar uma implementação ou credencial antes dessa decisão.
- **Arquitetura:** isolar a integração atrás de um adaptador configurável. O cálculo de recomendação e explicações determinísticas por template funcionam sem Spring AI, sem credenciais e sem dependência da integração para inicializar o backend.
- **Segredos:** credenciais de IA, quando aprovadas, devem vir de variável de ambiente ou gestor de segredos externo; nunca do código, Git ou frontend.

---

## 4. Proibições e Diretrizes de Qualidade Técnica

* **OWASP Top 10:** Proibida concatenação de strings em consultas SQL (obrigatório uso de parâmetros com JPA); proteção contra ataques de injeção e validação estrita de dados recebidos da ANP.
* **W3C / WCAG:** Código HTML semântico com tags `<main>`, `<section>`, `<article>`, atributos `aria-label` nos botões de rotas e contraste visual acessível.
* **IETF:** Comunicação via HTTPS; conformidade com os verbos HTTP (utilizando `PATCH` para atualizações parciais, corrigindo a divergência do documento base).
* **Idempotência na Carga ANP:** A rotina de importação deve utilizar chaves de deduplicação (CNPJ + Produto + Data da Coleta) para evitar registros duplicados.
