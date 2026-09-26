# Fase 5 — Preços de Combustíveis e Comparação

## Objetivo

Implementar a gestão de preços de combustíveis por posto, o catálogo de tipos de combustível e o endpoint de comparação que ordena postos por preço e/ou distância.

**Branch:** `feature/precos-combustiveis`
**Dependência:** Fase 4 (Postos e Geolocalização)

---

## Endpoints

| Método | Rota | Objetivo | Acesso | Status HTTP |
|--------|------|----------|--------|-------------|
| `GET`  | `/stations/{id}/fuel-prices` | Lista preços cadastrados do posto | Público / Autenticado | `200 OK` |
| `POST` | `/stations/{id}/fuel-prices` | Registra novo preço de combustível | `ROLE_ADMIN` | `201 Created` |
| `PATCH`| `/stations/{id}/fuel-prices/{priceId}` | Atualiza preço existente | `ROLE_ADMIN` | `200 OK` |
| `GET`  | `/fuel-prices/compare` | Compara postos ordenados por preço/distância | Público / Autenticado | `200 OK` |

---

## Regras de Negócio

### Preços de Combustíveis
1. Cada preço é vinculado a um `station_id` e um `fuel_type_id`
2. **Data de coleta obrigatória:** `collection_date` — data da coleta ANP ou data do cadastro manual
3. **Fonte de dados:** `data_source` → `ANP_IMPORT` (carga automática) ou `MANUAL_ADMIN` (cadastro pelo administrador)
4. **Unicidade:** constraint `(station_id, fuel_type_id, collection_date)` — não pode haver dois preços para o mesmo combustível no mesmo posto na mesma data
5. **Caráter informativo:** Preços têm caráter histórico. O sistema não garante preços em tempo real

### Comparação de Preços
1. Recebe parâmetros: `latitude`, `longitude`, `radiusKm`, `fuelTypeCode` (opcional), `sortBy` (price/distance)
2. Retorna postos com preços do combustível selecionado, ordenados conforme critério
3. Inclui fórmulas calculadas quando `vehicleId` é fornecido:
   - Custo para tanque cheio
   - Autonomia estimada
   - Custo por quilômetro

### Fórmulas de Domínio

**Custo para encher o tanque:**

$$\text{Custo Tanque Cheio (R\$)} = \text{Capacidade do Tanque (L)} \times \text{Preço por Litro (R\$/L)}$$

**Autonomia estimada:**

$$\text{Autonomia (km)} = \text{Capacidade do Tanque (L)} \times \text{Consumo Médio (km/L)}$$

**Custo por quilômetro:**

$$\text{Custo/KM (R\$/km)} = \frac{\text{Preço por Litro (R\$/L)}}{\text{Consumo Médio (km/L)}}$$

**Custo efetivo de deslocamento:**

$$\text{Custo Efetivo Total} = \text{Custo do Abastecimento} + \left(2 \times \text{Distância (km)} \times \text{Custo/KM}\right)$$

---

## Tarefas de Implementação

### 5.1 Entidade FuelType

```java
@Entity
@Table(name = "fuel_types")
public class FuelType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private String unitOfMeasure = "R$/litro";

    @Column(nullable = false)
    private Boolean active = true;

    // Construtores, getters, setters
}
```

### 5.2 Entidade FuelPrice

```java
@Entity
@Table(name = "fuel_prices")
public class FuelPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @Column(name = "sale_value", nullable = false, precision = 8, scale = 3)
    private BigDecimal saleValue;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(name = "data_source", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private DataSource dataSource = DataSource.MANUAL_ADMIN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Construtores, getters, setters
}
```

### 5.3 Repositórios

```java
public interface FuelTypeRepository extends JpaRepository<FuelType, UUID> {
    Optional<FuelType> findByCode(String code);
    List<FuelType> findByActiveTrue();
}

public interface FuelPriceRepository extends JpaRepository<FuelPrice, UUID> {
    List<FuelPrice> findByStationIdOrderByCollectionDateDesc(UUID stationId);

    @Query("""
        SELECT fp FROM FuelPrice fp
        WHERE fp.station.id = :stationId
          AND fp.collectionDate = (
              SELECT MAX(fp2.collectionDate) FROM FuelPrice fp2
              WHERE fp2.station.id = fp.station.id
                AND fp2.fuelType.id = fp.fuelType.id
          )
        """)
    List<FuelPrice> findLatestPricesByStation(@Param("stationId") UUID stationId);

    Optional<FuelPrice> findByStationIdAndFuelTypeIdAndCollectionDate(
            UUID stationId, UUID fuelTypeId, LocalDate collectionDate);
}
```

### 5.4 DTOs

