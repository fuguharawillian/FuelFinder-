package com.fuelfinder.modules.auth.entity;

import com.fuelfinder.modules.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RefreshTokenTest {

    @Test
    void storesHashAndTracksUseAndRevocation() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        AuthSession session = AuthSession.create(
                UUID.randomUUID(),
                new User("Driver", "driver@example.com", "encoded"),
                now,
                now.plusSeconds(3600));
        RefreshToken token = new RefreshToken(
                session,
                "opaque-token-hash",
                now.plusSeconds(3600),
                now);

        assertNull(token.getId());
        assertEquals(session, token.getSession());
        assertEquals("opaque-token-hash", token.getTokenHash());
        assertEquals(now.plusSeconds(3600), token.getExpiresAt());
        assertEquals(now, token.getCreatedAt());
        assertNull(token.getUsedAt());
        assertNull(token.getRevokedAt());

        token.markUsed(now.plusSeconds(1));
        token.revoke(now.plusSeconds(2));
        token.revoke(now.plusSeconds(3));
        assertEquals(now.plusSeconds(1), token.getUsedAt());
        assertEquals(now.plusSeconds(2), token.getRevokedAt());
    }
}
