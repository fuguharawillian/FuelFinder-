# Padrões e Convenções de Desenvolvimento — FuelFinder

Este documento define os padrões, convenções de código, diretrizes arquiteturais, normas de segurança e práticas de qualidade que devem ser rigorosamente seguidos durante a implementação do projeto **FuelFinder**.

Como o projeto está em **fase de concepção e planejamento**, o conteúdo deste documento expressa **convenções normativas planejadas** para orientar futuras tarefas de desenvolvimento e automação por IA.

---

## 1. Princípios Gerais e Conformidade Técnica

O desenvolvimento do FuelFinder deve aderir a boas práticas reconhecidas de engenharia de software e padrões de conformidade técnica:

* **OWASP (Open Worldwide Application Security Project):** Desenvolvimento seguro com foco no OWASP Top 10 — sanitização de inputs, parametrização contra SQL Injection, prevenção de Cross-Site Scripting (XSS), autenticação forte, autorização RBAC rigorosa em todas as rotas e proteção de dados sensíveis.
* **W3C / WCAG (Web Content Accessibility Guidelines):** Estruturação semântica de páginas web, garantia de contraste, responsividade para múltiplos tamanhos de tela e acessibilidade para pessoas com deficiência.
* **TC39 (ECMAScript):** Adoção de padrões modernos, seguros e padronizados de JavaScript no ambiente web do cliente.
* **IETF:** Comunicação estritamente segura via HTTPS (TLS 1.2/1.3), aderência às especificações HTTP/1.1 e HTTP/2, uso correto dos cabeçalhos de segurança (HSTS, CSP, X-Content-Type-Options) e semântica RESTful.
* **ISO/IEC 25010 & ISO/IEC 27001:** Qualidade de atributos de software (usabilidade, manutenibilidade, confiabilidade, desempenho) e governança da segurança da informação.
* **TPC (Transaction Processing Performance Council):** Boas práticas de eficiência de banco de dados para suportar cargas volumosas provenientes das importações de dados da ANP e consultas geoespaciais com baixa latência.

---

## 2. Organização do Projeto e Arquitetura Modular

O backend é concebido como um **Monólito Modular** baseado em **Spring Boot**, organizando os domínios de negócio com alto acoplamento interno e baixo acoplamento intermodular.

### 2.1 Estrutura de Pacotes

A aplicação deve adotar a seguinte hierarquia de pacotes:

```text
com.fuelfinder/
├── config/                  # Configurações globais (Security, WebMvc, Swagger/OpenAPI, CORS)
├── common/                  # Componentes transversais reutilizáveis
│   ├── exception/           # Classes base de exceções e tratamento global (@RestControllerAdvice)
│   ├── util/                # Utilitários gerais (geometria, cálculos matemáticos, datas)
│   └── dto/                 # DTOs compartilhados (ex.: paginação, respostas padrão de erro)
└── modules/                 # Módulos de domínio de negócio isolados
    ├── auth/                # Autenticação, emissão e validação de tokens JWT
    ├── user/                # Gestão de usuários, perfis (RBAC) e status de contas
    ├── vehicle/             # Gestão de veículos e consumo informado
    ├── station/             # Postos de combustível, localização e dados cadastrais
    ├── fuel/                # Catálogo de tipos de combustíveis
    ├── price/               # Preços históricos e correntes por posto
    ├── review/              # Avaliações numéricas, comentários e moderação
    ├── recommendation/      # Comparação de preços, custo por km e recomendações
    ├── anp/                 # Ingestão, download, validação e carga dos arquivos da ANP
    └── admin/               # Operações administrativas e moderação centralizada
```

### 2.2 Estrutura Interna de Cada Módulo

Cada módulo dentro de `modules/<dominio>` deve manter a separação em camadas:

```text
modules/<dominio>/
├── controller/              # Controladores REST (@RestController)
├── service/                 # Regras de negócio, interfaces e implementações de serviço
├── repository/              # Interfaces Spring Data JPA para acesso a dados
├── entity/                  # Entidades JPA (@Entity) persistidas no PostgreSQL
├── dto/                     # Contratos de transferência de dados
│   ├── request/             # DTOs de entrada validados com Bean Validation
│   └── response/            # DTOs de saída expostos aos clientes HTTP
├── mapper/                  # Conversores entre Entity e DTO (MapStruct ou mappers manuais)
└── exception/               # Exceções específicas do domínio
```

### 2.3 Regras de Dependência e Comunicação Intermodular

1. **Repositórios Privados:** Um módulo NUNCA deve injetar ou acessar diretamente o `Repository` de outro módulo. O acesso a dados de outro domínio deve ocorrer exclusivamente por meio de interfaces públicas de `Service`.
2. **Isolamento de Entidades:** Entidades JPA de um módulo não devem ser expostas diretamente em contratos públicos ou controllers de outros módulos; prefira a troca de DTOs ou identificadores.
3. **Comunicação Síncrona Inicial:** A comunicação entre módulos no monólito modular é síncrona, via injeção de dependência Spring (`@Autowired` via construtor).

