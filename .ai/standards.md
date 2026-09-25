# Padrões e Convenções de Desenvolvimento — FuelFinder

Este documento estabelece as convenções, diretrizes de arquitetura de código, padrões de API REST, segurança e práticas de validação e testes para o projeto **FuelFinder**. 

Como o projeto encontra-se em fase de concepção e o código-fonte ainda não foi implementado, este arquivo atua como a **fonte de diretrizes normativas** que deverão ser rigorosamente seguidas durante a implementação futura.

---

## 1. Organização do Projeto

O sistema é concebido como um **monólito modular** baseado em Spring Boot. A organização do código deve refletir a separação clara de responsabilidades técnicas e a delimitação dos domínios de negócio.

### 1.1 Estrutura de Módulos e Pacotes
A divisão de pacotes deve balancear a modularidade dos domínios com a separação clássica em camadas. A estrutura esperada na aplicação Spring Boot segue o padrão modular:

```text
com.fuelfinder/
├── config/              # Configurações globais (segurança, CORS, web, etc.)
├── common/              # Utilitários transversais, classes base e exceptions globais
│   ├── exception/
│   └── util/
└── modules/             # Domínios de negócio isolados modularmente
    ├── auth/            # Módulo de Autenticação e Autorização (JWT / RBAC)
    ├── user/            # Módulo de Usuários (Motoristas e Administradores)
    ├── vehicle/         # Módulo de Veículos e Consumo
    ├── station/         # Módulo de Postos de Combustível e Localização
    ├── fuel/            # Módulo de Tipos de Combustível
    ├── price/           # Módulo de Preços e Histórico
    ├── review/          # Módulo de Avaliações e Moderação
    └── recommendation/  # Módulo de Comparação, Recomendações e Custo-Benefício
```

Dentro de cada módulo (`modules/<dominio>`), a separação interna esperada é:
```text
├── controller/          # Controladores REST (entrada HTTP)
├── dto/                 # Data Transfer Objects (Request / Response)
│   ├── request/
│   └── response/
├── entity/              # Entidades persistidas no banco de dados relacional
├── repository/          # Interfaces de acesso a dados (Spring Data)
├── service/             # Regras de negócio e casos de uso
└── exception/           # Exceções específicas do domínio
```

> **Diretriz de Dependência:** Módulos não devem acessar repositórios de outros módulos diretamente. A comunicação intermodular deve ocorrer preferencialmente através de serviços (`Service`) bem definidos.

---

## 2. Convenções de Código

### 2.1 Nomenclatura Geral
* **Linguagem do Código:** Nomes de classes, métodos, variáveis, DTOs e entidades devem ser escritos em Inglês para manter conformidade idiomática com o framework Spring Boot e ecossistema Java.
* **Classes e Interfaces:** `PascalCase` (ex.: `StationController`, `VehicleService`).
* **Métodos e Variáveis:** `camelCase` (ex.: `findNearbyStations`, `averageConsumption`).
* **Constantes e Enums:** `UPPER_SNAKE_CASE` (ex.: `ROLE_MOTORISTA`, `ROLE_ADMIN`, `GASOLINE_REGULAR`).

### 2.2 Convenções por Artefato
* **Controllers:** Sufixo `Controller` (ex.: `StationController`, `PriceComparisonController`).
* **Services:** Sufixo `Service` para a classe ou interface de serviço (ex.: `VehicleService`, `FuelRecommendationService`).
* **Repositories:** Sufixo `Repository` (ex.: `StationRepository`, `UserRepository`).
* **Entities:** Nomes substantivos no singular representando a tabela de domínio (ex.: `Station`, `Vehicle`, `FuelPrice`).
* **DTOs:** Sufixos explícitos indicando sua direção:
  * Requisições: `*Request` ou `*RequestDTO` (ex.: `CreateVehicleRequest`, `PriceFilterRequest`).
  * Respostas: `*Response` ou `*ResponseDTO` (ex.: `StationSummaryResponse`, `FuelRecommendationResponse`).
* **Exceptions:** Sufixo `Exception` com nome descritivo do erro de negócio (ex.: `StationNotFoundException`, `InvalidConsumptionCalculationException`).
* **Enums:** Sufixo descritivo ou nome do tipo em `PascalCase` com valores em `UPPER_SNAKE_CASE` (ex.: `UserRole`, `FuelType`).

---

## 3. Padrões de API REST

A API do FuelFinder deve ser estritamente RESTful, previsível e stateless.

### 3.1 Nomenclatura de Endpoints
* Endpoints devem utilizar substantivos no plural, letras minúsculas e hífens para palavras compostas (`kebab-case`).
* O prefixo global da API deve conter versionamento: `/api/v1/...`.
* Estrutura de caminhos deve representar hierarquia de recursos:
  * `GET /api/v1/stations` (Listagem de postos)
  * `GET /api/v1/stations/{id}` (Detalhes de um posto)
  * `POST /api/v1/stations` (Cadastro de posto - Admin)
  * `GET /api/v1/stations/{id}/prices` (Preços de um posto específico)
  * `GET /api/v1/vehicles` (Veículos do usuário autenticado)
  * `POST /api/v1/vehicles` (Cadastro de veículo)
  * `GET /api/v1/stations/{id}/reviews` (Avaliações de um posto)
  * `POST /api/v1/stations/{id}/reviews` (Criar avaliação)

### 3.2 Métodos HTTP e Semântica
* `GET`: Recuperação de recursos. Seguro e idempotente. Não deve alterar estado no servidor.
* `POST`: Criação de recursos ou operações de processamento (ex.: autenticação, cálculo sob demanda).
* `PUT`: Substituição integral de recurso existente.
* `PATCH`: Atualização parcial de recurso existente.
* `DELETE`: Remoção de recurso existente.

