# Padrões e Convenções de Desenvolvimento — FuelFinder

Este documento define os padrões de arquitetura de código, convenções de estilo, boas práticas de engenharia de software e diretrizes de desenvolvimento para a plataforma **FuelFinder**. 

Ele serve como o **guia de implementação oficial para a próxima aula**, oferecendo convenções normativas claras e templates de código práticos.

---

## 1. Princípios Gerais e Conformidade Técnica

O desenvolvimento do FuelFinder segue normas de excelência em engenharia de software:

* **Clean Code & SOLID:** Alta coesão interna, baixo acoplamento intermodular e responsabilidade única em cada classe.
* **OWASP Top 10:** Desenvolvimento seguro com foco na prevenção de SQL Injection (consultas estritamente parametrizadas via Spring Data JPA), proteção contra XSS e validação minuciosa de dados externos provenientes da ANP.
* **W3C / WCAG (Acessibilidade Web):** Estruturação semântica de páginas HTML5 (`<main>`, `<nav>`, `<article>`), contraste de cores acessível e botões identificados com `aria-label`.
* **IETF:** Comunicação via HTTPS; adesão estrita à semântica RESTful dos métodos HTTP (utilizando `PATCH` para atualizações parciais, conforme a RFC 5789).
* **ISO/IEC 25010:** Foco em manutenibilidade, testabilidade, usabilidade e eficiência de execução.

---

## 2. Organização do Código e Estrutura de Pacotes

O backend é organizado como um **Monólito Modular** baseado em **Spring Boot**:

```text
com.fuelfinder/
├── config/                  # Configurações globais (Security, WebMvc, Swagger/OpenAPI, CORS)
├── common/                  # Componentes transversais reutilizáveis
│   ├── exception/           # Classes base de exceções e tratamento global (@RestControllerAdvice)
│   ├── util/                # Utilitários gerais (fórmula de Haversine, datas, geolocalização)
│   └── dto/                 # DTOs compartilhados (ex.: paginação, ProblemDetail)
└── modules/                 # Módulos de domínio de negócio isolados
    ├── auth/                # Autenticação, emissão e validação de tokens JWT
    ├── user/                # Gestão de usuários, perfis (RBAC) e status de contas
    ├── vehicle/             # Gestão de veículos e métricas de consumo informado
    ├── station/             # Postos de combustível, localização e visualização de mapas
    ├── fuel/                # Catálogo de tipos de combustíveis
    ├── price/               # Preços históricos e correntes por posto
    ├── review/              # Avaliações numéricas, comentários e moderação
    ├── recommendation/      # Comparação de preços, paridade e recomendações
    ├── anp/                 # Pipeline de ingestão da base ANP (1º Semestre de 2026)
    └── admin/               # Operações administrativas e moderação centralizada
```

### 2.1 Estrutura Interna de Cada Módulo
```text
modules/<dominio>/
├── controller/              # Controladores REST (@RestController)
├── service/                 # Regras de negócio e interfaces de serviço
├── repository/              # Interfaces Spring Data JPA
├── entity/                  # Entidades JPA (@Entity) persistidas no PostgreSQL
├── dto/                     # Records imutáveis de entrada (Request) e saída (Response)
├── mapper/                  # Conversores entre Entity e DTO
└── exception/               # Exceções específicas do domínio
```

### 2.2 Regras de Dependência e Isolamento
1. **Repositórios Privados:** Um módulo nunca deve injetar ou acessar o `Repository` de outro módulo. O acesso a dados de outro domínio deve ocorrer exclusivamente através da interface pública de `Service`.
2. **Entidades Isoladas:** Entidades JPA não devem ser expostas em contratos públicos ou controllers externos; a comunicação entre camadas e clientes externos ocorre estritamente via **DTOs**.

---

## 3. Convenções de Nomenclatura e Idioma

* **Idioma do Código:** Todo o código-fonte (classes, métodos, variáveis, DTOs, entidades, tabelas e colunas de banco de dados) deve ser escrito em **Inglês**. A documentação de regras de negócio e as mensagens exibidas na interface do usuário permanecem em **Português**.
* **Classes, Records, Interfaces e Enums:** `PascalCase` (ex.: `StationService`, `VehicleResponseDTO`, `FuelType`).
* **Métodos e Variáveis:** `camelCase` (ex.: `calculateHaversineDistance`, `tankCapacity`, `stationRepository`).
* **Constantes e Valores de Enum:** `UPPER_SNAKE_CASE` (ex.: `ROLE_MOTORISTA`, `ROLE_ADMIN`, `GASOLINE_REGULAR`).
* **Tabelas do Banco de Dados:** `snake_case` no plural (ex.: `users`, `vehicles`, `stations`, `fuel_prices`, `reviews`, `anp_import_logs`).
* **Colunas do Banco de Dados:** `snake_case` (ex.: `user_id`, `tank_capacity`, `average_consumption_gasoline`).
* **Rotas da API REST:** `kebab-case` no plural (ex.: `/stations/{id}/fuel-prices`, `/fuel-prices/compare`).

