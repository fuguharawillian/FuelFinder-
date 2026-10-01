package com.fuelfinder.modules.station.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StationResponseDTO(
        UUID id,
        String cnpj,
        String corporateName,
        String tradeName,
        String brand,
        String address,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Double distanceKm,
        BigDecimal averageRating,
        Integer totalReviews,
        String status) {
}
