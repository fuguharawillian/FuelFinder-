package com.fuelfinder.modules.recommendation.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.price.entity.FuelPrice;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.recommendation.dto.RecommendationResponseDTO;
import com.fuelfinder.modules.recommendation.dto.StationRecommendationDTO;
import com.fuelfinder.modules.recommendation.dto.VehicleSummaryDTO;
import com.fuelfinder.modules.station.dto.StationResponseDTO;
import com.fuelfinder.modules.station.service.StationService;
import com.fuelfinder.modules.vehicle.dto.ConsumptionDTO;
import com.fuelfinder.modules.vehicle.dto.TankCapacityDTO;
import com.fuelfinder.modules.vehicle.dto.VehicleResponseDTO;
import com.fuelfinder.modules.vehicle.entity.ConsumptionUnit;
import com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted;
import com.fuelfinder.modules.vehicle.entity.VolumeUnit;
import com.fuelfinder.modules.vehicle.service.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int COST_PER_KM_SCALE = 4;
    private static final int COMPARISON_SCALE = 8;
    private static final int CURRENCY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final Comparator<Candidate> COST_PER_KM_ORDER =
            Comparator.comparing(Candidate::costPerKm)
                    .thenComparing(candidate -> candidate.fuelType().getCode())
                    .thenComparing(candidate -> candidate.recommendation().stationId());

    private static final Set<String> GASOLINE_CODES =
            Set.of("GASOLINE_REGULAR", "GASOLINE_PREMIUM");
    private static final Set<String> DIESEL_CODES = Set.of("DIESEL_S10", "DIESEL_S500");
    private static final Map<String, Function<VehicleResponseDTO, ConsumptionDTO>>
            CONSUMPTION_BY_FUEL_CODE = Map.of(
                    "GASOLINE_REGULAR", VehicleResponseDTO::averageConsumptionGasoline,
                    "GASOLINE_PREMIUM", VehicleResponseDTO::averageConsumptionGasoline,
                    "ETHANOL", VehicleResponseDTO::averageConsumptionEthanol,
                    "DIESEL_S10", VehicleResponseDTO::averageConsumptionDiesel,
                    "DIESEL_S500", VehicleResponseDTO::averageConsumptionDiesel,
                    "CNG", VehicleResponseDTO::averageConsumptionCng);
    private static final Map<String, String> FUEL_NAMES = Map.of(
            "ETHANOL", "etanol",
            "GASOLINE_REGULAR", "gasolina comum",
            "GASOLINE_PREMIUM", "gasolina premium",
            "DIESEL_S10", "diesel S10",
            "DIESEL_S500", "diesel S500",
            "CNG", "GNV");

    private final VehicleService vehicleService;
    private final StationService stationService;
    private final FuelPriceRepository fuelPriceRepository;

    public RecommendationService(
            VehicleService vehicleService,
            StationService stationService,
            FuelPriceRepository fuelPriceRepository) {
        this.vehicleService = vehicleService;
        this.stationService = stationService;
        this.fuelPriceRepository = fuelPriceRepository;
    }

    public RecommendationResponseDTO recommend(
            UUID vehicleId,
            UUID userId,
            double latitude,
            double longitude,
            double radiusKm) {
        VehicleResponseDTO vehicle = vehicleService.findByIdAndUser(vehicleId, userId);
        List<StationResponseDTO> nearbyStations =
                stationService.search(latitude, longitude, radiusKm, null);
        if (nearbyStations.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Nenhum posto encontrado no raio de " + radiusKm + " km.");
        }

        Map<UUID, StationResponseDTO> stationsById = nearbyStations.stream()
                .collect(Collectors.toMap(StationResponseDTO::id, Function.identity()));
        List<String> fuelCodes = fuelCodesFor(vehicle.fuelTypeAccepted());
        List<Candidate> candidates = fuelPriceRepository.findLatestForStations(
                        nearbyStations.stream().map(StationResponseDTO::id).toList(), null)
                .stream()
                .filter(price -> fuelCodes.contains(price.getFuelType().getCode()))
                .filter(price -> Boolean.TRUE.equals(price.getFuelType().getActive()))
                .map(price -> createCandidate(price, stationsById, vehicle))
                .toList();
        if (candidates.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Nenhum preço atual para os combustíveis aceitos pelo veículo.");
        }

        RecommendationChoice choice = chooseFuel(vehicle.fuelTypeAccepted(), candidates);
        List<StationRecommendationDTO> options = candidates.stream()
                .filter(candidate -> candidate.fuelType().getCode().equals(choice.fuelCode()))
                .map(Candidate::recommendation)
                .sorted(Comparator
                        .comparing(RecommendationService::effectiveCost)
                        .thenComparing(StationRecommendationDTO::distanceKm)
                        .thenComparing(StationRecommendationDTO::stationId))
                .toList();

        return new RecommendationResponseDTO(
                new VehicleSummaryDTO(vehicle.nickname(), vehicle.fuelTypeAccepted()),
                choice.fuelCode(),
                buildExplanation(vehicle, choice.fuelCode(), choice.parityPercentage(), options.get(0)),
                choice.parityPercentage(),
                options);
    }

    private Candidate createCandidate(
            FuelPrice price,
            Map<UUID, StationResponseDTO> stationsById,
            VehicleResponseDTO vehicle) {
        StationResponseDTO station = stationsById.get(price.getStation().getId());
        if (station == null || station.distanceKm() == null) {
            throw new IllegalStateException(
                    "O preço retornado não corresponde a um posto elegível na busca geográfica.");
        }

        FuelType fuelType = price.getFuelType();
        String fuelCode = fuelType.getCode();
        ConsumptionDTO consumption = consumptionFor(fuelCode, vehicle);
        VolumeUnit expectedVolumeUnit = volumeUnitFor(fuelCode);
        ConsumptionUnit expectedConsumptionUnit = consumptionUnitFor(fuelCode);
        TankCapacityDTO tankCapacity = vehicle.tankCapacity();
        if (consumption == null || tankCapacity == null) {
            throw new BusinessException(
                    "O veículo não possui consumo ou capacidade para o combustível "
                            + fuelCode + ".");
        }
        if (consumption.unit() != expectedConsumptionUnit
                || tankCapacity.unit() != expectedVolumeUnit) {
            throw new BusinessException(
                    "As unidades de consumo e capacidade do veículo não correspondem ao combustível "
                            + fuelCode + ".");
        }
        validateFuelPriceUnit(fuelType);

        BigDecimal costPerKm = price.getSaleValue()
                .divide(consumption.value(), COMPARISON_SCALE, RoundingMode.HALF_UP);
        BigDecimal fullTankCost = tankCapacity.value()
                .multiply(price.getSaleValue())
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        BigDecimal roundTripCost = costPerKm
                .multiply(BigDecimal.valueOf(station.distanceKm() * 2))
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        StationRecommendationDTO recommendation = new StationRecommendationDTO(
                station.id(),
                station.tradeName() == null || station.tradeName().isBlank()
                        ? station.corporateName() : station.tradeName(),
                station.brand(),
                fuelCode,
                price.getSaleValue(),
                fuelType.getUnitOfMeasure(),
                station.distanceKm(),
                costPerKm.setScale(COST_PER_KM_SCALE, RoundingMode.HALF_UP),
                fullTankCost,
                roundTripCost);
        return new Candidate(fuelType, recommendation, costPerKm);
    }

    private RecommendationChoice chooseFuel(
            String acceptedFuelType,
            List<Candidate> candidates) {
        if (FuelTypeAccepted.FLEX.name().equals(acceptedFuelType)) {
            return chooseFlexFuel(candidates);
        }
        Candidate best = candidates.stream()
                .min(COST_PER_KM_ORDER)
                .orElseThrow();
        return new RecommendationChoice(best.fuelType().getCode(), null);
    }

    private RecommendationChoice chooseFlexFuel(List<Candidate> candidates) {
        Candidate bestEthanol = candidates.stream()
                .filter(candidate -> "ETHANOL".equals(candidate.fuelType().getCode()))
                .min(COST_PER_KM_ORDER)
                .orElse(null);
        Candidate bestGasoline = candidates.stream()
                .filter(candidate -> GASOLINE_CODES.contains(candidate.fuelType().getCode()))
                .min(COST_PER_KM_ORDER)
                .orElse(null);
        if (bestEthanol == null) {
            return new RecommendationChoice(bestGasoline.fuelType().getCode(), null);
        }
        if (bestGasoline == null) {
            return new RecommendationChoice(bestEthanol.fuelType().getCode(), null);
        }

        BigDecimal parity = bestEthanol.recommendation().price()
                .divide(bestGasoline.recommendation().price(), 4, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
        Candidate best = bestEthanol.costPerKm().compareTo(bestGasoline.costPerKm()) < 0
                ? bestEthanol : bestGasoline;
        return new RecommendationChoice(best.fuelType().getCode(), parity);
    }

    private List<String> fuelCodesFor(String acceptedFuelType) {
        FuelTypeAccepted accepted;
        try {
            accepted = FuelTypeAccepted.valueOf(acceptedFuelType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("Tipo de combustível do veículo inválido.");
        }
        return switch (accepted) {
            case FLEX -> List.of("ETHANOL", "GASOLINE_REGULAR", "GASOLINE_PREMIUM");
            case GASOLINE -> new ArrayList<>(GASOLINE_CODES);
            case ETHANOL -> List.of("ETHANOL");
            case DIESEL -> new ArrayList<>(DIESEL_CODES);
            case CNG -> List.of("CNG");
        };
    }

    private ConsumptionDTO consumptionFor(String fuelCode, VehicleResponseDTO vehicle) {
        Function<VehicleResponseDTO, ConsumptionDTO> consumptionExtractor =
                Objects.requireNonNull(
                        CONSUMPTION_BY_FUEL_CODE.get(fuelCode),
                        "Unsupported catalog fuel type: " + fuelCode);
        return consumptionExtractor.apply(vehicle);
    }

    private ConsumptionUnit consumptionUnitFor(String fuelCode) {
        return "CNG".equals(fuelCode)
                ? ConsumptionUnit.KM_PER_CUBIC_METER : ConsumptionUnit.KM_PER_LITER;
    }

    private VolumeUnit volumeUnitFor(String fuelCode) {
        return "CNG".equals(fuelCode) ? VolumeUnit.CUBIC_METER : VolumeUnit.LITER;
    }

    private void validateFuelPriceUnit(FuelType fuelType) {
        String expectedUnit = "CNG".equals(fuelType.getCode()) ? "R$/m³" : "R$/litro";
        if (!expectedUnit.equals(fuelType.getUnitOfMeasure())) {
            throw new BusinessException(
                    "A unidade de preço do combustível " + fuelType.getCode()
                            + " deve ser " + expectedUnit + ".");
        }
    }

    private String buildExplanation(
            VehicleResponseDTO vehicle,
            String fuelCode,
            BigDecimal parity,
            StationRecommendationDTO best) {
        String explanation = "Para o veículo " + vehicle.nickname()
                + ", " + fuelName(fuelCode)
                + " tem custo de R$ " + best.costPerKm() + "/km no posto "
                + best.stationName() + ".";
        if (parity != null) {
            return explanation + " Paridade etanol/gasolina: " + parity + "%.";
        }
        return explanation;
    }

    private String fuelName(String fuelCode) {
        return Objects.requireNonNull(
                FUEL_NAMES.get(fuelCode),
                "Unsupported catalog fuel type: " + fuelCode);
    }

    private static BigDecimal effectiveCost(StationRecommendationDTO option) {
        return option.estimatedFullTankCost().add(option.estimatedRoundTripCost());
    }

    private record Candidate(
            FuelType fuelType,
            StationRecommendationDTO recommendation,
            BigDecimal costPerKm) {
    }

    private record RecommendationChoice(String fuelCode, BigDecimal parityPercentage) {
    }
}
