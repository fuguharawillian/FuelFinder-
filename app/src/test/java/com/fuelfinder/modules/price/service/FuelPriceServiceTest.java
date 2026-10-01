package com.fuelfinder.modules.price.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.DuplicateResourceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.service.InvalidCredentialsException;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.fuel.repository.FuelTypeRepository;
import com.fuelfinder.modules.price.dto.CompareResultDTO;
import com.fuelfinder.modules.price.dto.UpdateFuelPriceRequestDTO;
import com.fuelfinder.modules.price.entity.DataSource;
import com.fuelfinder.modules.price.entity.FuelPrice;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.dto.StationResponseDTO;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.entity.StationStatus;
import com.fuelfinder.modules.station.repository.StationRepository;
import com.fuelfinder.modules.station.service.StationService;
import com.fuelfinder.modules.vehicle.entity.ConsumptionUnit;
import com.fuelfinder.modules.vehicle.entity.FuelConsumption;
import com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted;
import com.fuelfinder.modules.vehicle.entity.TankCapacity;
import com.fuelfinder.modules.vehicle.entity.Vehicle;
import com.fuelfinder.modules.vehicle.entity.VolumeUnit;
import com.fuelfinder.modules.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuelPriceServiceTest {

    @Mock
    private FuelPriceRepository fuelPriceRepository;
    @Mock
    private FuelTypeRepository fuelTypeRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private StationService stationService;
    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private FuelPriceService service;

    private final UUID stationId = UUID.randomUUID();
    private final UUID fuelTypeId = UUID.randomUUID();
    private final UUID priceId = UUID.randomUUID();
    private Station station;
    private FuelType fuelType;

    @BeforeEach
    void setUp() {
        station = new Station(
                "12345678901234", "Corporate", "Trade", "Brand",
                "Street", "1", "Center", "City", "SP", "00000-000",
                BigDecimal.ZERO, BigDecimal.ZERO);
        station.setId(stationId);
        fuelType = fuelType("GASOLINE_REGULAR", true);
        fuelType.setId(fuelTypeId);
    }

    @Test
    void requiresActiveStationForReadingAndCreatingPrices() {
        when(stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getLatestPrices(stationId));

        when(stationRepository.findById(stationId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(stationId, createRequest()));

        station.setStatus(StationStatus.INACTIVE);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        assertThrows(BusinessException.class,
                () -> service.create(stationId, createRequest()));
    }

    @Test
    void createsOnlyActiveFuelTypesAndRejectsDuplicateCollectionDates() {
        station.setStatus(StationStatus.ACTIVE);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findByCode("GASOLINE_REGULAR")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(stationId, createRequest()));

        FuelType inactive = fuelType("GASOLINE_REGULAR", false);
        when(fuelTypeRepository.findByCode("GASOLINE_REGULAR")).thenReturn(Optional.of(inactive));
        assertThrows(BusinessException.class,
                () -> service.create(stationId, createRequest()));

        when(fuelTypeRepository.findByCode("GASOLINE_REGULAR")).thenReturn(Optional.of(fuelType));
        FuelPrice duplicate = price(priceId, station, fuelType, "5.00", LocalDate.now());
        when(fuelPriceRepository.findByStationIdAndFuelTypeIdAndCollectionDate(
                stationId, fuelTypeId, LocalDate.now())).thenReturn(Optional.of(duplicate));
        assertThrows(DuplicateResourceException.class,
                () -> service.create(stationId, createRequest()));
    }

    @Test
    void updatesPricePartiallyAndRejectsEmptyMissingOrDuplicateUpdates() {
        assertThrows(BusinessException.class,
                () -> service.update(stationId, priceId, new UpdateFuelPriceRequestDTO(null, null)));
        when(fuelPriceRepository.findByIdAndStationId(priceId, stationId))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(
                stationId, priceId, new UpdateFuelPriceRequestDTO(new BigDecimal("5.20"), null)));

        FuelPrice existing = price(priceId, station, fuelType, "5.00", LocalDate.now());
        when(fuelPriceRepository.findByIdAndStationId(priceId, stationId))
                .thenReturn(Optional.of(existing));
        when(fuelPriceRepository.findByStationIdAndFuelTypeIdAndCollectionDate(
                stationId, fuelTypeId, LocalDate.now())).thenReturn(Optional.of(existing));
        when(fuelPriceRepository.save(existing)).thenReturn(existing);
        var unchangedDate = service.update(stationId, priceId,
                new UpdateFuelPriceRequestDTO(new BigDecimal("5.20"), null));
        assertEquals(new BigDecimal("5.20"), unchangedDate.saleValue());

        FuelPrice collision = price(UUID.randomUUID(), station, fuelType, "5.30",
                LocalDate.of(2026, 10, 1));
        when(fuelPriceRepository.findByStationIdAndFuelTypeIdAndCollectionDate(
                stationId, fuelTypeId, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(collision));
        assertThrows(DuplicateResourceException.class, () -> service.update(
                stationId, priceId,
                new UpdateFuelPriceRequestDTO(null, LocalDate.of(2026, 10, 1))));
    }

    @Test
    void comparesLatestPricesByDistanceOrPriceAndCalculatesCompatibleFuelMeasures() {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID secondStationId = UUID.randomUUID();
        StationResponseDTO near = stationResponse(stationId, "Near", 1.0);
        StationResponseDTO far = stationResponse(secondStationId, "Far", 2.0);
        when(stationService.search(0.0, 0.0, 5.0, null)).thenReturn(List.of(near, far));
        when(fuelTypeRepository.findByCode("GASOLINE_REGULAR")).thenReturn(Optional.of(fuelType));
        Vehicle vehicle = new Vehicle(
                userId, "Car", "Brand", "Model", 2020, FuelTypeAccepted.FLEX,
                new TankCapacity(new BigDecimal("50"), VolumeUnit.LITER),
                new FuelConsumption(new BigDecimal("10"), ConsumptionUnit.KM_PER_LITER),
                new FuelConsumption(new BigDecimal("8"), ConsumptionUnit.KM_PER_LITER),
                new FuelConsumption(new BigDecimal("7"), ConsumptionUnit.KM_PER_LITER),
                new FuelConsumption(new BigDecimal("13"), ConsumptionUnit.KM_PER_CUBIC_METER));
        vehicle.setId(vehicleId);
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId))
                .thenReturn(Optional.of(vehicle));

        List<FuelPrice> prices = List.of(
                price(UUID.randomUUID(), station, fuelType, "5.00", LocalDate.now()),
                price(UUID.randomUUID(), stationWithId(secondStationId), fuelType,
                        "4.50", LocalDate.now()));
        when(fuelPriceRepository.findLatestForStations(
                List.of(stationId, secondStationId), "GASOLINE_REGULAR"))
                .thenReturn(prices);
        when(fuelPriceRepository.findLatestForStations(
                List.of(stationId, secondStationId), null))
                .thenReturn(prices);

        List<CompareResultDTO> byPrice = service.compare(
                0, 0, 5, "GASOLINE_REGULAR", vehicleId, userId, "PRICE");
        assertEquals(secondStationId, byPrice.get(0).stationId());
        assertEquals(new BigDecimal("0.4500"), byPrice.get(0).costPerKm());
        assertEquals(new BigDecimal("225.00"), byPrice.get(0).estimatedFullTankCost());
        assertEquals(new BigDecimal("500.00"), byPrice.get(0).estimatedRange());
        assertEquals(new BigDecimal("1.80"), byPrice.get(0).estimatedRoundTripCost());

        List<CompareResultDTO> byDistance = service.compare(
                0, 0, 5, null, vehicleId, userId, "DISTANCE");
        assertEquals(stationId, byDistance.get(0).stationId());
    }

    @Test
    void handlesEmptySearchInvalidFuelAndVehicleOwnershipFailures() {
        when(stationService.search(0.0, 0.0, 5.0, null)).thenReturn(List.of());
        assertEquals(List.of(), service.compare(0, 0, 5, null, null, null, "PRICE"));
        verify(fuelPriceRepository, never()).findLatestForStations(any(), any());

        when(stationService.search(0.0, 0.0, 5.0, null))
                .thenReturn(List.of(stationResponse(stationId, "Near", 1.0)));
        assertThrows(BusinessException.class,
                () -> service.compare(0, 0, 5, null, null, null, "INVALID"));
        when(fuelTypeRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.compare(0, 0, 5, "UNKNOWN", null, null, "PRICE"));
        assertThrows(InvalidCredentialsException.class,
                () -> service.compare(0, 0, 5, null, priceId, null, "PRICE"));
        when(vehicleRepository.findByIdAndUserId(priceId, stationId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.compare(0, 0, 5, null, priceId, stationId, "PRICE"));
    }

    @Test
    void calculatesOnlyAvailableFuelMetrics() {
        assertNull(service.calculateCostPerKm(new BigDecimal("5"), null));
        assertNull(service.calculateCostPerKm(new BigDecimal("5"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.5000"),
                service.calculateCostPerKm(new BigDecimal("5"), new BigDecimal("10")));
    }

    @Test
    void mapsLiquidAndCngCatalogCodesToTheirConsumptionAndVolumeUnits() {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        station.setTradeName(null);
        when(stationService.search(0.0, 0.0, 5.0, null))
                .thenReturn(List.of(stationResponse(stationId, "Corporate", 1.0)));

        assertCatalogComparison(vehicleId, userId, "ETHANOL", FuelTypeAccepted.ETHANOL,
                null, new FuelConsumption(new BigDecimal("8"), ConsumptionUnit.KM_PER_LITER),
                null, null);
        assertCatalogComparison(vehicleId, userId, "DIESEL_S10", FuelTypeAccepted.DIESEL,
                null, null,
                new FuelConsumption(new BigDecimal("7"), ConsumptionUnit.KM_PER_LITER), null);
        assertCatalogComparison(vehicleId, userId, "CNG", FuelTypeAccepted.CNG,
                null, null, null,
                new FuelConsumption(new BigDecimal("13"), ConsumptionUnit.KM_PER_CUBIC_METER));
    }

    @Test
    void omitsVehicleMetricsWhenFuelIsUnknownOrTankCapacityIsMissing() {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(stationService.search(0.0, 0.0, 5.0, null))
                .thenReturn(List.of(stationResponse(stationId, "Corporate", 1.0)));
        Vehicle vehicleWithoutTank = new Vehicle(
                userId, "Car", "Brand", "Model", 2020, FuelTypeAccepted.GASOLINE,
                null,
                new FuelConsumption(new BigDecimal("10"), ConsumptionUnit.KM_PER_LITER),
                null, null, null);
        vehicleWithoutTank.setId(vehicleId);
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId))
                .thenReturn(Optional.of(vehicleWithoutTank));

        FuelType unknown = fuelType("UNLISTED", true);
        FuelPrice unknownPrice = price(
                priceId, station, unknown, "5.00", LocalDate.now());
        when(fuelPriceRepository.findLatestForStations(List.of(stationId), null))
                .thenReturn(List.of(unknownPrice));
        CompareResultDTO unknownResult = service.compare(
                0, 0, 5, null, vehicleId, userId, "PRICE").get(0);
        assertNull(unknownResult.costPerKm());

        FuelPrice gasolinePrice = price(
                priceId, station, fuelType, "5.00", LocalDate.now());
        when(fuelTypeRepository.findByCode("GASOLINE_REGULAR"))
                .thenReturn(Optional.of(fuelType));
        when(fuelPriceRepository.findLatestForStations(
                List.of(stationId), "GASOLINE_REGULAR"))
                .thenReturn(List.of(gasolinePrice));
        CompareResultDTO gasolineResult = service.compare(
                0, 0, 5, "GASOLINE_REGULAR", vehicleId, userId, "PRICE").get(0);
        assertEquals(new BigDecimal("0.5000"), gasolineResult.costPerKm());
        assertNull(gasolineResult.estimatedFullTankCost());
    }

    private void assertCatalogComparison(
            UUID vehicleId,
            UUID userId,
            String code,
            com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted vehicleFuel,
            FuelConsumption gasoline,
            FuelConsumption ethanol,
            FuelConsumption diesel,
            FuelConsumption cng) {
        FuelType type = fuelType(code, true);
        when(fuelTypeRepository.findByCode(code)).thenReturn(Optional.of(type));
        Vehicle vehicle = new Vehicle(
                userId,
                "Car",
                "Brand",
                "Model",
                2020,
                vehicleFuel,
                new TankCapacity(
                        new BigDecimal(vehicleFuel == com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted.CNG
                                ? "12.5" : "50"),
                        vehicleFuel == com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted.CNG
                                ? VolumeUnit.CUBIC_METER : VolumeUnit.LITER),
                gasoline,
                ethanol,
                diesel,
                cng);
        vehicle.setId(vehicleId);
        when(vehicleRepository.findByIdAndUserId(vehicleId, userId))
                .thenReturn(Optional.of(vehicle));
        when(fuelPriceRepository.findLatestForStations(List.of(stationId), code))
                .thenReturn(List.of(price(
                        UUID.randomUUID(), station, type, "4.00", LocalDate.now())));

        CompareResultDTO result = service.compare(
                0, 0, 5, code, vehicleId, userId, "PRICE").get(0);

        BigDecimal expectedConsumption = switch (code) {
            case "ETHANOL" -> new BigDecimal("8");
            case "DIESEL_S10" -> new BigDecimal("7");
            default -> new BigDecimal("13");
        };
        assertEquals(new BigDecimal("4.00").divide(
                expectedConsumption,
                4, java.math.RoundingMode.HALF_UP), result.costPerKm());
    }

    private com.fuelfinder.modules.price.dto.CreateFuelPriceRequestDTO createRequest() {
        return new com.fuelfinder.modules.price.dto.CreateFuelPriceRequestDTO(
                "GASOLINE_REGULAR", new BigDecimal("5.50"), LocalDate.now());
    }

    private FuelType fuelType(String code, boolean active) {
        FuelType result = new FuelType();
        result.setId(fuelTypeId);
        result.setCode(code);
        result.setName(code);
        result.setUnitOfMeasure("R$/litro");
        result.setActive(active);
        return result;
    }

    private FuelPrice price(
            UUID id, Station owner, FuelType type, String value, LocalDate date) {
        FuelPrice result = new FuelPrice(
                owner, type, new BigDecimal(value), date, DataSource.MANUAL_ADMIN);
        result.setId(id);
        return result;
    }

    private Station stationWithId(UUID id) {
        Station result = new Station(
                "12345678901234", "Far Corporate", "Far", "Brand",
                "Street", "1", "Center", "City", "SP", "00000-000",
                BigDecimal.ZERO, BigDecimal.ZERO);
        result.setId(id);
        return result;
    }

    private StationResponseDTO stationResponse(UUID id, String name, double distance) {
        return new StationResponseDTO(
                id, "12345678901234", name + " Corporate", name, "Brand",
                "Street", "City", "SP", "00000-000", BigDecimal.ZERO, BigDecimal.ZERO,
                distance, BigDecimal.ZERO, 0, StationStatus.ACTIVE.name());
    }
}
