# Stack Tecnológica — FuelFinder

Este documento detalha as tecnologias **planejadas e definidas** para a plataforma **FuelFinder**.

> **Diretriz de Rigor:** Somente tecnologias formalmente definidas ou autorizadas nas especificações do projeto constam neste documento. Nenhuma versão não especificada foi inferida ou inventada.

---

## 1. Tabela da Stack Tecnológica

| Categoria | Tecnologia | Versão | Status | Observações |
| :--- | :--- | :--- | :--- | :--- |
| **Backend** | Spring Boot | Versão ainda não definida. | Planejada | Framework para desenvolvimento da API REST e monólito modular. |
| **Banco de Dados** | PostgreSQL | Versão ainda não definida. | Planejada | Banco de dados relacional principal para persistência e transações ACID. |
| **Frontend** | HTML | Versão ainda não definida. | Planejada | Estrutura base da interface web responsiva para desktop e dispositivos móveis. |
| **Visualização de Mapa** | Leaflet | 1.9.4 | Definida | Biblioteca JavaScript para renderização interativa do mapa no frontend. |
| **Provedor de Mapas** | OpenStreetMap | — | Definida | Provedor aberto de tiles de mapas para renderização através do Leaflet. |
| **Autenticação** | JWT (JSON Web Token) | — | Planejada | Padrão stateless para autenticação e autorização na API REST. |
| **Navegação Externa** | Google Maps / Waze | — | Definida | Destino para redirecionamento externo de rotas através de deep links / URLs. |
| **Banco / Geoespacial** | PostGIS | Versão ainda não definida. | Em avaliação | Extensão relacional para consultas geoespaciais avançadas (evolução futura). |
| **Processamento de Imagem** | Leitura de Fotos / OCR | — | Funcionalidade futura / Em avaliação | Leitura e reconhecimento automático de preços a partir de fotografias. |

---

## 2. Classificação das Tecnologias por Status

### 2.1 Tecnologias Definidas
Tecnologias cuja escolha e versão (ou provedor) estão formalmente aprovadas:
* **Leaflet (1.9.4):** Ferramenta mandatória para o cliente de mapas interativos.
* **OpenStreetMap:** Base cartográfica para exibição dos mapas sem custos no MVP.
* **Google Maps e Waze:** Serviços externos mandatários para recepção de rotas redirecionadas pelo sistema.

### 2.2 Tecnologias Planejadas
Tecnologias cuja categoria e produto estão aprovados, mas cuja versão específica ou especificações técnicas detalhadas de implementação estão pendentes:
* **Spring Boot:** Escolhido como backend da aplicação; versão exata a ser fixada no setup do projeto (`Versão ainda não definida.`).
* **PostgreSQL:** Escolhido como banco de dados principal; versão exata a ser fixada no setup do ambiente (`Versão ainda não definida.`).
* **HTML (Web Responsiva):** Escolhido como padrão da camada de visão; frameworks auxiliares ou pré-processadores não foram estipulados.
* **JWT:** Mecanismo adotado para a camada de segurança; biblioteca de manipulação a ser definida.

### 2.3 Tecnologias em Avaliação e Funcionalidades Futuras
Tecnologias que não fazem parte do escopo mandatório do MVP e dependem de avaliação técnica posterior:
* **PostGIS:** Avaliação sobre a real necessidade de índice espacial no banco frente ao cálculo de distância em linha reta na aplicação.
* **Leitura de Preços por Fotografia (OCR):** Funcionalidade pós-MVP destinada ao reconhecimento de valores em fotos de totens de postos de combustível.

---

## 3. Decisões Tecnológicas em Aberto

As seguintes decisões técnicas não possuem definições preliminares nas especificações:

```text
DECISÃO EM ABERTO
- Versão do Java (ex.: Java 17 LTS ou Java 21 LTS).
- Versão do Spring Boot (ex.: 3.x).
- Versão do PostgreSQL (ex.: 15 ou 16).
- Definição de bibliotecas/frameworks CSS complementares para responsividade (ex.: CSS puro, Bootstrap, Tailwind).
- Framework/biblioteca JavaScript reativa para o frontend (ex.: JavaScript Vanilla puro, Vue, React, ou renderização de templates no servidor via Thymeleaf).
- Ferramenta/SDK para geração e validação de tokens JWT (ex.: jjwt, java-jwt).
```
