package com.fuelfinder.modules.review.dto;

import com.fuelfinder.modules.review.entity.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ModerateReviewRequestDTO(
        @NotNull(message = "O status da avaliação é obrigatório")
        ReviewStatus status) {
}
