package com.fuelfinder.modules.auth.service;

import com.fuelfinder.modules.user.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(UUID userId, Role role, UUID sessionId, Instant issuedAt, Instant expiresAt) {
}
