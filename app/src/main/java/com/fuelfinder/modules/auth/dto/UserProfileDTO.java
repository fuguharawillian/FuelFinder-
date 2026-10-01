package com.fuelfinder.modules.auth.dto;

import java.util.UUID;

public record UserProfileDTO(UUID id, String fullName, String email, String role) {
}
