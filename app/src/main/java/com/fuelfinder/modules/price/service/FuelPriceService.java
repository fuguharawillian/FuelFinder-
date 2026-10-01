package com.fuelfinder.modules.price.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.DuplicateResourceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.service.InvalidCredentialsException;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.fuel.repository.FuelTypeRepository;
import com.fuelfinder.modules.price.dto.CompareResultDTO;
import com.fuelfinder.modules.price.dto.CreateFuelPriceRequestDTO;
import com.fuelfinder.modules.price.dto.FuelPriceResponseDTO;
import com.fuelfinder.modules.price.dto.UpdateFuelPriceRequestDTO;
import com.fuelfinder.modules.price.entity.DataSource;
import com.fuelfinder.modules.price.entity.FuelPrice;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.dto.StationResponseDTO;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.entity.StationStatus;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.station.service.StationService;
import com.fuelfinder.modules.vehicle.dto.ConsumptionDTO;
import com.fuelfinder.modules.vehicle.dto.TankCapacityDTO;
import com.fuelfinder.modules.vehicle.dto.VehicleResponseDTO;
import com.fuelfinder.modules.vehicle.entity.FuelConsumption;
import com.fuelfinder.modules.vehicle.entity.TankCapacity;
import com.fuelfinder.modules.vehicle.entity.Vehicle;
import com.fuelfinder.modules.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FuelPriceService {

    private final FuelPriceRepository fuelPriceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final StationRepository stationRepository;
    private final StationService stationService;
    private final VehicleRepository vehicleRepository;

    public FuelPriceService(
            FuelPriceRepository fuelPriceRepository,
            FuelTypeRepository fuelTypeRepository,
            StationRepository stationRepository,
            StationService stationService,
            VehicleRepository vehicleRepository) {
        this.fuelPriceRepository = fuelPriceRepository;
        this.fuelTypeRepository = fuelTypeRepository;
        this.stationRepository = stationRepository;
        this.stationService = stationService;
        this.vehicleRepository = vehicleRepository;
    }

    public List<FuelPriceResponseDTO> getLatestPrices(UUID stationId) {
        stationRepository.findByIdAndStatus(
                        stationId, StationStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));
        return fuelPriceRepository.findLatestByStationId(stationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FuelPriceResponseDTO create(UUID stationId, CreateFuelPriceRequestDTO request) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Posto não encontrado."));
        if (station.getStatus() != StationStatus.ACTIVE) {
            throw new BusinessException("Não é possível cadastrar preços em um posto inativo.");
        }
        FuelType fuelType = findActiveFuelType(request.fuelTypeCode());
        ensureDateIsUnique(stationId, fuelType, request.collectionDate(), null);

        FuelPrice price = new FuelPrice(
                station, fuelType, request.saleValue(), request.collectionDate(),
                DataSource.MANUAL_ADMIN);
        return toResponse(fuelPriceRepository.save(price));
    }

    @Transactional
    public FuelPriceResponseDTO update(
            UUID stationId,
            UUID priceId,
            UpdateFuelPriceRequestDTO request) {
        if (request.saleValue() == null && request.collectionDate() == null) {
            throw new BusinessException("Informe ao menos o preço ou a data de coleta.");
        }
        FuelPrice price = fuelPriceRepository.findByIdAndStationId(priceId, stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Preço não encontrado."));
        BigDecimal updatedValue = request.saleValue() == null
                ? price.getSaleValue()
                : request.saleValue();
        var updatedDate = request.collectionDate() == null
                ? price.getCollectionDate()
                : request.collectionDate();
        ensureDateIsUnique(stationId, price.getFuelType(), updatedDate, priceId);
        price.setSaleValue(updatedValue);
        price.setCollectionDate(updatedDate);
        return toResponse(fuelPriceRepository.save(price));
    }

    public List<CompareResultDTO> compare(
            double latitude,
            double longitude,
            double radiusKm,
            String fuelTypeCode,
            UUID vehicleId,
            UUID userId,
            String sortBy) {
        List<StationResponseDTO> nearby = stationService.search(
                latitude, longitude, radiusKm, null);
        if (nearby.isEmpty()) {
            return List.of();
        }
        Map<UUID, Double> distances = nearby.stream().collect(Collectors.toMap(
                StationResponseDTO::id, StationResponseDTO::distanceKm));
        VehicleResponseDTO vehicle = findVehicle(vehicleId, userId);
        if (fuelTypeCode != null) {
            findActiveFuelType(fuelTypeCode);
        }
        if (!"PRICE".equalsIgnoreCase(sortBy) && !"DISTANCE".equalsIgnoreCase(sortBy)) {
            throw new BusinessException("A ordenação deve ser PRICE ou DISTANCE.");
        }
        List<CompareResultDTO> results = fuelPriceRepository.findLatestForStations(
                        nearby.stream().map(StationResponseDTO::id).toList(), fuelTypeCode)
                .stream()
                .map(price -> toCompareResult(price, distances.get(price.getStation().getId()), vehicle))
                .toList();
        Comparator<CompareResultDTO> ordering = "DISTANCE".equalsIgnoreCase(sortBy)
                ? Comparator.comparing(CompareResultDTO::distanceKm)
                    .thenComparing(CompareResultDTO::price)
                : Comparator.comparing(CompareResultDTO::price)
                    .thenComparing(CompareResultDTO::distanceKm);
        return results.stream().sorted(ordering).toList();
    }

    public BigDecimal calculateCostPerKm(BigDecimal price, BigDecimal consumption) {
        if (consumption == null || consumption.signum() <= 0) {
            return null;
        }
        return price.divide(consumption, 4, RoundingMode.HALF_UP);
    }

    private VehicleResponseDTO findVehicle(UUID vehicleId, UUID userId) {
        if (vehicleId == null) {
            return null;
        }
        if (userId == null) {
            throw new InvalidCredentialsException("Autentique-se para usar os dados do veículo.");
        }
        return vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .map(this::toVehicleResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo não encontrado."));
    }

    private VehicleResponseDTO toVehicleResponse(Vehicle vehicle) {
        return new VehicleResponseDTO(
                vehicle.getId(),
                vehicle.getNickname(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYearManufacture(),
                vehicle.getFuelTypeAccepted().name(),
                toTankCapacityDTO(vehicle.getTankCapacity()),
                toConsumptionDTO(vehicle.getAvgConsumptionGasoline()),
                toConsumptionDTO(vehicle.getAvgConsumptionEthanol()),
                toConsumptionDTO(vehicle.getAvgConsumptionDiesel()),
                toConsumptionDTO(vehicle.getAvgConsumptionCng()));
    }

    private CompareResultDTO toCompareResult(
            FuelPrice price,
            Double distanceKm,
            VehicleResponseDTO vehicle) {
        BigDecimal consumption = findConsumption(price.getFuelType().getCode(), vehicle);
        BigDecimal costPerKm = calculateCostPerKm(price.getSaleValue(), consumption);
        BigDecimal tankCost = null;
        BigDecimal estimatedRange = null;
        BigDecimal roundTripCost = null;
        if (vehicle != null && costPerKm != null) {
            TankCapacityDTO capacity = vehicle.tankCapacity();
            if (capacity != null) {
                tankCost = capacity.value().multiply(price.getSaleValue())
                        .setScale(2, RoundingMode.HALF_UP);
                estimatedRange = capacity.value().multiply(consumption)
                        .setScale(2, RoundingMode.HALF_UP);
                roundTripCost = costPerKm.multiply(BigDecimal.valueOf(distanceKm * 2))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        return new CompareResultDTO(
                price.getStation().getId(),
                price.getStation().getTradeName() == null
                        ? price.getStation().getCorporateName()
                        : price.getStation().getTradeName(),
                price.getStation().getBrand(),
                price.getFuelType().getCode(),
                price.getSaleValue(),
                price.getFuelType().getUnitOfMeasure(),
                price.getCollectionDate(),
                distanceKm,
                costPerKm,
                tankCost,
                estimatedRange,
                roundTripCost);
    }

    private BigDecimal findConsumption(String fuelTypeCode, VehicleResponseDTO vehicle) {
        if (vehicle == null) {
            return null;
        }
        ConsumptionDTO consumption = switch (fuelTypeCode) {
            case "GASOLINE_REGULAR", "GASOLINE_PREMIUM" -> vehicle.averageConsumptionGasoline();
            case "ETHANOL" -> vehicle.averageConsumptionEthanol();
            case "DIESEL_S10", "DIESEL_S500" -> vehicle.averageConsumptionDiesel();
            case "CNG" -> vehicle.averageConsumptionCng();
            default -> null;
        };
        return consumption == null ? null : consumption.value();
    }

    private TankCapacityDTO toTankCapacityDTO(TankCapacity capacity) {
        return capacity == null ? null
                : new TankCapacityDTO(capacity.getValue(), capacity.getUnit());
    }

    private ConsumptionDTO toConsumptionDTO(FuelConsumption consumption) {
        return consumption == null ? null
                : new ConsumptionDTO(consumption.getValue(), consumption.getUnit());
    }

    private FuelType findActiveFuelType(String code) {
        FuelType fuelType = fuelTypeRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipo de combustível não encontrado."));
        if (!Boolean.TRUE.equals(fuelType.getActive())) {
            throw new BusinessException("O tipo de combustível está inativo.");
        }
        return fuelType;
    }

    private void ensureDateIsUnique(
            UUID stationId,
            FuelType fuelType,
            java.time.LocalDate date,
            UUID excludedPriceId) {
        fuelPriceRepository.findByStationIdAndFuelTypeIdAndCollectionDate(
                        stationId, fuelType.getId(), date)
                .filter(existing -> !existing.getId().equals(excludedPriceId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Já existe um preço para este combustível nesta data.");
                });
    }

    private FuelPriceResponseDTO toResponse(FuelPrice price) {
        return new FuelPriceResponseDTO(
                price.getId(),
                price.getFuelType().getCode(),
                price.getFuelType().getName(),
                price.getSaleValue(),
                price.getCollectionDate(),
                price.getDataSource().name(),
                price.getFuelType().getUnitOfMeasure());
    }
}
