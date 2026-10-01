package com.fuelfinder.modules.price.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FuelPriceResponseDTO(
        UUID id,
        String fuelTypeCode,
        String fuelTypeName,
        BigDecimal saleValue,
        LocalDate collectionDate,
        String dataSource,
        String unitOfMeasure) {
}
