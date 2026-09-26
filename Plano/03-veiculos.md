# Fase 3 — Gestão de Veículos

## Objetivo

Implementar o CRUD completo de veículos vinculados ao motorista autenticado, com validação de consumo médio informado e regras específicas para veículos Flex (bicombustíveis).

**Branch:** `feature/veiculos`
**Dependência:** Fase 2 (Autenticação e Usuários)

---

## Endpoints

| Método | Rota | Objetivo | Acesso | Status HTTP |
|--------|------|----------|--------|-------------|
| `POST` | `/vehicles` | Cadastra veículo com consumo informado | `ROLE_MOTORISTA` | `201 Created` |
| `GET`  | `/vehicles` | Lista veículos do motorista autenticado | `ROLE_MOTORISTA` | `200 OK` |
| `GET`  | `/vehicles/{id}` | Consulta detalhes de um veículo | `ROLE_MOTORISTA` | `200 OK` |
| `PATCH`| `/vehicles/{id}` | Atualiza dados do veículo | `ROLE_MOTORISTA` | `200 OK` |
| `DELETE`| `/vehicles/{id}` | Remove um veículo | `ROLE_MOTORISTA` | `204 No Content` |

---

## Regras de Negócio

### Cadastro de Veículo
1. Veículo é vinculado ao `user_id` do motorista autenticado
2. Um motorista pode cadastrar múltiplos veículos
3. O motorista só acessa/modifica seus próprios veículos (ownership check)

### Campos Obrigatórios
- `nickname`: Apelido amigável (2-50 caracteres)
- `brand`: Marca do veículo
- `model`: Modelo do veículo
- `yearManufacture`: Ano de fabricação (inteiro entre 1950 e ano corrente + 1)
- `fuelTypeAccepted`: Enum `[GASOLINE, ETHANOL, FLEX, DIESEL, CNG]`
- `tankCapacity`: Capacidade do tanque em litros (valor positivo > 0)

### Consumo Médio Informado
1. **Premissa central:** O consumo é informado diretamente pelo motorista
2. O sistema **não** consulta manuais de montadoras ou tabelas externas
3. Validação numérica: consumo entre `1.0` e `40.0` km/L
4. **Veículos FLEX (bicombustíveis):**
   - Obrigatório informar `averageConsumptionGasoline` E `averageConsumptionEthanol`
   - Ambos devem ser valores positivos entre 1.0 e 40.0
5. **Veículos de combustível único:**
   - Informar apenas o consumo do combustível correspondente
   - Ex.: GASOLINE → apenas `averageConsumptionGasoline`
   - Ex.: DIESEL → apenas `averageConsumptionGasoline` (campo genérico)

---

## Tarefas de Implementação

### 3.1 Entidade Vehicle

```java
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "year_manufacture", nullable = false)
    private Integer yearManufacture;

    @Column(name = "fuel_type_accepted", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FuelTypeAccepted fuelTypeAccepted;

    @Column(name = "tank_capacity", nullable = false, precision = 6, scale = 2)
    private BigDecimal tankCapacity;

    @Column(name = "avg_consumption_gasoline", precision = 5, scale = 2)
    private BigDecimal avgConsumptionGasoline;

    @Column(name = "avg_consumption_ethanol", precision = 5, scale = 2)
    private BigDecimal avgConsumptionEthanol;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Construtores, getters e setters
}
```

### 3.2 Enum FuelTypeAccepted

```java
public enum FuelTypeAccepted {
    GASOLINE,
    ETHANOL,
    FLEX,
    DIESEL,
    CNG
}
```

### 3.3 VehicleRepository

```java
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findByUserId(UUID userId);
    Optional<Vehicle> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}
```

### 3.4 DTOs

```java
// CreateVehicleRequestDTO
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

    @NotNull(message = "O tipo de combustível é obrigatório")
    String fuelTypeAccepted,

    @NotNull(message = "A capacidade do tanque é obrigatória")
    @Positive(message = "A capacidade do tanque deve ser maior que zero")
    BigDecimal tankCapacity,

    @DecimalMin(value = "1.0", message = "O consumo deve ser no mínimo 1.0 km/L")
    @DecimalMax(value = "40.0", message = "O consumo deve ser no máximo 40.0 km/L")
    BigDecimal averageConsumptionGasoline,

    @DecimalMin(value = "1.0", message = "O consumo deve ser no mínimo 1.0 km/L")
    @DecimalMax(value = "40.0", message = "O consumo deve ser no máximo 40.0 km/L")
    BigDecimal averageConsumptionEthanol
) {}

// VehicleResponseDTO
public record VehicleResponseDTO(
    UUID id,
    String nickname,
    String brand,
    String model,
    Integer yearManufacture,
    String fuelTypeAccepted,
    BigDecimal tankCapacity,
    BigDecimal averageConsumptionGasoline,
    BigDecimal averageConsumptionEthanol
) {}
```

