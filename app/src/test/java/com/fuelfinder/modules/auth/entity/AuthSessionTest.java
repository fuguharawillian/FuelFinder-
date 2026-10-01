package com.fuelfinder.modules.auth.entity;

import com.fuelfinder.modules.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthSessionTest {

    @Test
    void createsSessionAndRevokesItOnlyOnce() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = createdAt.plusSeconds(3600);
        AuthSession session = AuthSession.create(
                UUID.randomUUID(),
                new User("Driver", "driver@example.com", "encoded"),
                createdAt,
                expiresAt);

        assertEquals(createdAt, session.getCreatedAt());
        assertEquals(expiresAt, session.getExpiresAt());
        assertNull(session.getRevokedAt());

        Instant revokedAt = createdAt.plusSeconds(10);
        session.revoke(revokedAt);
        session.revoke(createdAt.plusSeconds(20));
        assertEquals(revokedAt, session.getRevokedAt());
    }
}
