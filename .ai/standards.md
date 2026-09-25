# Convenções de Código e Estilo — FuelFinder

Este documento define os padrões de arquitetura de código, convenções de estilo, boas práticas de engenharia de software e diretrizes de desenvolvimento para a plataforma **FuelFinder**.

---

## 1. Princípios Gerais de Design

- **Clean Code & SOLID**: Alta coesão, baixo acoplamento e responsabilidade única em cada camada.
- **Fail-Fast & Validação Precoce**: Parâmetros de entrada e regras de domínio devem falhar de forma explícita e rápida com mensagens claras.
- **Imutabilidade por Padrão**: DTOs, objetos de valor (Value Objects) e configurações devem ser imutáveis sempre que possível.
- **Design Orientado a Domínio (DDD Pragmático)**: Termos de negócio bem definidos na linguagem ubíqua (`Station`, `FuelPrice`, `Vehicle`, `Review`, `Refueling`).
- **Stateless Backend**: A API REST não mantém estado de sessão HTTP em memória; autenticação e autorização são baseadas em tokens JWT.

---

## 2. Convenções da Linguagem Java (Java 25)

### 2.1 Padrões de Nomenclatura
- **Classes, Records, Interfaces, Enums**: `PascalCase` (ex.: `StationService`, `FuelType`, `CreateVehicleRequest`).
- **Métodos e Variáveis**: `camelCase` (ex.: `calculateCostPerKm`, `fuelPriceRepository`).
- **Constantes e Valores de Enum**: `UPPER_SNAKE_CASE` (ex.: `MAX_SEARCH_RADIUS_KM`, `ROLE_ADMIN`).
- **Pacotes**: `lowercase` sem separadores especiais (ex.: `com.fuelfinder.station.domain`).

### 2.2 Uso de Recursos Modernos do Java 25
- **Records**: Utilizar obrigatoriamente para DTOs (Request/Response), eventos e Value Objects.
- **Pattern Matching**: Utilizar pattern matching para `instanceof` e comandos `switch` exaustivos.
- **Sealed Types**: Utilizar interfaces e classes seladas (`sealed`) para modelar hierarquias fechadas de domínio (ex.: tipos de combustível, resultados de cálculo).
- **Text Blocks**: Utilizar text blocks (`"""`) para queries SQL complexas, documentação ou payloads de teste.
- **Virtual Threads (Project Loom)**: Configurar o runtime do Spring Boot para despachar requisições I/O-bound em Virtual Threads (`spring.threads.virtual.enabled=true`).

---

## 3. Estrutura de Camadas e Responsabilidades

O backend adota a arquitetura de **Monolito Modular em Camadas**:

```
com.fuelfinder
├── auth/                 # Autenticação, JWT, RBAC e Security
├── user/                 # Gestão de usuários e perfis
├── vehicle/              # Gestão de veículos e métricas de consumo
├── station/              # Postos de combustível e dados geoespaciais
├── price/                # Preços de combustíveis e histórico
├── review/               # Avaliações e moderação de postos
├── recommendation/       # Algoritmos de comparação e recomendação inteligente (Spring AI)
└── common/               # Exceções globais, utilitários e configurações compartilhadas
```

### 3.1 Controllers (`web` ou `controller`)
- Exclusivamente responsáveis por:
  - Receber e mapear requisições HTTP.
  - Aplicar validação declarativa nos parâmetros (`@Valid`, `@Validated`).
  - Chamar o respectivo serviço de aplicação/domínio.
  - Retornar o código de status HTTP correto e o DTO de resposta.
- **Regras estritas**:
  - Não conter lógica de negócio, regras de cálculo ou SQL direto.
  - Nunca expor entidades JPA diretamente; sempre utilizar DTOs (Records).

### 3.2 Services (`service` ou `domain`)
- Concentram a lógica de negócio, regras de cálculo, orquestração de transações (`@Transactional`) e validações de domínio.
- Lançam exceções de negócio especializadas (ex.: `StationNotFoundException`, `UnauthorizedOperationException`, `InvalidFuelPriceException`).

### 3.3 Repositories (`repository`)
- Interfaces que estendem `JpaRepository` ou `CrudRepository`.
- Métodos de busca customizados devem usar convenções do Spring Data ou JPQL/SQL nativo (com suporte espacial PostGIS) quando necessário.
- Consultas de leitura complexas devem retornar projeções ou DTOs para evitar overhead de entidades gerenciadas.