### 3.5 VehicleService

```java
@Service
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    @Transactional
    public VehicleResponseDTO create(CreateVehicleRequestDTO request, UUID userId) {
        validateFuelConsumption(request);
        validateYear(request.yearManufacture());

        Vehicle vehicle = new Vehicle();
        vehicle.setUserId(userId);
        vehicle.setNickname(request.nickname());
        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        vehicle.setYearManufacture(request.yearManufacture());
        vehicle.setFuelTypeAccepted(FuelTypeAccepted.valueOf(request.fuelTypeAccepted()));
        vehicle.setTankCapacity(request.tankCapacity());
        vehicle.setAvgConsumptionGasoline(request.averageConsumptionGasoline());
        vehicle.setAvgConsumptionEthanol(request.averageConsumptionEthanol());

        Vehicle saved = vehicleRepository.save(vehicle);
        return toDTO(saved);
    }

    public List<VehicleResponseDTO> listByUser(UUID userId) {
        return vehicleRepository.findByUserId(userId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public VehicleResponseDTO findByIdAndUser(UUID vehicleId, UUID userId) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo não encontrado."));
        return toDTO(vehicle);
    }

    @Transactional
    public void delete(UUID vehicleId, UUID userId) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo não encontrado."));
        vehicleRepository.delete(vehicle);
    }

    private void validateFuelConsumption(CreateVehicleRequestDTO request) {
        if ("FLEX".equals(request.fuelTypeAccepted())) {
            if (request.averageConsumptionGasoline() == null
                    || request.averageConsumptionEthanol() == null) {
                throw new BusinessException(
                    "Veículos Flex devem informar consumo médio de gasolina e etanol.");
            }
        }
    }

    private void validateYear(Integer year) {
        int maxYear = Year.now().getValue() + 1;
        if (year < 1950 || year > maxYear) {
            throw new BusinessException(
                "Ano de fabricação deve ser entre 1950 e " + maxYear + ".");
        }
    }

    private VehicleResponseDTO toDTO(Vehicle v) {
        return new VehicleResponseDTO(
            v.getId(), v.getNickname(), v.getBrand(), v.getModel(),
            v.getYearManufacture(), v.getFuelTypeAccepted().name(),
            v.getTankCapacity(), v.getAvgConsumptionGasoline(),
            v.getAvgConsumptionEthanol()
        );
    }
}
```

### 3.6 VehicleController

```java
@RestController
@RequestMapping("/vehicles")
@PreAuthorize("hasRole('MOTORISTA')")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ResponseEntity<VehicleResponseDTO> create(
            @Valid @RequestBody CreateVehicleRequestDTO request,
            @AuthenticationPrincipal String userId) {
        VehicleResponseDTO created = vehicleService.create(
                request, UUID.fromString(userId));
        return ResponseEntity
                .created(URI.create("/vehicles/" + created.id()))
                .body(created);
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponseDTO>> listMine(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
                vehicleService.listByUser(UUID.fromString(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponseDTO> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
                vehicleService.findByIdAndUser(id, UUID.fromString(userId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VehicleResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleRequestDTO request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
                vehicleService.update(id, request, UUID.fromString(userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {
        vehicleService.delete(id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
```

---

## Testes

### Unitários
- `VehicleServiceTest`:
  - Criação com sucesso (FLEX com ambos consumos)
  - Criação FLEX sem consumo de etanol → `BusinessException`
  - Ano inválido (1949, ano corrente + 2) → `BusinessException`
  - Listagem retorna apenas veículos do usuário
  - Acesso a veículo de outro usuário → `ResourceNotFoundException`

### Integração
- `VehicleControllerIntegrationTest`:
  - POST retorna 201 com Location header
  - GET lista somente veículos do motorista autenticado
  - DELETE retorna 204
  - Acesso sem token retorna 401
  - Admin tentando acessar retorna 403

---

## Critérios de Aceitação

- [ ] Motorista só acessa seus próprios veículos
- [ ] Veículo FLEX exige ambos consumos (gasolina e etanol)
- [ ] Consumo validado no range [1.0, 40.0] km/L
- [ ] Ano de fabricação validado entre 1950 e ano corrente + 1
- [ ] Capacidade do tanque deve ser positiva
- [ ] CRUD completo funciona via Swagger
- [ ] Testes passam

---

## Commit Sugerido

```
feat: add vehicle CRUD with fuel consumption validation for flex vehicles
```
