package com.fuelfinder.modules.vehicle.dto;

import com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequestDTO(
        @NotBlank(message = "O apelido do veículo é obrigatório")
        @Size(min = 2, max = 50, message = "O apelido deve conter entre 2 e 50 caracteres")
        String nickname,

        @NotBlank(message = "A marca é obrigatória")
        @Size(max = 100, message = "A marca deve conter no máximo 100 caracteres")
        String brand,

        @NotBlank(message = "O modelo é obrigatório")
        @Size(max = 100, message = "O modelo deve conter no máximo 100 caracteres")
        String model,

        @NotNull(message = "O ano de fabricação é obrigatório")
        Integer yearManufacture,

        @NotNull(message = "O tipo de combustível é obrigatório")
        FuelTypeAccepted fuelTypeAccepted,

        @NotNull(message = "A capacidade do tanque é obrigatória")
        @Valid
        TankCapacityDTO tankCapacity,

        @Valid
        ConsumptionDTO averageConsumptionGasoline,

        @Valid
        ConsumptionDTO averageConsumptionEthanol,

        @Valid
        ConsumptionDTO averageConsumptionDiesel,

        @Valid
        ConsumptionDTO averageConsumptionCng) {
}
