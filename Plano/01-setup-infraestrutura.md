# Fase 1 — Setup e Infraestrutura

## Objetivo

Configurar o projeto Spring Boot 3.4 com Java 21 LTS, banco de dados PostgreSQL 16 via Docker, migrações Flyway, tratamento global de exceções, configuração de CORS/Swagger e utilitários transversais.

**Branch:** `feature/setup-infraestrutura`

---

## Pré-requisitos

- JDK 21 LTS instalado
- Maven 3.9+ instalado
- Docker e Docker Compose instalados
- IDE configurada (IntelliJ IDEA recomendado)

---

## Tarefas

### 1.1 Criar Projeto Spring Boot via Spring Initializr

**Configuração do Initializr:**

| Campo | Valor |
|-------|-------|
| Project | Maven |
| Language | Java |
| Spring Boot | 3.4.x |
| Group | `com.fuelfinder` |
| Artifact | `fuelfinder` |
| Name | `FuelFinder` |
| Package name | `com.fuelfinder` |
| Packaging | Jar |
| Java | 21 |

**Dependências Spring Initializr:**
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- Flyway Migration
- PostgreSQL Driver

---

### 1.2 Configurar `pom.xml` com Dependências Adicionais

Adicionar ao `pom.xml` gerado:

```xml
<!-- JWT - JJWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- Springdoc OpenAPI (Swagger) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.7.0</version>
</dependency>

<!-- Testes -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

### 1.3 Estruturar Pacotes

Criar a árvore de pacotes conforme os padrões definidos:

```text
src/main/java/com/fuelfinder/
├── FuelFinderApplication.java
├── config/
│   ├── SecurityConfig.java         (placeholder — implementado na Fase 2)
│   ├── WebConfig.java
│   └── OpenApiConfig.java
├── common/
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── BusinessException.java
│   │   └── DuplicateResourceException.java
│   ├── util/
│   │   └── HaversineCalculator.java
│   └── dto/
│       └── ErrorResponseDTO.java
└── modules/
    ├── auth/
    │   ├── controller/
    │   ├── service/
    │   ├── dto/
    │   └── exception/
    ├── user/
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── dto/
    │   ├── mapper/
    │   └── exception/
    ├── vehicle/
    ├── station/
    ├── fuel/
    ├── price/
    ├── review/
    ├── recommendation/
    └── anp/
```

> **Nota:** Nas fases iniciais, os módulos conterão apenas as pastas vazias. O conteúdo será implementado na fase correspondente.

---

### 1.4 Configurar `application.yml`

```yaml
# application.yml
spring:
  application:
    name: FuelFinder

  datasource:
    url: jdbc:postgresql://localhost:5432/fuelfinder
    username: ${DB_USERNAME:fuelfinder}
    password: ${DB_PASSWORD:fuelfinder123}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null

# JWT Configuration
jwt:
  secret: ${JWT_SECRET:chave-secreta-dev-fuelfinder-2026-trocar-em-producao}
  expiration: 86400000  # 24 horas em milissegundos

# Swagger/OpenAPI
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: method

# Server
server:
  port: 8080
  error:
    include-message: always
    include-binding-errors: always
```

---

### 1.5 Docker Compose para PostgreSQL 16

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: fuelfinder-db
    environment:
      POSTGRES_DB: fuelfinder
      POSTGRES_USER: fuelfinder
      POSTGRES_PASSWORD: fuelfinder123
    ports:
      - "5432:5432"
    volumes:
      - fuelfinder_pgdata:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  fuelfinder_pgdata:
```

**Comandos:**
```bash
# Subir o banco
docker compose up -d

# Verificar status
docker compose ps

# Ver logs
docker compose logs postgres
```

---

### 1.6 Migração Flyway — Esquema Inicial