### 3.4 Entities (`entity` ou `model`)
- Entidades JPA mapeadas para tabelas do PostgreSQL.
- Chaves primárias UUID ou sequenciais bem definidas.
- Auditoria automática com campos `createdAt` e `updatedAt` (Spring Data Auditing).
- Métodos de negócio dentro das entidades para manipular estado interno de forma segura (encapsulamento).

### 3.5 DTOs (Data Transfer Objects)
- Implementados como Java `record`.
- Validações usando Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Email`, `@PositiveOrZero`).
- Nomenclatura clara: `<Acao><Entidade>Request` e `<Entidade>Response` (ex.: `CreateStationRequest`, `StationDetailResponse`).

---

## 4. Tratamento de Exceções e Respostas da API

### 4.1 Padrão RFC 7807 (Problem Details)
Todas as falhas e erros de validação retornam o formato padronizado `ProblemDetail`:

```json
{
  "type": "https://fuelfinder.com/errors/invalid-price",
  "title": "Preço de combustível inválido",
  "status": 400,
  "detail": "O preço informado não pode ser menor ou igual a zero.",
  "instance": "/stations/123/fuel-prices",
  "timestamp": "2026-09-24T20:58:00Z",
  "errors": [
    {
      "field": "price",
      "message": "deve ser maior que zero"
    }
  ]
}
```

### 4.2 Centralização com `@RestControllerAdvice`
- Tratamento unificado de exceções de validação (`MethodArgumentNotValidException`).
- Tratamento de recursos não encontrados (`ResourceNotFoundException` -> 404).
- Tratamento de violação de regras de acesso (`AccessDeniedException` -> 403).
- Tratamento de erros inesperados com log estruturado de rastreabilidade (Correlation ID / Trace ID).

---

## 5. Padrões de Segurança

- **Senhas**: Devem ser criptografadas utilizando BCrypt com fator de custo apropriado (mínimo 12 rounds).
- **Tokens JWT**: Devem conter claims mínimas (Subject: User ID, Roles, Expiração). Não armazenar dados sensíveis no payload.
- **RBAC (Role-Based Access Control)**:
  - Anotações nos endpoints: `@PreAuthorize("hasRole('ADMIN')")` ou `@PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")`.
  - Perfis definidos no sistema: `ROLE_DRIVER`, `ROLE_ADMIN` (e futuramente `ROLE_STATION_OPERATOR`).
- **Validação de Propriedade**: O usuário só pode alterar ou excluir seus próprios veículos, avaliações e dados cadastrais, a menos que possua o perfil de Administrador.

---

## 6. Convenções de Testes

- **Pirâmide de Testes**:
  1. **Testes Unitários**: Testar regras de negócio isoladas nos Services e Entidades (JUnit 5 + Mockito + AssertJ). Rápida execução, sem contexto de Spring.
  2. **Testes de Integração de Repositório**: Testar queries espaciais PostGIS e repositórios JPA com Testcontainers (`@DataJpaTest` + container PostgreSQL/PostGIS).
  3. **Testes de Controllers**: Validar contratos de API, serialização e status codes com `@WebMvcTest`.
  4. **Testes de Integração End-to-End**: Testes completos de fluxos de autenticação e comparação de preços com `@SpringBootTest`.
- **Nomenclatura de Testes**: Padrão `deve[ComportamentoEsperado]Quando[Cenario]` ou `should[ExpectedBehavior]When[Condition]`.
  - Exemplo: `deveCalcularConsumoMedioQuandoHouverAbastecimentosValidos()`.

---

## 7. Padrões de Versionamento e Git

- **Conventional Commits**:
  - `feat:` Nova funcionalidade para o usuário.
  - `fix:` Correção de bug.
  - `docs:` Alterações em documentação.
  - `style:` Formatação de código sem alteração semântica.
  - `refactor:` Refatoração de código sem alteração de comportamento.
  - `test:` Inclusão ou modificação de testes.
  - `chore:` Atualização de dependências, builds ou tarefas de infraestrutura.
- **Branches**:
  - `main`: Código de produção pronto para deploy.
  - `develop`: Código consolidado de desenvolvimento.
  - `feat/<nome-feature>`: Branches de desenvolvimento de funcionalidades.
  - `fix/<nome-bug>`: Correção de defeitos.
