package com.fuelfinder.modules.vehicle.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.vehicle.dto.ConsumptionDTO;
import com.fuelfinder.modules.vehicle.dto.CreateVehicleRequestDTO;
import com.fuelfinder.modules.vehicle.dto.TankCapacityDTO;
import com.fuelfinder.modules.vehicle.dto.UpdateVehicleRequestDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, CLOCK);
        lenient().when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(invocation -> {
                    Vehicle vehicle = invocation.getArgument(0);
                    vehicle.setId(VEHICLE_ID);
                    return vehicle;
                });
    }

    @Test
    void createsFlexVehicleWithExplicitLiquidConsumptionAndVolumeUnits() {
        var response = vehicleService.create(request(
                FuelTypeAccepted.FLEX,
                volume("50", VolumeUnit.LITER),
                consumption("12.5", ConsumptionUnit.KM_PER_LITER),
                consumption("8.0", ConsumptionUnit.KM_PER_LITER),
                null,
                null,
                2020), USER_ID);

        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        assertEquals(USER_ID, saved.getValue().getUserId());
        assertEquals(VEHICLE_ID, response.id());
        assertEquals("FLEX", response.fuelTypeAccepted());
        assertEquals(consumption("12.5", ConsumptionUnit.KM_PER_LITER),
                response.averageConsumptionGasoline());
        assertEquals(consumption("8.0", ConsumptionUnit.KM_PER_LITER),
                response.averageConsumptionEthanol());
        assertEquals(VolumeUnit.LITER, response.tankCapacity().unit());
    }

    @Test
    void createsCngVehicleWithCubicMeterCapacityAndConsumption() {
        var response = vehicleService.create(request(
                FuelTypeAccepted.CNG,
                volume("15", VolumeUnit.CUBIC_METER),
                null,
                null,
                null,
                consumption("13.2", ConsumptionUnit.KM_PER_CUBIC_METER),
                2020), USER_ID);

        assertEquals(VolumeUnit.CUBIC_METER, response.tankCapacity().unit());
        assertEquals(consumption("13.2", ConsumptionUnit.KM_PER_CUBIC_METER),
                response.averageConsumptionCng());
    }

    @Test
    void acceptsFuelSpecificConsumptionAndInclusiveRangeBoundaries() {
        assertEquals("GASOLINE", vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume("50", VolumeUnit.LITER),
                consumption("1.0", ConsumptionUnit.KM_PER_LITER), null, null, null, 2020),
                USER_ID).fuelTypeAccepted());
        assertEquals("DIESEL", vehicleService.create(request(
                FuelTypeAccepted.DIESEL, volume("80", VolumeUnit.LITER),
                null, null, consumption("40.0", ConsumptionUnit.KM_PER_LITER), null, 2020),
                USER_ID).fuelTypeAccepted());
        assertEquals("ETHANOL", vehicleService.create(request(
                FuelTypeAccepted.ETHANOL, volume("50", VolumeUnit.LITER),
                null, consumption("40.0", ConsumptionUnit.KM_PER_LITER), null, null, 2020),
                USER_ID).fuelTypeAccepted());
    }

    @Test
    void rejectsInvalidYearsAndMissingYear() {
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume("50", VolumeUnit.LITER),
                consumption("10", ConsumptionUnit.KM_PER_LITER), null, null, null, 1949),
                USER_ID));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume("50", VolumeUnit.LITER),
                consumption("10", ConsumptionUnit.KM_PER_LITER), null, null, null, 2028),
                USER_ID));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume("50", VolumeUnit.LITER),
                consumption("10", ConsumptionUnit.KM_PER_LITER), null, null, null, null),
                USER_ID));
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void rejectsMissingExtraneousOrUnitMismatchedFuelConsumptions() {
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.FLEX, liquid("10"), null, null, null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.FLEX, null, liquid("8"), null, null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.ETHANOL, liquid("10"), liquid("8"), null, null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.ETHANOL, null, null, null, null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.DIESEL, null, null, null, null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.GASOLINE, liquid("10"), liquid("8"), null, null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.CNG, null, null, null, liquid("10")));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.CNG, null, null, null,
                consumption("10", ConsumptionUnit.KM_PER_LITER)));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.GASOLINE, null, null, null, null));
        assertThrows(BusinessException.class, () -> create(
                null, liquid("10"), null, null, null));

        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.FLEX, liquid("10"), liquid("8"),
                liquid("7"), null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.FLEX, liquid("10"), liquid("8"),
                null, consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER)));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.GASOLINE, liquid("10"), null,
                liquid("7"), null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.GASOLINE, liquid("10"), null,
                null, consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER)));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.ETHANOL, null, liquid("8"),
                liquid("7"), null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.ETHANOL, null, liquid("8"),
                null, consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER)));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.DIESEL, null, null, liquid("7"), liquid("12")));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.DIESEL, null, null, liquid("7"),
                consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER)));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.DIESEL, null, liquid("8"), liquid("7"), null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.DIESEL, liquid("10"), null, liquid("7"), null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.CNG, null, liquid("8"), null,
                consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER)));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.CNG, null, null, liquid("7"),
                consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER)));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.CNG, liquid("10"), null, null,
                consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER)));
    }

    @Test
    void validatesEveryCombinationOfCngAndLiquidConsumptionFields() {
        for (int mask = 0; mask < 16; mask++) {
            ConsumptionDTO gasoline = (mask & 0b0010) == 0 ? null : liquid("10");
            ConsumptionDTO ethanol = (mask & 0b0100) == 0 ? null : liquid("8");
            ConsumptionDTO diesel = (mask & 0b1000) == 0 ? null : liquid("7");
            ConsumptionDTO cng = (mask & 0b0001) == 0
                    ? null
                    : consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER);

            if (mask == 0b0001) {
                assertEquals("CNG", vehicleService.create(request(
                        FuelTypeAccepted.CNG,
                        volume("15", VolumeUnit.CUBIC_METER),
                        gasoline, ethanol, diesel, cng, 2020), USER_ID).fuelTypeAccepted());
            } else {
                assertThrows(BusinessException.class, () -> vehicleService.create(request(
                        FuelTypeAccepted.CNG,
                        volume("15", VolumeUnit.CUBIC_METER),
                        gasoline, ethanol, diesel, cng, 2020), USER_ID));
            }
        }
    }

    @Test
    void rejectsConsumptionOutOfRangeAndMismatchedTankVolumeUnit() {
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.GASOLINE, liquid("0.99"), null, null, null));
        assertThrows(BusinessException.class, () -> create(
                FuelTypeAccepted.CNG, null, null, null,
                consumption("40.01", ConsumptionUnit.KM_PER_CUBIC_METER)));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.CNG, volume("15", VolumeUnit.LITER),
                null, null, null, consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER), 2020),
                USER_ID));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, null, liquid("12"), null, null, null, 2020),
                USER_ID));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume(null, VolumeUnit.LITER),
                new ConsumptionDTO(null, ConsumptionUnit.KM_PER_LITER),
                null, null, null, 2020),
                USER_ID));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume(null, VolumeUnit.LITER),
                liquid("12"), null, null, null, 2020), USER_ID));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume("50", null),
                liquid("12"), null, null, null, 2020), USER_ID));
        assertThrows(BusinessException.class, () -> vehicleService.create(request(
                FuelTypeAccepted.GASOLINE, volume("0", VolumeUnit.LITER),
                liquid("12"), null, null, null, 2020), USER_ID));
    }

    @Test
    void listsVehiclesAndHidesVehiclesOwnedByOtherUsers() {
        Vehicle vehicle = vehicle(FuelTypeAccepted.GASOLINE, liquid("12"), null, null, null);
        when(vehicleRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(vehicle));
        when(vehicleRepository.findByIdAndUserId(VEHICLE_ID, USER_ID))
                .thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByIdAndUserId(VEHICLE_ID, OTHER_USER_ID))
                .thenReturn(Optional.empty());

        assertEquals(1, vehicleService.listByUser(USER_ID).size());
        assertEquals("Test car", vehicleService.findByIdAndUser(VEHICLE_ID, USER_ID).nickname());
        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.findByIdAndUser(VEHICLE_ID, OTHER_USER_ID));
    }

    @Test
    void returnsVehiclesWithNullOptionalMeasurements() {
        Vehicle vehicle = new Vehicle(
                USER_ID, "Car", "Brand", "Model", 2020,
                FuelTypeAccepted.GASOLINE, null, null, null, null, null);
        when(vehicleRepository.findByIdAndUserId(VEHICLE_ID, USER_ID))
                .thenReturn(Optional.of(vehicle));

        var response = vehicleService.findByIdAndUser(VEHICLE_ID, USER_ID);

        assertNull(response.tankCapacity());
        assertNull(response.averageConsumptionGasoline());
        assertNull(response.averageConsumptionDiesel());
        assertNull(response.averageConsumptionCng());
    }

    @Test
    void updatesDieselConsumptionAsAnExplicitLiquidMeasurement() {
        Vehicle vehicle = vehicle(
                FuelTypeAccepted.DIESEL, null, null, liquid("10"), null);
        when(vehicleRepository.findByIdAndUserId(VEHICLE_ID, USER_ID))
                .thenReturn(Optional.of(vehicle));
        UpdateVehicleRequestDTO patch = new UpdateVehicleRequestDTO();
        patch.setAverageConsumptionDiesel(liquid("11"));

        var response = vehicleService.update(VEHICLE_ID, patch, USER_ID);

        assertEquals(liquid("11"), response.averageConsumptionDiesel());
    }

    @Test
    void partiallyUpdatesVehicleAndRejectsInvalidMeasurementUpdates() {
        Vehicle vehicle = vehicle(
                FuelTypeAccepted.FLEX, liquid("12"), liquid("8"), null, null);
        when(vehicleRepository.findByIdAndUserId(VEHICLE_ID, USER_ID))
                .thenReturn(Optional.of(vehicle));

        UpdateVehicleRequestDTO patch = new UpdateVehicleRequestDTO();
        patch.setNickname("Updated car");
        patch.setFuelTypeAccepted(FuelTypeAccepted.GASOLINE);
        patch.setAverageConsumptionEthanol(null);
        patch.setAverageConsumptionGasoline(
                consumption("14", ConsumptionUnit.KM_PER_LITER));

        var updated = vehicleService.update(VEHICLE_ID, patch, USER_ID);
        assertEquals("Updated car", updated.nickname());
        assertEquals(2020, updated.yearManufacture());
        assertNull(updated.averageConsumptionEthanol());
        assertEquals(decimal("14"), updated.averageConsumptionGasoline().value());

        UpdateVehicleRequestDTO invalidNullYear = new UpdateVehicleRequestDTO();
        invalidNullYear.setYearManufacture(null);
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, invalidNullYear, USER_ID));

        UpdateVehicleRequestDTO invalidCapacity = new UpdateVehicleRequestDTO();
        invalidCapacity.setTankCapacity(volume("0", VolumeUnit.LITER));
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, invalidCapacity, USER_ID));

        UpdateVehicleRequestDTO nullCapacity = new UpdateVehicleRequestDTO();
        nullCapacity.setTankCapacity(null);
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, nullCapacity, USER_ID));

        UpdateVehicleRequestDTO capacityWithoutValue = new UpdateVehicleRequestDTO();
        capacityWithoutValue.setTankCapacity(volume(null, VolumeUnit.LITER));
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, capacityWithoutValue, USER_ID));

        UpdateVehicleRequestDTO capacityWithoutUnit = new UpdateVehicleRequestDTO();
        capacityWithoutUnit.setTankCapacity(volume("50", null));
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, capacityWithoutUnit, USER_ID));

        UpdateVehicleRequestDTO blankNickname = new UpdateVehicleRequestDTO();
        blankNickname.setNickname("  ");
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, blankNickname, USER_ID));

        UpdateVehicleRequestDTO nullNickname = new UpdateVehicleRequestDTO();
        nullNickname.setNickname(null);
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, nullNickname, USER_ID));
        UpdateVehicleRequestDTO nullFuelType = new UpdateVehicleRequestDTO();
        nullFuelType.setFuelTypeAccepted(null);
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, nullFuelType, USER_ID));

        UpdateVehicleRequestDTO invalidFlex = new UpdateVehicleRequestDTO();
        invalidFlex.setFuelTypeAccepted(FuelTypeAccepted.FLEX);
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, invalidFlex, USER_ID));

        UpdateVehicleRequestDTO invalidConsumption = new UpdateVehicleRequestDTO();
        invalidConsumption.setAverageConsumptionGasoline(
                consumption("41", ConsumptionUnit.KM_PER_LITER));
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, invalidConsumption, USER_ID));

        UpdateVehicleRequestDTO wrongCngUnit = new UpdateVehicleRequestDTO();
        wrongCngUnit.setFuelTypeAccepted(FuelTypeAccepted.CNG);
        wrongCngUnit.setAverageConsumptionCng(
                consumption("12", ConsumptionUnit.KM_PER_LITER));
        wrongCngUnit.setTankCapacity(volume("15", VolumeUnit.CUBIC_METER));
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, wrongCngUnit, USER_ID));

        UpdateVehicleRequestDTO invalidCngCombination = new UpdateVehicleRequestDTO();
        invalidCngCombination.setFuelTypeAccepted(FuelTypeAccepted.CNG);
        invalidCngCombination.setAverageConsumptionCng(
                consumption("12", ConsumptionUnit.KM_PER_CUBIC_METER));
        invalidCngCombination.setAverageConsumptionGasoline(
                consumption("10", ConsumptionUnit.KM_PER_LITER));
        invalidCngCombination.setTankCapacity(volume("15", VolumeUnit.CUBIC_METER));
        assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, invalidCngCombination, USER_ID));
    }

    @Test
    void deleteRequiresVehicleOwnership() {
        Vehicle vehicle = vehicle(FuelTypeAccepted.GASOLINE, liquid("12"), null, null, null);
        when(vehicleRepository.findByIdAndUserId(VEHICLE_ID, USER_ID))
                .thenReturn(Optional.of(vehicle));

        vehicleService.delete(VEHICLE_ID, USER_ID);

        verify(vehicleRepository).delete(vehicle);
        when(vehicleRepository.findByIdAndUserId(VEHICLE_ID, USER_ID))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.delete(VEHICLE_ID, USER_ID));
    }

    private void create(
            FuelTypeAccepted type,
            ConsumptionDTO gasoline,
            ConsumptionDTO ethanol,
            ConsumptionDTO diesel,
            ConsumptionDTO cng) {
        vehicleService.create(request(
                type,
                type == FuelTypeAccepted.CNG
                        ? volume("15", VolumeUnit.CUBIC_METER)
                        : volume("50", VolumeUnit.LITER),
                gasoline, ethanol, diesel, cng, 2020), USER_ID);
    }

    private CreateVehicleRequestDTO request(
            FuelTypeAccepted type,
            TankCapacityDTO tankCapacity,
            ConsumptionDTO gasoline,
            ConsumptionDTO ethanol,
            ConsumptionDTO diesel,
            ConsumptionDTO cng,
            Integer year) {
        return new CreateVehicleRequestDTO(
                "Test car", "Brand", "Model", year, type,
                tankCapacity, gasoline, ethanol, diesel, cng);
    }

    private Vehicle vehicle(
            FuelTypeAccepted type,
            ConsumptionDTO gasoline,
            ConsumptionDTO ethanol,
            ConsumptionDTO diesel,
            ConsumptionDTO cng) {
        Vehicle vehicle = new Vehicle(
                USER_ID,
                "Test car",
                "Brand",
                "Model",
                2020,
                type,
                new TankCapacity(decimal("50"), VolumeUnit.LITER),
                toFuelConsumption(gasoline),
                toFuelConsumption(ethanol),
                toFuelConsumption(diesel),
                toFuelConsumption(cng));
        vehicle.setId(VEHICLE_ID);
        return vehicle;
    }

    private ConsumptionDTO liquid(String value) {
        return consumption(value, ConsumptionUnit.KM_PER_LITER);
    }

    private ConsumptionDTO consumption(String value, ConsumptionUnit unit) {
        return value == null ? null : new ConsumptionDTO(decimal(value), unit);
    }

    private TankCapacityDTO volume(String value, VolumeUnit unit) {
        return new TankCapacityDTO(value == null ? null : decimal(value), unit);
    }

    private FuelConsumption toFuelConsumption(ConsumptionDTO dto) {
        return dto == null ? null : new FuelConsumption(dto.value(), dto.unit());
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