```sql
-- src/main/resources/db/migration/V1__initial_schema.sql

-- ===========================================
-- FuelFinder — Esquema Inicial do Banco de Dados
-- ===========================================

-- Tabela de Usuários
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_MOTORISTA',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ROLE_MOTORISTA', 'ROLE_ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

-- Tabela de Veículos
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    brand VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    year_manufacture INTEGER NOT NULL,
    fuel_type_accepted VARCHAR(20) NOT NULL,
    tank_capacity DECIMAL(6,2) NOT NULL,
    avg_consumption_gasoline DECIMAL(5,2),
    avg_consumption_ethanol DECIMAL(5,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_vehicles_fuel_type CHECK (fuel_type_accepted IN ('GASOLINE', 'ETHANOL', 'FLEX', 'DIESEL', 'CNG')),
    CONSTRAINT ck_vehicles_tank_positive CHECK (tank_capacity > 0),
    CONSTRAINT ck_vehicles_year CHECK (year_manufacture >= 1950)
);

-- Tabela de Postos de Combustível
CREATE TABLE stations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj VARCHAR(18) NOT NULL,
    corporate_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    brand VARCHAR(100),
    street VARCHAR(255),
    number VARCHAR(20),
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state CHAR(2) NOT NULL,
    postal_code VARCHAR(10),
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    total_reviews INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_stations_cnpj UNIQUE (cnpj),
    CONSTRAINT ck_stations_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_stations_latitude CHECK (latitude BETWEEN -90.0 AND 90.0),
    CONSTRAINT ck_stations_longitude CHECK (longitude BETWEEN -180.0 AND 180.0),
    CONSTRAINT ck_stations_rating CHECK (average_rating BETWEEN 0.00 AND 5.00)
);

-- Tabela de Tipos de Combustível
CREATE TABLE fuel_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    unit_of_measure VARCHAR(20) NOT NULL DEFAULT 'R$/litro',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_fuel_types_code UNIQUE (code)
);

-- Tabela de Preços de Combustíveis
CREATE TABLE fuel_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_id UUID NOT NULL,
    fuel_type_id UUID NOT NULL,
    sale_value DECIMAL(8,3) NOT NULL,
    collection_date DATE NOT NULL,
    data_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL_ADMIN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fuel_prices_station FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE CASCADE,
    CONSTRAINT fk_fuel_prices_fuel_type FOREIGN KEY (fuel_type_id) REFERENCES fuel_types(id),
    CONSTRAINT uk_fuel_prices_unique UNIQUE (station_id, fuel_type_id, collection_date),
    CONSTRAINT ck_fuel_prices_value_positive CHECK (sale_value > 0),
    CONSTRAINT ck_fuel_prices_source CHECK (data_source IN ('ANP_IMPORT', 'MANUAL_ADMIN'))
);

-- Tabela de Avaliações
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    station_id UUID NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_station FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE CASCADE,
    CONSTRAINT uk_reviews_user_station UNIQUE (user_id, station_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_status CHECK (status IN ('APPROVED', 'PENDING', 'REJECTED'))
);

-- Tabela de Log de Importação ANP
CREATE TABLE anp_import_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    reference_period VARCHAR(20) NOT NULL,
    source_url VARCHAR(500),
    import_start TIMESTAMP NOT NULL,
    import_end TIMESTAMP,
    total_records_read INTEGER NOT NULL DEFAULT 0,
    total_records_imported INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
    error_details TEXT,
    triggered_by UUID,
    CONSTRAINT fk_anp_logs_user FOREIGN KEY (triggered_by) REFERENCES users(id),
    CONSTRAINT ck_anp_logs_status CHECK (status IN ('SUCCESS', 'PARTIAL', 'FAILED'))
);

-- Índices para Performance
CREATE INDEX idx_vehicles_user_id ON vehicles(user_id);
CREATE INDEX idx_stations_status ON stations(status);
CREATE INDEX idx_stations_city_state ON stations(city, state);
CREATE INDEX idx_stations_lat_lng ON stations(latitude, longitude);
CREATE INDEX idx_fuel_prices_station ON fuel_prices(station_id);
CREATE INDEX idx_fuel_prices_fuel_type ON fuel_prices(fuel_type_id);
CREATE INDEX idx_fuel_prices_date ON fuel_prices(collection_date DESC);
CREATE INDEX idx_reviews_station ON reviews(station_id);
CREATE INDEX idx_reviews_user ON reviews(user_id);
CREATE INDEX idx_reviews_status ON reviews(status);
```

---

### 1.7 Migração Flyway — Seed de Tipos de Combustível

