package com.fuelfinder.modules.recommendation.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StationRecommendationDTO(
        UUID stationId,
        String stationName,
        String brand,
        String fuelType,
        BigDecimal price,
        String unitOfMeasure,
        Double distanceKm,
        BigDecimal costPerKm,
        BigDecimal estimatedFullTankCost,
        BigDecimal estimatedRoundTripCost) {
}
