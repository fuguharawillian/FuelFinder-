package com.fuelfinder.modules.auth.dto;

public record AuthResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfileDTO user) {
}
