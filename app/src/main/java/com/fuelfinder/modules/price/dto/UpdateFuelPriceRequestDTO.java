package com.fuelfinder.modules.price.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFuelPriceRequestDTO(
        @Positive(message = "O valor de venda deve ser positivo")
        @Digits(integer = 5, fraction = 3, message = "O preço aceita até 5 dígitos e 3 casas decimais")
        BigDecimal saleValue,
        LocalDate collectionDate) {
}
