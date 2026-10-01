package com.fuelfinder.modules.vehicle.dto;

import java.util.UUID;

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
        ConsumptionDTO averageConsumptionCng) {
}