---

## 3. Convenções de Nomenclatura

* **Idioma do Código:** Todo o código-fonte (nomes de classes, interfaces, métodos, variáveis, atributos, enums, endpoints, comentários técnicos e testes) deve ser escrito em **Inglês**. A documentação de negócio e mensagens de usuário permanecem em Português.
* **Classes e Interfaces:** `PascalCase` (ex.: `StationService`, `FuelPriceRepository`, `VehicleResponseDTO`).
* **Métodos e Variáveis:** `camelCase` (ex.: `calculateCostPerKm`, `findNearbyStations`, `averageConsumption`).
* **Constantes e Enums:** `UPPER_SNAKE_CASE` (ex.: `ROLE_MOTORISTA`, `ROLE_ADMIN`, `REGULAR_GASOLINE`, `IMPORT_STATUS_SUCCESS`).
* **Tabelas do Banco de Dados:** `snake_case` no plural (ex.: `users`, `vehicles`, `stations`, `fuel_prices`, `reviews`, `anp_import_logs`).
* **Colunas do Banco de Dados:** `snake_case` (ex.: `station_id`, `created_at`, `sale_value`, `fuel_type`).
* **Rotas da API REST:** `kebab-case` no plural (ex.: `/fuel-prices/compare`, `/recommendations/fuel`).

### 3.1 Sufixos e Padrões de Artefatos

* **Controllers:** Sufixo `Controller` (ex.: `StationController`).
* **Services:** Sufixo `Service` (ex.: `VehicleService`, `AnpIntegrationService`).
* **Repositories:** Sufixo `Repository` (ex.: `FuelPriceRepository`).
* **Entities:** Nome substantivo singular sem sufixo (ex.: `Station`, `Vehicle`, `FuelPrice`).
* **DTOs:** Sufixos `RequestDTO` e `ResponseDTO` (ex.: `CreateVehicleRequestDTO`, `StationDetailsResponseDTO`).
* **Exceptions:** Sufixo `Exception` (ex.: `ResourceNotFoundException`, `BusinessRuleException`).

---

## 4. Padrões de Camadas Arquiteturais

### 4.1 Camada Controller

* Responsável exclusivamente pelo protocolo HTTP: mapeamento de rotas, serialização/deserialização JSON, acionamento de validações e delegação para a camada Service.
* **Proibições:**
  * Não conter regras de cálculo, fórmulas matemáticas ou regras de negócio.
  * Não acessar repositórios diretamente.
  * Não manipular entidades de banco de dados diretamente; consumir e retornar estritamente DTOs.
* **Anotações Mandatórias:** `@RestController`, `@RequestMapping`, anotações semânticas de rota (`@GetMapping`, `@PostMapping`, etc.), `@Valid` nos corpos de requisição e anotações de segurança RBAC (`@PreAuthorize`).

### 4.2 Camada Service

* Concentra toda a lógica de negócio, orquestração de casos de uso, validações de invariantes e cálculos (fórmula de consumo, autonomia, custo por km, comparações de combustível).
* Gerencia demarcação de transações com `@Transactional(readOnly = true)` no nível de classe e `@Transactional` explícito em métodos de escrita.
* Lança exceções de domínio tipadas quando invariantes forem violadas.

### 4.3 Camada Repository

* Extensões de `JpaRepository<Entity, ID>` do Spring Data JPA.
* Consultas customizadas devem utilizar JPQL ou queries nativas indexadas quando a performance for mandatória.
* Devem retornar `Optional<Entity>` em consultas por chave ou atributos únicos.

### 4.4 Camada Entity

* Mapeamento explícito de tabelas e colunas com anotações JPA (`@Table`, `@Column`, `@Id`, `@GeneratedValue`).
* Toda entidade deve conter campos de auditoria: `createdAt` (`TIMESTAMP WITH TIME ZONE`) e `updatedAt`.
* Relacionamentos Lazy por padrão (`FetchType.LAZY`) para evitar queries N+1.

### 4.5 Camada DTO

