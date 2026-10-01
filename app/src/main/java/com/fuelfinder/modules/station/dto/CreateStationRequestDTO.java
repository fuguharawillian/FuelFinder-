package com.fuelfinder.modules.station.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateStationRequestDTO(
        @NotBlank(message = "O CNPJ é obrigatório")
        @Size(max = 18, message = "O CNPJ deve conter no máximo 18 caracteres")
        @Pattern(regexp = "(\\d{14}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})",
                message = "O CNPJ deve conter 14 dígitos, com ou sem formatação")
        String cnpj,

        @NotBlank(message = "A razão social é obrigatória")
        @Size(max = 255, message = "A razão social deve conter no máximo 255 caracteres")
        String corporateName,

        @Size(max = 255, message = "O nome fantasia deve conter no máximo 255 caracteres")
        String tradeName,

        @Size(max = 100, message = "A marca deve conter no máximo 100 caracteres")
        String brand,

        @Size(max = 255, message = "A rua deve conter no máximo 255 caracteres")
        String street,

        @Size(max = 20, message = "O número deve conter no máximo 20 caracteres")
        String number,

        @Size(max = 100, message = "O bairro deve conter no máximo 100 caracteres")
        String neighborhood,

        @NotBlank(message = "A cidade é obrigatória")
        @Size(max = 100, message = "A cidade deve conter no máximo 100 caracteres")
        String city,

        @NotBlank(message = "O estado é obrigatório")
        @Pattern(regexp = "[A-Za-z]{2}", message = "O estado deve conter a sigla de 2 letras")
        String state,

        @Size(max = 10, message = "O CEP deve conter no máximo 10 caracteres")
        String postalCode,

        @NotNull(message = "A latitude é obrigatória")
        @DecimalMin(value = "-90.0", message = "A latitude mínima é -90")
        @DecimalMax(value = "90.0", message = "A latitude máxima é 90")
        BigDecimal latitude,

        @NotNull(message = "A longitude é obrigatória")
        @DecimalMin(value = "-180.0", message = "A longitude mínima é -180")
        @DecimalMax(value = "180.0", message = "A longitude máxima é 180")
        BigDecimal longitude) {
}