### 3.3 Códigos de Resposta HTTP
* `200 OK`: Sucesso em operações de leitura, atualização ou processamento com retorno no corpo.
* `201 Created`: Recurso criado com sucesso. Deve preferencialmente incluir o cabeçalho `Location` com a URI do recurso criado.
* `204 No Content`: Operação executada com sucesso sem conteúdo no corpo de resposta (ex.: deleção com sucesso).
* `400 Bad Request`: Requisição malformada ou falha em validações sintáticas/semânticas.
* `401 Unauthorized`: Falha de autenticação (token ausente, inválido ou expirado).
* `403 Forbidden`: Usuário autenticado não possui permissão para acessar o recurso (violação de RBAC).
* `404 Not Found`: Recurso não localizado para o identificador informado.
* `422 Unprocessable Entity`: Dados compreensíveis, mas violam regras de validação de negócio (opcional frente ao 400).
* `500 Internal Server Error`: Erro inesperado do servidor (detalhes técnicos sensíveis nunca devem ser expostos ao cliente).

### 3.4 DTOs de Entrada e Saída
* **Isolamento de Entidades:** Jamais expor entidades do banco diretamente nos endpoints. Toda comunicação com a API deve ser intermediada por DTOs.
* **Projeção e Imutabilidade:** DTOs de resposta devem fornecer apenas os dados necessários para o cliente, prevenindo vazamento de dados internos (ex.: senhas, dados de auditoria irrelevantes).

---

## 4. Segurança e Autorização (RBAC)

O FuelFinder adota autenticação stateless baseada em tokens JWT e autorização por controle de acesso baseado em papéis (Role-Based Access Control - RBAC).

### 4.1 Autenticação
* Autenticação via `POST /api/v1/auth/login`.
* O cliente deve receber um token JWT e enviá-lo nas requisições subsequentes no cabeçalho:
  ```http
  Authorization: Bearer <token_jwt>
  ```
* Se o token não for fornecido em rotas protegidas, a API deve responder `401 Unauthorized`.

### 4.2 Perfis e Papéis (RBAC)
Os perfis definidos para o MVP são:
* `ROLE_MOTORISTA`: Usuário padrão do sistema.
* `ROLE_ADMIN`: Administrador da plataforma.

### 4.3 Proteção de Endpoints
* **Rotas Públicas:** Consulta básica de postos, comparação de preços, leitura de avaliações, login e cadastro de usuários.
* **Rotas Protegidas - Motorista (`ROLE_MOTORISTA`):** Gestão de veículos próprios, registro de avaliações, preferências de recomendação.
* **Rotas Protegidas - Administrador (`ROLE_ADMIN`):** Cadastro e edição de postos, gerenciamento de preços oficiais, gerenciamento de combustíveis, moderação de avaliações e administração geral.

---

## 5. Validação de Dados

* Todos os dados de entrada recebidos via `body`, `path variables` ou `query parameters` devem ser validados antes de qualquer processamento negocial.
* Devem ser validadas restrições como: obrigatoriedade de campos, limites numéricos (ex.: capacidade de tanque > 0, consumo > 0), intervalos de coordenadas válidas (-90 a 90 para latitude, -180 a 180 para longitude), formatos de e-mail e tamanhos de textos.
* Quando houver falha de validação, a requisição deve ser imediatamente interrompida, retornando código `400 Bad Request` com o detalhamento dos campos rejeitados.

---

## 6. Tratamento de Erros

A API deve prover um tratamento centralizado de exceções através de controladores de conselho (`@ControllerAdvice` / `@RestControllerAdvice` no Spring Boot).

### 6.1 Regra de Isolamento
* Regras de negócio **nunca** devem ser implementadas ou espalhadas dentro dos `Controllers`.
* Os `Controllers` apenas recebem requisições, delegam a execução para os `Services` e convertem resultados em DTOs e códigos HTTP apropriados.
* Violações de regras de negócio devem disparar exceções de domínio no `Service`, capturadas pela camada centralizada de tratamento de erros.

### 6.2 Estrutura Padronizada de Resposta de Erro
Todas as respostas de erro da API devem retornar um payload JSON consistente com a seguinte estrutura:

```json
{
  "timestamp": "2026-09-24T21:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Mensagem clara e objetiva sobre o erro",
  "path": "/api/v1/vehicles",
  "errors": [
    {
      "field": "tankCapacity",
      "message": "A capacidade do tanque deve ser maior que zero."
    }
  ]
}
```

---

## 7. Estratégia de Testes

Para garantir a confiabilidade do FuelFinder durante a evolução do monólito modular, deve ser adotada uma pirâmide de testes proporcional à complexidade inicial:

### 7.1 Testes Unitários
* **Foco:** Serviços de domínio (`Service`), utilitários de cálculo de consumo e componentes de recomendação e custo-benefício.
* **Premissa:** Rápidos, isolados e sem dependência de banco de dados ou contexto de rede.

### 7.2 Testes de Integração
* **Foco:** Endpoints REST (`Controllers`), segurança (validação de JWT e RBAC) e repositórios de dados (`Repository`).
* **Premissa:** Validação do fluxo completo de requisição/resposta, persistência e transacionalidade.

---

## 8. Decisões em Aberto e Pendências

As seguintes decisões técnicas não estão fechadas nas especificações atuais e devem ser tratadas como pendentes:

```text
DECISÃO EM ABERTO
- Biblioteca e ferramenta específica de migração de banco de dados (ex.: Flyway vs Liquibase).
- Framework específico de testes de integração e asserções a ser padronizado além das ferramentas nativas do ecossistema Spring.
- Estrutura de documentação automática de API (ex.: OpenAPI / Swagger).
- Estratégia de renovação de tokens JWT (Refresh Tokens).
```
