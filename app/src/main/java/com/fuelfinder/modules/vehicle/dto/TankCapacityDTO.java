package com.fuelfinder.modules.vehicle.dto;

import com.fuelfinder.modules.vehicle.entity.VolumeUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TankCapacityDTO(
        @NotNull(message = "A capacidade do tanque é obrigatória")
        @Positive(message = "A capacidade do tanque deve ser maior que zero")
        @Digits(integer = 5, fraction = 5, message = "A capacidade aceita até 5 dígitos e 5 casas decimais")
        BigDecimal value,

        @NotNull(message = "A unidade de capacidade é obrigatória")
        VolumeUnit unit) {
}