* Devem ser imutáveis (utilizar Java `record` quando aplicável).
* Anotados com Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Positive`, `@Email`).
* Isolam totalmente o esquema de banco de dados da representação externa da API REST.

### 4.6 Camada de Segurança e RBAC

* Centralizada no Spring Security.
* Autenticação Stateless via filtro JWT (`OncePerRequestFilter`) validando o header `Authorization: Bearer <token>`.
* Autorização baseada em papéis com `@PreAuthorize("hasRole('ADMIN')")` ou `@PreAuthorize("hasRole('MOTORISTA')")`.
* Senhas criptografadas obrigatoriamente com algoritmo seguro (BCrypt com fator de custo adequado ou Argon2).

### 4.7 Camada de Tratamento de Exceções

* Centralizada através de `@RestControllerAdvice`.
* Respostas de erro padronizadas inspiradas na RFC 7807 (Problem Details for HTTP APIs):
  ```json
  {
    "timestamp": "2026-09-24T22:00:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Dados de entrada inválidos",
    "path": "/vehicles",
    "fields": [
      {
        "field": "tankCapacity",
        "message": "A capacidade do tanque deve ser um valor estritamente positivo"
      }
    ]
  }
  ```

---

## 5. Padrões de API REST

### 5.1 Verbos HTTP e Semântica

* `GET`: Consultas idempotentes e seguras (sem efeito colateral no servidor).
* `POST`: Criação de recursos ou operações de processamento (ex.: autenticação, importação de arquivo). Retorna HTTP 201 com header `Location` ou objeto criado.
* `PUT`: Substituição integral de um recurso existente.
* `PATCH`: Atualização parcial de campos específicos de um recurso.
* `DELETE`: Remoção ou inativação lógica de recurso. Retorna HTTP 204 No Content.

> [!IMPORTANT]
> **Inconsistência Observada na Especificação Original:**
> A especificação fornecida utilizou repetidamente a palavra `PATH` em vez de `PATCH` nas tabelas de rotas para:
> * `PATH /users/me`
> * `PATH /vehicles/{id}`
> * `PATH /stations/{id}`
> * `PATH /stations/{id}/fuel-prices/{priceId}`
> * `PATH /reviews/{id}`
> Como a palavra `PATH` não existe no protocolo HTTP (RFC 9110 / RFC 5789), o padrão normativo de implementação estabelece o uso do método `PATCH` para atualizações parciais. Essa divergência está explicitamente registrada neste documento.

### 5.2 Códigos de Retorno HTTP Padronizados

* `200 OK`: Consulta ou atualização executada com sucesso.
* `201 Created`: Novo recurso criado com sucesso.
* `204 No Content`: Operação concluída com sucesso sem corpo de resposta (ex.: deleção).
* `400 Bad Request`: Payload inválido, falha de validação de campos sintáticos ou lógicos.
* `401 Unauthorized`: Ausência de token JWT, token expirado ou credenciais inválidas.
* `403 Forbidden`: Usuário autenticado não possui o papel (Role) necessário para o recurso.
* `404 Not Found`: Recurso não localizado para o ID especificado.
* `409 Conflict`: Conflito de integridade (ex.: e-mail já cadastrado, violação de chave única).
* `422 Unprocessable Entity`: Erro semântico de regra de negócio em dados sintaticamente válidos.
* `500 Internal Server Error`: Erro inesperado não tratado no servidor.

### 5.3 Paginação e Ordenação

* Endpoints que retornam listagens (postos, preços, avaliações) devem suportar paginação:
  * `page`: Índice da página (0-indexed, padrão: 0).
  * `size`: Quantidade de registros por página (padrão: 20, máximo: 100).
  * `sort`: Campo de ordenação e direção (ex.: `sort=saleValue,asc` ou `sort=distance,asc`).

---

## 6. Padrões de Testes de Software

O projeto deve seguir a estratégia da pirâmide de testes:

1. **Testes Unitários:**
   * Foco em Services, cálculos matemáticos (fórmula de consumo, estimativa de autonomia, custo por km, cálculo de distância em linha reta) e regras de validação.
   * Frameworks: JUnit 5, AssertJ e Mockito para isolamento de dependências.
   * Cobertura esperada: alta densidade nas classes de domínio e serviços.
2. **Testes de Integração:**
   * Foco em Repositories (queries nativas/JPQL), validações de integridade relacional e Controllers (com `MockMvc` ou `WebTestClient`).
   * Utilização de banco de dados real em contêineres de teste (Testcontainers com imagem oficial do PostgreSQL).
3. **Testes de Desempenho e Carga (Diretriz TPC):**
   * Avaliação do processo de parsing e inserção em lote (batch insert) do arquivo semestral da ANP contendo milhares de registros.
   * Avaliação do tempo de resposta das consultas de postos e preços com filtros de ordenação sob volume expressivo de dados.
4. **Testes de Segurança:**
   * Validação de permissões de acesso em rotas restritas a motoristas e administradores.
   * Testes de injeção de parâmetros maliciosos e tokens forjados/expirados.

---

## 7. Decisões em Aberto e Não Definidas

```text
DECISÃO EM ABERTO
- Padrão de versionamento da API REST: adoção de prefixo na URI (/api/v1) versus versionamento por Header HTTP.
- Biblioteca específica para mapeamento de objetos: MapStruct versus mapeamento programático manual em mappers Java.
- Mecanismo de blacklist ou revogação de tokens JWT em caso de logout antes da expiração natural.
- Biblioteca de documentação OpenAPI/Swagger (ex.: Springdoc-openapi v2.x).
```
