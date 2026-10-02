package com.fuelfinder.modules.auth;

import java.util.UUID;

public record AuthPrincipal(UUID userId, UUID sessionId) {
}
