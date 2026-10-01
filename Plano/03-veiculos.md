# Fase 3 — Gestão de Veículos

## Objetivo

Implementar o CRUD completo de veículos vinculados ao motorista autenticado, com validação de consumo médio informado e regras específicas para veículos Flex (bicombustíveis).

**Branch:** `feature/veiculos`
**Dependência:** Fase 2 (Autenticação e Usuários)

---

## Endpoints

| Método | Rota | Objetivo | Acesso | Status HTTP |
|--------|------|----------|--------|-------------|
| `POST` | `/vehicles` | Cadastra veículo com consumo informado | `ROLE_DRIVER` | `201 Created` |
| `GET`  | `/vehicles` | Lista veículos do motorista autenticado | `ROLE_DRIVER` | `200 OK` |
| `GET`  | `/vehicles/{id}` | Consulta detalhes de um veículo | `ROLE_DRIVER` | `200 OK` |
| `PATCH`| `/vehicles/{id}` | Atualiza dados do veículo | `ROLE_DRIVER` | `200 OK` |
| `DELETE`| `/vehicles/{id}` | Remove um veículo | `ROLE_DRIVER` | `204 No Content` |

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
- `tankCapacity`: Objeto `{value, unit}`. Combustíveis líquidos usam `LITER`; `CNG` usa `CUBIC_METER`. O valor deve ser positivo.

### Consumo Médio Informado
1. **Premissa central:** O consumo é informado diretamente pelo motorista
2. O sistema **não** consulta manuais de montadoras ou tabelas externas
3. Cada consumo é um objeto `{value, unit}`; o valor deve estar entre `1.0` e `40.0`.
   - Gasolina, etanol e diesel usam `KM_PER_LITER`.
   - GNV (`CNG`) usa `KM_PER_CUBIC_METER`.
4. **Veículos FLEX (bicombustíveis):**
   - Obrigatório informar `averageConsumptionGasoline` E `averageConsumptionEthanol`
   - Ambos devem ser valores positivos entre 1.0 e 40.0
5. **Veículos de combustível único:**
   - Informar apenas o consumo do combustível correspondente
   - Ex.: GASOLINE → apenas `averageConsumptionGasoline`
   - DIESEL → apenas `averageConsumptionDiesel`
   - CNG → apenas `averageConsumptionCng`, em km/m³, com capacidade em m³

### Atualização Parcial
- Campos omitidos em `PATCH /vehicles/{id}` permanecem inalterados.
- `null` pode limpar os campos de consumo, sujeito às regras do combustível após a atualização.
- Campos obrigatórios do veículo não podem ser definidos como `null`; validações de ano, capacidade e consumo usam os valores finais do veículo.

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

    @Embedded
    private TankCapacity tankCapacity;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "avg_consumption_gasoline_value")),
        @AttributeOverride(name = "unit", column = @Column(name = "avg_consumption_gasoline_unit"))
    })
    private FuelConsumption avgConsumptionGasoline;

    // Campos FuelConsumption equivalentes para etanol, diesel e CNG.

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
    @Valid TankCapacityDTO tankCapacity,
    @Valid ConsumptionDTO averageConsumptionGasoline,
    @Valid ConsumptionDTO averageConsumptionEthanol,
    @Valid ConsumptionDTO averageConsumptionDiesel,
    @Valid ConsumptionDTO averageConsumptionCng
) {}

// VehicleResponseDTO
public record VehicleResponseDTO(
    UUID id,
    String nickname,
    String brand,
    String model,
    Integer yearManufacture,
    String fuelTypeAccepted,
    TankCapacityDTO tankCapacity,
    ConsumptionDTO averageConsumptionGasoline,
    ConsumptionDTO averageConsumptionEthanol,
    ConsumptionDTO averageConsumptionDiesel,
    ConsumptionDTO averageConsumptionCng
) {}
```

`TankCapacityDTO` contém `value` e a unidade `LITER` ou `CUBIC_METER`.
`ConsumptionDTO` contém `value` e `KM_PER_LITER` ou `KM_PER_CUBIC_METER`.
Na migração de dados legados, a capacidade CNG em litros é dividida por 1.000;
o valor numérico do consumo CNG legado é preservado e classificado como km/m³.

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

        Vehicle vehicle = new Vehicle(
                userId,
                request.nickname(),
                request.brand(),
                request.model(),
                request.yearManufacture(),
                request.fuelTypeAccepted(),
                toTankCapacity(request.tankCapacity()),
                toConsumption(request.averageConsumptionGasoline()),
                toConsumption(request.averageConsumptionEthanol()),
                toConsumption(request.averageConsumptionDiesel()),
                toConsumption(request.averageConsumptionCng()));

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

    // O service valida as combinações combustível/consumo e as unidades:
    // km/L para líquidos, km/m³ e capacidade em m³ para CNG.

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
@PreAuthorize("hasRole('DRIVER')")
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

- [x] Motorista só acessa seus próprios veículos
- [x] Veículo FLEX exige ambos consumos (gasolina e etanol)
- [x] Consumo validado no range [1.0, 40.0] com unidade dimensional compatível
- [x] Ano de fabricação validado entre 1950 e ano corrente + 1
- [x] Capacidade do tanque deve ser positiva
- [x] CRUD completo funciona via Swagger
- [x] Testes passam

---

## Commit Sugerido

```
feat: add vehicle CRUD with fuel consumption validation for flex vehicles
```
