package com.fuelfinder.modules.auth.dto;

public record AuthResult(AuthResponseDTO response, String refreshToken) {
}
