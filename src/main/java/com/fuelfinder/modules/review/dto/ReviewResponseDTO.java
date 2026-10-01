package com.fuelfinder.modules.review.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponseDTO(
        UUID id,
        UUID userId,
        String userName,
        UUID stationId,
        Integer rating,
        String comment,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
