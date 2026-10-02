package com.fuelfinder.modules.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationResponseDTO(
        VehicleSummaryDTO vehicle,
        String recommendedFuel,
        String explanation,
        BigDecimal parityPercentage,
        List<StationRecommendationDTO> topOptions) {
}