```sql
-- src/main/resources/db/migration/V2__seed_fuel_types.sql

INSERT INTO fuel_types (id, code, name, unit_of_measure, active) VALUES
    (gen_random_uuid(), 'GASOLINE_REGULAR',   'Gasolina Comum',     'R$/litro', true),
    (gen_random_uuid(), 'GASOLINE_ADDITIVE',  'Gasolina Aditivada', 'R$/litro', true),
    (gen_random_uuid(), 'ETHANOL',            'Etanol',             'R$/litro', true),
    (gen_random_uuid(), 'DIESEL_S10',         'Diesel S10',         'R$/litro', true),
    (gen_random_uuid(), 'DIESEL_S500',        'Diesel S500',        'R$/litro', true),
    (gen_random_uuid(), 'CNG',                'GNV',                'R$/m³',    true);
```

---

### 1.8 Tratamento Centralizado de Exceções (RFC 7807)

```java
// com.fuelfinder.common.exception.GlobalExceptionHandler

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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso não encontrado");
        problem.setType(URI.create("https://fuelfinder.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Recurso duplicado");
        problem.setType(URI.create("https://fuelfinder.com/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Erro de regra de negócio");
        problem.setType(URI.create("https://fuelfinder.com/errors/business"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

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

### 1.9 Utilitário: Fórmula de Haversine

```java
// com.fuelfinder.common.util.HaversineCalculator

package com.fuelfinder.common.util;

public final class HaversineCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineCalculator() {
        // Utility class
    }

    /**
     * Calcula a distância em linha reta entre dois pontos
     * usando a Fórmula de Haversine.
     *
     * @param lat1 Latitude do ponto 1 (graus decimais)
     * @param lng1 Longitude do ponto 1 (graus decimais)
     * @param lat2 Latitude do ponto 2 (graus decimais)
     * @param lng2 Longitude do ponto 2 (graus decimais)
     * @return Distância em quilômetros
     */
    public static double calculateDistanceKm(
            double lat1, double lng1,
            double lat2, double lng2) {

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.asin(Math.sqrt(a));

        return EARTH_RADIUS_KM * c;
    }
}
```

---

### 1.10 Configuração do Swagger/OpenAPI

```java
// com.fuelfinder.config.OpenApiConfig

package com.fuelfinder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fuelFinderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FuelFinder API")
                        .description("API REST para consulta, localização e comparação de preços de combustíveis")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FuelFinder Team")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .schemaRequirement("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
    }
}
```

---

## Arquivos a Criar nesta Fase

| Arquivo | Descrição |
|---------|-----------|
| `pom.xml` | Configuração Maven com todas as dependências |
| `docker-compose.yml` | PostgreSQL 16 containerizado |
| `src/main/resources/application.yml` | Configurações da aplicação |
| `src/main/resources/db/migration/V1__initial_schema.sql` | Esquema completo do banco |
| `src/main/resources/db/migration/V2__seed_fuel_types.sql` | Dados iniciais de combustíveis |
| `src/main/java/.../FuelFinderApplication.java` | Classe principal |
| `src/main/java/.../config/WebConfig.java` | CORS |
| `src/main/java/.../config/OpenApiConfig.java` | Swagger |
| `src/main/java/.../common/exception/GlobalExceptionHandler.java` | Tratamento de erros RFC 7807 |
| `src/main/java/.../common/exception/ResourceNotFoundException.java` | Exceção 404 |
| `src/main/java/.../common/exception/BusinessException.java` | Exceção 422 |
| `src/main/java/.../common/exception/DuplicateResourceException.java` | Exceção 409 |
| `src/main/java/.../common/util/HaversineCalculator.java` | Cálculo de distância |

---

## Critérios de Aceitação

- [ ] `mvn clean compile` executa sem erros
- [ ] `docker compose up -d` sobe o PostgreSQL
- [ ] Aplicação inicia com `mvn spring-boot:run` e conecta ao banco
- [ ] Flyway aplica as migrações `V1` e `V2` automaticamente
- [ ] Tabela `fuel_types` contém 6 registros de seed
- [ ] Swagger UI acessível em `http://localhost:8080/swagger-ui.html`
- [ ] Haversine calcula corretamente (ex.: SP ↔ RJ ≈ 357 km)

---

## Commit Sugerido

```
chore: setup spring boot 3.4 project with postgresql, flyway and swagger
```
