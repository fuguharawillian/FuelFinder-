# Stack Tecnológica — FuelFinder

Este documento detalha o conjunto de tecnologias selecionadas, sugeridas e em avaliação para o desenvolvimento da plataforma **FuelFinder**.

Em conformidade com as diretrizes fundamentais do projeto:
* **NÃO** foram inventadas versões de tecnologias além das formalmente especificadas;
* Itens sem especificação explícita estão identificados como `DECISÃO EM ABERTO` ou `NÃO DEFINIDO NA ESPECIFICAÇÃO`;
* Funcionalidades e ferramentas futuras estão segregadas do escopo do Produto Mínimo Viável (MVP).

---

## 1. Classificação Geral das Tecnologias

A matriz tecnológica divide-se em três níveis de consolidação:

1. **Definidas:** Tecnologias e ferramentas explicitamente determinadas na especificação do projeto.
2. **Sugeridas:** Tecnologias citadas na especificação ou recomendadas pela literatura técnica para complementar a solução sem gerar acoplamento prematuro.
3. **Em Aberto:** Componentes cuja definição de produto, fornecedor ou versão permanece pendente de deliberação.

---

## 2. Matriz Consolidada da Stack

| Categoria | Tecnologia | Versão | Status | Finalidade / Justificativa |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend - Biblioteca de Mapas** | Leaflet | **1.9.4** | Definida | Visualização cartográfica interativa e responsiva no navegador. |
| **Frontend - Provedor de Mapas** | OpenStreetMap | — | Definida | Camada de tiles gratuitos e abertos para exibição no Leaflet. |
| **Frontend - Estrutura Web** | HTML | NÃO DEFINIDO NA ESPECIFICAÇÃO | Definida | Base da interface web responsiva para desktop e dispositivos móveis. |
| **Backend - Framework Principal** | Spring Boot | NÃO DEFINIDO NA ESPECIFICAÇÃO | Definida | Desenvolvimento do monólito modular e exposição da API REST. |
| **Banco de Dados Relacional** | PostgreSQL | NÃO DEFINIDO NA ESPECIFICAÇÃO | Definida | Persistência relacional, garantia de transações ACID e suporte futuro a extensões geoespaciais. |
| **Autenticação & Sessão** | JWT (JSON Web Token) | — | Definida | Padrão stateless para autenticação e controle de acesso RBAC. |
| **Navegação Veicular Externa** | Google Maps / Waze | — | Definida | Destinos externos para abertura de rotas e navegação via GPS do dispositivo. |
| **Fonte Primária de Dados Públicos** | ANP - Série Histórica de Combustíveis e GLP | 1º Semestre de 2026 | Definida | Portal oficial de dados abertos para importação de postos e preços históricos. |
| **Extensão Geoespacial de Banco** | PostGIS | NÃO DEFINIDO NA ESPECIFICAÇÃO | Sugerida / Em avaliação | Extensão relacional para consultas de proximidade espaciais no PostgreSQL (Evolução futura). |
| **Reconhecimento de Imagem / OCR** | Leitura de Fotos de Totens | — | Pós-MVP / Em avaliação | Extração de preços e metadados de fotos tiradas por condutores. |
| **Geocodificação de Endereços** | Serviço de Geocodificação (ex.: Nominatim / ViaCEP) | — | Sugerida | Obtenção de coordenadas (lat/long) para endereços da base ANP e busca manual. |
| **Persistência / ORM** | Spring Data JPA / Hibernate | Compatível com Spring Boot | Sugerida | Mapeamento objeto-relacional e abstração de consultas no PostgreSQL. |
| **Validação de Dados** | Jakarta Bean Validation | Compatível com Spring Boot | Sugerida | Validação sintática e de restrições em DTOs de entrada da API. |
| **Documentação da API REST** | OpenAPI 3.0 / Swagger (Springdoc) | — | Sugerida | Geração de documentação interativa e contratos dos endpoints. |
| **Testes Automatizados** | JUnit 5, Mockito, Testcontainers | — | Sugerida | Cobertura de testes unitários, testes de integração e validação relacional em contêineres. |

---

## 3. Detalhamento por Categoria

### 3.1 Tecnologias Definidas

* **Leaflet (1.9.4):**
  * Escolha mandatória para a camada de mapas interativos do frontend.
  * Versão explicitamente fixada em `1.9.4` na especificação de requisitos.
  * Renderiza marcadores do usuário e dos postos calculados na proximidade.
* **OpenStreetMap (Tiles):**
  * Fonte mandatória de camadas cartográficas (tiles) consumidas pelo Leaflet.
  * Garante funcionamento leve, sem restrições de custos com licenças proprietárias no MVP.
* **Spring Boot:**
  * Framework backend mandatário para a criação da API REST e estruturação do monólito modular.
  * *Versão específica do framework:* `NÃO DEFINIDO NA ESPECIFICAÇÃO`.
* **PostgreSQL:**
  * Banco de dados relacional principal adotado para toda a plataforma.
  * Armazena dados de usuários, veículos, postos, combustíveis, preços e logs da ANP.
  * *Versão específica do SGBD:* `NÃO DEFINIDO NA ESPECIFICAÇÃO`.
