package com.fuelfinder.modules.recommendation.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.price.entity.FuelPrice;
import com.fuelfinder.modules.price.repository.FuelPriceRepository;
import com.fuelfinder.modules.station.dto.StationResponseDTO;
import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.service.StationService;
import com.fuelfinder.modules.vehicle.dto.ConsumptionDTO;
import com.fuelfinder.modules.vehicle.dto.TankCapacityDTO;
import com.fuelfinder.modules.vehicle.dto.VehicleResponseDTO;
import com.fuelfinder.modules.vehicle.entity.ConsumptionUnit;
import com.fuelfinder.modules.vehicle.entity.VolumeUnit;
import com.fuelfinder.modules.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FIRST_STATION_ID = UUID.randomUUID();
    private static final UUID SECOND_STATION_ID = UUID.randomUUID();

    @Mock
    private VehicleService vehicleService;
    @Mock
    private StationService stationService;
    @Mock
    private FuelPriceRepository fuelPriceRepository;

    @InjectMocks
    private RecommendationService service;

    private VehicleResponseDTO flexVehicle;
    private StationResponseDTO firstStation;
    private StationResponseDTO secondStation;

    @BeforeEach
    void setUp() {
        flexVehicle = vehicle("FLEX", "54", VolumeUnit.LITER,
                "13.5", "9.2", null, null);
        firstStation = station(FIRST_STATION_ID, " ", "2.45");
        secondStation = station(SECOND_STATION_ID, "Posto Bairro", "0.50");
        when(vehicleService.findByIdAndUser(VEHICLE_ID, USER_ID)).thenReturn(flexVehicle);
        when(stationService.search(0.0, 0.0, 5.0, null))
                .thenReturn(List.of(firstStation, secondStation));
    }

    @Test
    void recommendsEthanolByPersonalizedCostAndOrdersStationsByEffectiveCost() {
        stubPrices(
                price(FIRST_STATION_ID, "ETHANOL", "3.89", "R$/litro", true),
                price(SECOND_STATION_ID, "ETHANOL", "3.89", "R$/litro", true),
                price(FIRST_STATION_ID, "GASOLINE_REGULAR", "5.79", "R$/litro", true));

        var result = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);

        assertEquals("ETHANOL", result.recommendedFuel());
        assertEquals(new BigDecimal("67.18"), result.parityPercentage());
        assertEquals(2, result.topOptions().size());
        assertEquals("Posto Bairro", result.topOptions().get(0).stationName());
        assertEquals("3.89", result.topOptions().get(0).price().toPlainString());
        assertEquals("R$/litro", result.topOptions().get(0).unitOfMeasure());
        assertEquals("0.4228", result.topOptions().get(0).costPerKm().toPlainString());
        assertEquals("210.06",
                result.topOptions().get(0).estimatedFullTankCost().toPlainString());
        assertEquals("0.42",
                result.topOptions().get(0).estimatedRoundTripCost().toPlainString());
        org.junit.jupiter.api.Assertions.assertTrue(
                result.explanation().contains("Paridade etanol/gasolina: 67.18%."));
    }

    @Test
    void recommendsTheCheapestGasolineVariantWhenItBeatsEthanol() {
        stubPrices(
                price(FIRST_STATION_ID, "ETHANOL", "4.50", "R$/litro", true),
                price(FIRST_STATION_ID, "GASOLINE_REGULAR", "6.00", "R$/litro", true),
                price(SECOND_STATION_ID, "GASOLINE_PREMIUM", "5.79", "R$/litro", true));

        var result = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);

        assertEquals("GASOLINE_PREMIUM", result.recommendedFuel());
        assertEquals(1, result.topOptions().size());
        assertEquals("Posto Bairro", result.topOptions().get(0).stationName());
        assertEquals(new BigDecimal("77.72"), result.parityPercentage());
    }

    @Test
    void recommendsGasolineWhenFlexFuelCostsPerKilometerAreEqual() {
        stubPrices(
                price(FIRST_STATION_ID, "ETHANOL", "4.60", "R$/litro", true),
                price(FIRST_STATION_ID, "GASOLINE_REGULAR", "6.75", "R$/litro", true));

        var result = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);

        assertEquals("GASOLINE_REGULAR", result.recommendedFuel());
        assertEquals(new BigDecimal("68.15"), result.parityPercentage());
    }

    @Test
    void flexVehicleFallsBackToWhicheverFuelHasPrices() {
        stubPrices(price(FIRST_STATION_ID, "ETHANOL", "3.50", "R$/litro", true));
        var ethanolOnly = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);
        assertEquals("ETHANOL", ethanolOnly.recommendedFuel());
        assertNull(ethanolOnly.parityPercentage());

        stubPrices(price(FIRST_STATION_ID, "GASOLINE_PREMIUM", "5.00", "R$/litro", true));
        var gasolineOnly = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);
        assertEquals("GASOLINE_PREMIUM", gasolineOnly.recommendedFuel());
        assertNull(gasolineOnly.parityPercentage());
    }

    @Test
    void recommendsSingleFuelVehiclesAcrossTheirAvailableCatalogVariants() {
        stubVehicle(vehicle("GASOLINE", "50", VolumeUnit.LITER,
                "13.5", null, null, null));
        stubPrices(
                price(FIRST_STATION_ID, "GASOLINE_REGULAR", "5.79", "R$/litro", true),
                price(SECOND_STATION_ID, "GASOLINE_PREMIUM", "5.50", "R$/litro", true));
        var gasoline = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);
        assertEquals("GASOLINE_PREMIUM", gasoline.recommendedFuel());
        stubPrices(price(FIRST_STATION_ID, "GASOLINE_REGULAR", "5.79", "R$/litro", true));
        assertEquals("GASOLINE_REGULAR",
                service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5).recommendedFuel());

        stubVehicle(vehicle("DIESEL", "70", VolumeUnit.LITER,
                null, null, "12.0", null));
        stubPrices(
                price(FIRST_STATION_ID, "DIESEL_S10", "6.00", "R$/litro", true),
                price(SECOND_STATION_ID, "DIESEL_S500", "5.80", "R$/litro", true));
        assertEquals("DIESEL_S500",
                service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5).recommendedFuel());
        stubPrices(price(FIRST_STATION_ID, "DIESEL_S10", "6.00", "R$/litro", true));
        assertEquals("DIESEL_S10",
                service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5).recommendedFuel());

        stubVehicle(vehicle("ETHANOL", "45", VolumeUnit.LITER,
                null, "9.0", null, null));
        stubPrices(price(FIRST_STATION_ID, "ETHANOL", "3.89", "R$/litro", true));
        assertEquals("ETHANOL",
                service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5).recommendedFuel());
    }

    @Test
    void calculatesCngRecommendationWithCubicMeterMeasurements() {
        stubVehicle(vehicle("CNG", "0.08", VolumeUnit.CUBIC_METER,
                null, null, null, "18.0"));
        stubPrices(price(FIRST_STATION_ID, "CNG", "4.00", "R$/m³", true));

        var result = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);

        assertEquals("CNG", result.recommendedFuel());
        assertEquals("R$/m³", result.topOptions().get(0).unitOfMeasure());
        assertEquals("0.2222", result.topOptions().get(0).costPerKm().toPlainString());
        assertEquals("0.32",
                result.topOptions().get(0).estimatedFullTankCost().toPlainString());
    }

    @Test
    void reportsMissingStationsPricesAndInvalidVehicleFuelTypes() {
        when(stationService.search(0.0, 0.0, 5.0, null)).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        when(stationService.search(0.0, 0.0, 5.0, null))
                .thenReturn(List.of(firstStation, secondStation));
        stubPrices();
        assertThrows(ResourceNotFoundException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        stubPrices(price(FIRST_STATION_ID, "ETHANOL", "3.50", "R$/litro", false));
        assertThrows(ResourceNotFoundException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        stubVehicle(vehicle("NOT_A_FUEL", "50", VolumeUnit.LITER,
                null, null, null, null));
        assertThrows(BusinessException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));
        stubVehicle(vehicle(null, "50", VolumeUnit.LITER,
                null, null, null, null));
        assertThrows(BusinessException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));
    }

    @Test
    void rejectsIncompatibleVehicleAndFuelPriceUnits() {
        stubVehicle(vehicle("CNG", "50", VolumeUnit.LITER,
                null, null, null, "18.0"));
        stubPrices(price(FIRST_STATION_ID, "CNG", "4.00", "R$/m³", true));
        assertThrows(BusinessException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        stubVehicle(vehicle("FLEX", "50", VolumeUnit.LITER,
                "13.5", "9.2", null, null));
        stubPrices(price(FIRST_STATION_ID, "ETHANOL", "3.50", "R$/m³", true));
        assertThrows(BusinessException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        stubPrices(price(UUID.randomUUID(), "ETHANOL", "3.50", "R$/litro", true));
        assertThrows(IllegalStateException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));
    }

    @Test
    void rejectsMissingVehicleMeasurementsAndStationsWithoutDistance() {
        stubVehicle(new VehicleResponseDTO(
                VEHICLE_ID, "Meu veículo", "Marca", "Modelo", 2020, "ETHANOL",
                null, null, null, null, null));
        stubPrices(price(FIRST_STATION_ID, "ETHANOL", "3.50", "R$/litro", true));
        assertThrows(BusinessException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        stubVehicle(new VehicleResponseDTO(
                VEHICLE_ID, "Meu veículo", "Marca", "Modelo", 2020, "ETHANOL",
                null, null,
                new ConsumptionDTO(new BigDecimal("9.0"), ConsumptionUnit.KM_PER_LITER),
                null, null));
        assertThrows(BusinessException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        stubVehicle(new VehicleResponseDTO(
                VEHICLE_ID, "Meu veículo", "Marca", "Modelo", 2020, "ETHANOL",
                new TankCapacityDTO(new BigDecimal("50"), VolumeUnit.LITER), null,
                new ConsumptionDTO(new BigDecimal("9.0"), ConsumptionUnit.KM_PER_CUBIC_METER),
                null, null));
        assertThrows(BusinessException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        stubVehicle(vehicle("ETHANOL", "50", VolumeUnit.LITER,
                null, "9.0", null, null));
        when(stationService.search(0.0, 0.0, 5.0, null)).thenReturn(List.of(
                new StationResponseDTO(
                        FIRST_STATION_ID, "12345678901234", "Corp", "Trade", "Brand",
                        "Address", "City", "SP", "00000-000", BigDecimal.ZERO, BigDecimal.ZERO,
                        null, BigDecimal.ZERO, 0, "ACTIVE")));
        assertThrows(IllegalStateException.class,
                () -> service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5));

        when(stationService.search(0.0, 0.0, 5.0, null)).thenReturn(List.of(
                new StationResponseDTO(
                        FIRST_STATION_ID, "12345678901234", "Corp", null, "Brand",
                        "Address", "City", "SP", "00000-000", BigDecimal.ZERO, BigDecimal.ZERO,
                        1.0, BigDecimal.ZERO, 0, "ACTIVE")));
        var result = service.recommend(VEHICLE_ID, USER_ID, 0, 0, 5);
        assertEquals("Corp", result.topOptions().get(0).stationName());
    }

    private void stubVehicle(VehicleResponseDTO vehicle) {
        when(vehicleService.findByIdAndUser(VEHICLE_ID, USER_ID)).thenReturn(vehicle);
    }

    private void stubPrices(FuelPrice... prices) {
        when(fuelPriceRepository.findLatestForStations(anyList(), isNull()))
                .thenReturn(List.of(prices));
    }

    private VehicleResponseDTO vehicle(
            String fuelType,
            String capacity,
            VolumeUnit capacityUnit,
            String gasoline,
            String ethanol,
            String diesel,
            String cng) {
        return new VehicleResponseDTO(
                VEHICLE_ID,
                "Meu veículo",
                "Marca",
                "Modelo",
                2020,
                fuelType,
                new TankCapacityDTO(new BigDecimal(capacity), capacityUnit),
                consumption(gasoline, ConsumptionUnit.KM_PER_LITER),
                consumption(ethanol, ConsumptionUnit.KM_PER_LITER),
                consumption(diesel, ConsumptionUnit.KM_PER_LITER),
                consumption(cng, ConsumptionUnit.KM_PER_CUBIC_METER));
    }

    private ConsumptionDTO consumption(String value, ConsumptionUnit unit) {
        return value == null ? null : new ConsumptionDTO(new BigDecimal(value), unit);
    }

    private StationResponseDTO station(UUID id, String tradeName, String distance) {
        return new StationResponseDTO(
                id, "12345678901234", "Nome corporativo", tradeName, "Bandeira",
                "Rua", "Cidade", "SP", "00000-000",
                BigDecimal.ZERO, BigDecimal.ZERO, Double.valueOf(distance),
                BigDecimal.ZERO, 0, "ACTIVE");
    }

    private FuelPrice price(
            UUID stationId,
            String code,
            String saleValue,
            String unit,
            boolean active) {
        Station station = new Station(
                "12345678901234", "Corporate", "Trade", "Brand",
                "Street", "1", "Center", "City", "SP", "00000-000",
                BigDecimal.ZERO, BigDecimal.ZERO);
        station.setId(stationId);
        FuelType fuelType = new FuelType();
        fuelType.setCode(code);
        fuelType.setName(code);
        fuelType.setUnitOfMeasure(unit);
        fuelType.setActive(active);
        return new FuelPrice(station, fuelType, new BigDecimal(saleValue),
                LocalDate.now(), null);
    }
}