```java
// CreateFuelPriceRequestDTO
public record CreateFuelPriceRequestDTO(
    @NotBlank(message = "O código do combustível é obrigatório")
    String fuelTypeCode,

    @NotNull(message = "O valor de venda é obrigatório")
    @Positive(message = "O valor de venda deve ser positivo")
    BigDecimal saleValue,

    @NotNull(message = "A data de coleta é obrigatória")
    LocalDate collectionDate
) {}

// FuelPriceResponseDTO
public record FuelPriceResponseDTO(
    UUID id,
    String fuelTypeCode,
    String fuelTypeName,
    BigDecimal saleValue,
    LocalDate collectionDate,
    String dataSource,
    String unitOfMeasure
) {}

// CompareResultDTO
public record CompareResultDTO(
    UUID stationId,
    String stationName,
    String brand,
    String fuelType,
    BigDecimal price,
    Double distanceKm,
    BigDecimal costPerKm,
    BigDecimal estimatedFullTankCost,
    BigDecimal estimatedRange,
    BigDecimal estimatedRoundTripCost
) {}
```

### 5.5 FuelPriceService

```java
@Service
@Transactional(readOnly = true)
public class FuelPriceService {

    private final FuelPriceRepository fuelPriceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final StationRepository stationRepository;

    public List<FuelPriceResponseDTO> getLatestPrices(UUID stationId) {
        return fuelPriceRepository.findLatestPricesByStation(stationId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public FuelPriceResponseDTO create(UUID stationId, CreateFuelPriceRequestDTO request) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));

        FuelType fuelType = fuelTypeRepository.findByCode(request.fuelTypeCode())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de combustível inválido."));

        // Verificar unicidade
        fuelPriceRepository.findByStationIdAndFuelTypeIdAndCollectionDate(
                stationId, fuelType.getId(), request.collectionDate())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                        "Já existe um preço para este combustível nesta data.");
                });

        FuelPrice fuelPrice = new FuelPrice();
        fuelPrice.setStation(station);
        fuelPrice.setFuelType(fuelType);
        fuelPrice.setSaleValue(request.saleValue());
        fuelPrice.setCollectionDate(request.collectionDate());
        fuelPrice.setDataSource(DataSource.MANUAL_ADMIN);

        FuelPrice saved = fuelPriceRepository.save(fuelPrice);
        return toDTO(saved);
    }

    /**
     * Calcula custo por km = preço / consumo
     */
    public BigDecimal calculateCostPerKm(BigDecimal price, BigDecimal consumption) {
        if (consumption == null || consumption.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return price.divide(consumption, 4, RoundingMode.HALF_UP);
    }

    /**
     * Calcula custo efetivo total = custo abastecimento + (2 × distância × custo/km)
     */
    public BigDecimal calculateEffectiveCost(
            BigDecimal tankCapacity, BigDecimal price,
            double distanceKm, BigDecimal costPerKm) {
        BigDecimal fuelCost = tankCapacity.multiply(price);
        BigDecimal travelCost = costPerKm
                .multiply(BigDecimal.valueOf(distanceKm * 2));
        return fuelCost.add(travelCost);
    }

    // Métodos auxiliares
}
```

---

## Exemplo de Resposta: `GET /fuel-prices/compare`

**Request:** `GET /fuel-prices/compare?latitude=-23.55&longitude=-46.63&radiusKm=5&fuelTypeCode=GASOLINE_REGULAR&vehicleId=...`

**Response (200 OK):**

```json
[
  {
    "stationId": "c1f7b8a2-...",
    "stationName": "Posto Central",
    "brand": "IPIRANGA",
    "fuelType": "GASOLINE_REGULAR",
    "price": 5.79,
    "distanceKm": 2.45,
    "costPerKm": 0.4289,
    "estimatedFullTankCost": 312.66,
    "estimatedRange": 729.00,
    "estimatedRoundTripCost": 2.10
  }
]
```

---

## Testes

### Unitários
- `FuelPriceServiceTest`:
  - Criação de preço com sucesso
  - Duplicata retorna erro
  - `calculateCostPerKm` com valores conhecidos
  - `calculateEffectiveCost` com valores conhecidos

### Integração
- `FuelPriceControllerIntegrationTest`:
  - GET lista preços do posto
  - POST como ADMIN retorna 201
  - POST como MOTORISTA retorna 403
  - GET compare retorna lista ordenada por preço

---

## Critérios de Aceitação

- [ ] Preços associados a posto + tipo de combustível + data
- [ ] Unicidade por (station, fuelType, collectionDate)
- [ ] Data de coleta exibida em todo preço
- [ ] `data_source` identifica origem (ANP_IMPORT / MANUAL_ADMIN)
- [ ] Comparação ordena por preço e/ou distância
- [ ] Fórmulas de custo/km e custo efetivo calculam corretamente
- [ ] Testes passam

---

## Commit Sugerido

```
feat: add fuel price management and comparison endpoint with cost formulas
```
