package com.fuelfinder.modules.vehicle.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VehicleTest {

    @Test
    void initializesAndUpdatesPersistenceTimestamps() {
        Vehicle vehicle = new Vehicle();
        vehicle.initializeTimestamps();
        LocalDateTime initialCreatedAt = vehicle.getCreatedAt();
        assertNotNull(initialCreatedAt);
        assertNotNull(vehicle.getUpdatedAt());

        vehicle.initializeTimestamps();
        assertEquals(initialCreatedAt, vehicle.getCreatedAt());

        vehicle.updateTimestamp();
        assertNotNull(vehicle.getUpdatedAt());
    }

    @Test
    void exposesVehiclePropertiesForPersistenceAndResponses() {
        Vehicle vehicle = new Vehicle();
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        vehicle.setId(id);
        vehicle.setUserId(userId);
        vehicle.setNickname("Car");
        vehicle.setBrand("Brand");
        vehicle.setModel("Model");
        vehicle.setYearManufacture(2020);
        vehicle.setFuelTypeAccepted(FuelTypeAccepted.FLEX);
        vehicle.setTankCapacity(new TankCapacity(new BigDecimal("50.00"), VolumeUnit.LITER));
        vehicle.setAvgConsumptionGasoline(
                new FuelConsumption(new BigDecimal("12.00"), ConsumptionUnit.KM_PER_LITER));
        vehicle.setAvgConsumptionEthanol(
                new FuelConsumption(new BigDecimal("8.00"), ConsumptionUnit.KM_PER_LITER));
        vehicle.setAvgConsumptionDiesel(
                new FuelConsumption(new BigDecimal("10.00"), ConsumptionUnit.KM_PER_LITER));
        vehicle.setAvgConsumptionCng(
                new FuelConsumption(new BigDecimal("13.00"), ConsumptionUnit.KM_PER_CUBIC_METER));

        assertEquals(id, vehicle.getId());
        assertEquals(userId, vehicle.getUserId());
        assertEquals("Car", vehicle.getNickname());
        assertEquals("Brand", vehicle.getBrand());
        assertEquals("Model", vehicle.getModel());
        assertEquals(2020, vehicle.getYearManufacture());
        assertEquals(FuelTypeAccepted.FLEX, vehicle.getFuelTypeAccepted());
        assertEquals(new BigDecimal("50.00"), vehicle.getTankCapacity().getValue());
        assertEquals(VolumeUnit.LITER, vehicle.getTankCapacity().getUnit());
        assertEquals(new BigDecimal("12.00"), vehicle.getAvgConsumptionGasoline().getValue());
        assertEquals(ConsumptionUnit.KM_PER_LITER, vehicle.getAvgConsumptionGasoline().getUnit());
        assertEquals(new BigDecimal("8.00"), vehicle.getAvgConsumptionEthanol().getValue());
        assertEquals(ConsumptionUnit.KM_PER_LITER, vehicle.getAvgConsumptionEthanol().getUnit());
        assertEquals(new BigDecimal("10.00"), vehicle.getAvgConsumptionDiesel().getValue());
        assertEquals(ConsumptionUnit.KM_PER_LITER, vehicle.getAvgConsumptionDiesel().getUnit());
        assertEquals(new BigDecimal("13.00"), vehicle.getAvgConsumptionCng().getValue());
        assertEquals(
                ConsumptionUnit.KM_PER_CUBIC_METER, vehicle.getAvgConsumptionCng().getUnit());
    }
}