* **HTML (Web Responsiva):**
  * Especificado como padrão visual da aplicação. Deve seguir princípios de responsividade (adaptação para dispositivos móveis e desktops).
* **JWT (JSON Web Token):**
  * Mecanismo definido para transmissão segura de credenciais e validação stateless de sessões na API REST.
* **Google Maps e Waze:**
  * Provedores externos definidos para navegação veicular por rota. O FuelFinder não computa rotas internamente, redirecionando o condutor via URL estruturada (deep linking).
* **Base de Dados ANP (Série Histórica - 1º Semestre de 2026):**
  * Origem primária oficial de postos e preços públicos. O sistema conecta-se ao portal de dados abertos para download e ingestão do arquivo semestral de referência.

### 3.2 Tecnologias Sugeridas

* **PostGIS (Extensão Geoespacial):**
  * Citada na especificação arquitetural como possibilidade de evolução futura para indexação espacial (GIST) e queries geográficas nativas.
  * No MVP, a distância é calculada em linha reta pela diferença de coordenadas, tornando a adoção imediata do PostGIS opcional ou passível de postergação.
* **Serviço de Geocodificação:**
  * Necessário para obter latitude e longitude dos postos cujos registros da ANP possuam apenas logradouro, bairro, município e CEP, e para viabilizar a busca manual quando o usuário não autorizar o GPS.
  * Exemplos sugeridos: Nominatim (OpenStreetMap) para conversão de logradouros ou ViaCEP para complemento municipal de CEPs brasileiros.
* **Spring Data JPA / Hibernate:**
  * Padrão idiomático no ecossistema Spring para acesso a dados no PostgreSQL, simplificando transações e consultas paginadas.
* **Springdoc-openapi (OpenAPI 3.0):**
  * Geração automática de documentação e console interativo para consumo da API REST.
* **Testcontainers:**
  * Execução de testes de integração com instância real e descartável do PostgreSQL via Docker.

### 3.3 Tecnologias Pós-MVP / Em Avaliação

* **Leitura de Preços por Fotografia (OCR):**
  * Funcionalidade citada como "possível funcionalidade pós-MVP" onde o usuário envia foto do totem do posto e o sistema avalia a imagem, extrai localização/horário dos metadados e atualiza preços divergentes.
  * Requer ferramentas futuras de visão computacional, OCR e validação de metadados EXIF.

---

## 4. Decisões Tecnológicas em Aberto

As seguintes definições técnicas não constam na especificação e constituem decisões em aberto:

```text
DECISÃO EM ABERTO
1. Versão da Linguagem Java:
   - Alternativas a avaliar: Java 17 LTS ou Java 21 LTS.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

2. Versão do Framework Spring Boot:
   - Alternativas a avaliar: Spring Boot 3.2.x, 3.3.x ou versão superior estável.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

3. Versão do Banco de Dados PostgreSQL:
   - Alternativas a avaliar: PostgreSQL 15 ou 16.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

4. Framework / Biblioteca CSS para o Frontend:
   - Alternativas a avaliar: Tailwind CSS, Bootstrap 5 ou CSS Moderno puro.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

5. Biblioteca / Abordagem JavaScript no Frontend:
   - Alternativas a avaliar: JavaScript Vanilla puro, Vue.js, React ou renderização de templates no servidor (Thymeleaf).
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

6. Biblioteca de Manipulação de Tokens JWT no Backend:
   - Alternativas a avaliar: jjwt (Java JWT), java-jwt (Auth0) ou Spring Security OAuth2 Resource Server.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

7. Provedor de Geocodificação de Endereços:
   - Alternativas a avaliar: Nominatim (OSM), Google Geocoding API, OpenCage ou biblioteca de base local de CEPs.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

8. Ferramenta de Migração de Banco de Dados:
   - Alternativas a avaliar: Flyway ou Liquibase.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.

9. Mecanismo de Execução de Tarefas Agendadas (Importação ANP):
   - Alternativas a avaliar: Spring @Scheduled, Spring Batch ou Cron de sistema operacional.
   - Status: NÃO DEFINIDO NA ESPECIFICAÇÃO.
```

---

## 5. Diretrizes de Qualidade e Referências Técnicas

A seleção e o uso das tecnologias devem obrigatoriamente respeitar as diretrizes normativas estabelecidas para o projeto:

* **OWASP:** Uso de bibliotecas atualizadas sem vulnerabilidades conhecidas (CVEs), saneamento de parâmetros contra SQL Injection e proteção de endpoints REST.
* **W3C / WCAG:** Código HTML semântico e compatibilidade com leitores de tela e múltiplos navegadores.
* **TC39:** Utilização de recursos modernos de ECMAScript com ampla compatibilidade e modularidade no frontend.
* **IETF:** Protocolos HTTPS, HTTP/2 e comunicação RESTful padronizada com JSON.
* **ISO/IEC 25010 & ISO/IEC 27001:** Padrões de manutenibilidade, usabilidade, eficiência e segurança criptográfica.
* **TPC:** Otimização de queries SQL e planos de indexação no PostgreSQL para garantir respostas rápidas nas consultas de postos e na carga em lote dos arquivos semestrais da ANP.
