package com.fuelfinder.modules.price.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CompareResultDTO(
        UUID stationId,
        String stationName,
        String brand,
        String fuelTypeCode,
        BigDecimal price,
        String unitOfMeasure,
        LocalDate collectionDate,
        Double distanceKm,
        BigDecimal costPerKm,
        BigDecimal estimatedFullTankCost,
        BigDecimal estimatedRange,
        BigDecimal estimatedRoundTripCost) {
}