---

## 4. Templates de Código Práticos para a Implementação

### 4.1 Padrão de DTO Imutável com Java `record` e Validação
```java
package com.fuelfinder.modules.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateVehicleRequestDTO(
    @NotBlank(message = "O apelido do veículo é obrigatório")
    @Size(min = 2, max = 50, message = "O apelido deve conter entre 2 e 50 caracteres")
    String nickname,

    @NotBlank(message = "A marca é obrigatória")
    String brand,

    @NotBlank(message = "O modelo é obrigatório")
    String model,

    @NotNull(message = "O ano de fabricação é obrigatório")
    Integer yearManufacture,

    @NotBlank(message = "O tipo de combustível é obrigatório")
    String fuelTypeAccepted,

    @NotNull(message = "A capacidade do tanque é obrigatória")
    @Positive(message = "A capacidade do tanque deve ser maior que zero")
    BigDecimal tankCapacity,

    @NotNull(message = "O consumo médio é obrigatório")
    @Positive(message = "O consumo médio deve ser maior que zero")
    BigDecimal averageConsumptionGasoline,

    BigDecimal averageConsumptionEthanol
) {}
```

### 4.2 Padrão de Controller RESTful com RBAC
```java
package com.fuelfinder.modules.vehicle.controller;

import com.fuelfinder.modules.vehicle.dto.CreateVehicleRequestDTO;
import com.fuelfinder.modules.vehicle.dto.VehicleResponseDTO;
import com.fuelfinder.modules.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vehicles")
@PreAuthorize("hasRole('MOTORISTA')")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponseDTO> create(
            @Valid @RequestBody CreateVehicleRequestDTO request,
            @AuthenticationPrincipal String userId) {
        VehicleResponseDTO created = vehicleService.create(request, UUID.fromString(userId));
        return ResponseEntity.created(URI.create("/vehicles/" + created.id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponseDTO>> listMine(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(vehicleService.listByUser(UUID.fromString(userId)));
    }
}
```

### 4.3 Padrão de Service com Transações e Regras de Negócio
```java
package com.fuelfinder.modules.vehicle.service;

import com.fuelfinder.modules.vehicle.dto.CreateVehicleRequestDTO;
import com.fuelfinder.modules.vehicle.dto.VehicleResponseDTO;
import com.fuelfinder.modules.vehicle.entity.Vehicle;
import com.fuelfinder.modules.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public VehicleResponseDTO create(CreateVehicleRequestDTO request, UUID userId) {
        Vehicle vehicle = new Vehicle(
            userId,
            request.nickname(),
            request.brand(),
            request.model(),
            request.yearManufacture(),
            request.fuelTypeAccepted(),
            request.tankCapacity(),
            request.averageConsumptionGasoline(),
            request.averageConsumptionEthanol()
        );
        Vehicle saved = vehicleRepository.save(vehicle);
        return toDTO(saved);
    }

    public List<VehicleResponseDTO> listByUser(UUID userId) {
        return vehicleRepository.findByUserId(userId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private VehicleResponseDTO toDTO(Vehicle v) {
        return new VehicleResponseDTO(
            v.getId(),
            v.getNickname(),
            v.getBrand(),
            v.getModel(),
            v.getYearManufacture(),
            v.getFuelTypeAccepted(),
            v.getTankCapacity(),
            v.getAverageConsumptionGasoline(),
            v.getAverageConsumptionEthanol()
        );
    }
}
```

### 4.4 Tratamento Centralizado de Exceções (RFC 7807)
```java
package com.fuelfinder.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Falha na validação dos campos de entrada.");
        problem.setTitle("Erro de Validação");
        problem.setType(URI.create("https://fuelfinder.com/errors/validation"));
        problem.setProperty("timestamp", Instant.now());

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("invalidFields", fieldErrors);
        return problem;
    }
}
```

---

## 5. Padrões de Git e Ciclo de Vida de Mudanças

* **Conventional Commits Obrigatório:**
  * `feat:` Nova funcionalidade para a plataforma.
  * `fix:` Correção de defeito ou bug.
  * `docs:` Modificações exclusivamente em documentação.
  * `style:` Formatação de código sem alteração semântica.
  * `refactor:` Refatoração de código sem alteração no comportamento funcional.
  * `test:` Adição ou modificação de testes automatizados.
  * `chore:` Tarefas de configuração, dependências ou infraestrutura.
* **Branches:**
  * `main`: Código estável homologado para apresentação e testes.
  * `feature/<nome-da-feature>`: Desenvolvimento isolado de cada funcionalidade.
