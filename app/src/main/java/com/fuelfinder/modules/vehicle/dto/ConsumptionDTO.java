package com.fuelfinder.modules.vehicle.dto;

import com.fuelfinder.modules.vehicle.entity.ConsumptionUnit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ConsumptionDTO(
        @NotNull(message = "O valor de consumo é obrigatório")
        @DecimalMin(value = "1.0", message = "O consumo deve ser no mínimo 1.0")
        @DecimalMax(value = "40.0", message = "O consumo deve ser no máximo 40.0")
        @Digits(integer = 3, fraction = 2, message = "O consumo aceita até 3 dígitos e 2 casas decimais")
        BigDecimal value,

        @NotNull(message = "A unidade de consumo é obrigatória")
        ConsumptionUnit unit) {
}
