package com.fuelfinder.modules.auth.service;

import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET =
            "FuelFinderJwtServiceTestSecretKeyContainsMoreThan32Bytes";
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void generatesAndParsesJwtClaimsWithConfiguredExpiration() {
        JwtService service = new JwtService(SECRET, 300_000, clock);
        User user = new User("Driver", "driver@example.com", "encoded");
        user.setId(UUID.randomUUID());
        user.setRole(Role.ROLE_DRIVER);
        UUID sessionId = UUID.randomUUID();

        String token = service.generateToken(user, sessionId);
        JwtClaims claims = service.parseToken(token).orElseThrow();

        assertEquals(user.getId(), claims.userId());
        assertEquals(Role.ROLE_DRIVER, claims.role());
        assertEquals(sessionId, claims.sessionId());
        assertEquals(NOW, claims.issuedAt());
        assertEquals(NOW.plusMillis(300_000), claims.expiresAt());
        assertEquals(300, service.getExpirationSeconds());
    }

    @Test
    void rejectsInvalidSecretAndExpirationConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, 0, clock));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("short", 300_000, clock));
    }

    @Test
    void rejectsExpiredTamperedAndWrongAlgorithmTokens() {
        JwtService service = new JwtService(SECRET, 300_000, clock);
        User user = new User("Driver", "driver@example.com", "encoded");
        user.setId(UUID.randomUUID());
        UUID sessionId = UUID.randomUUID();
        String expiredToken = Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", Role.ROLE_DRIVER.name())
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(20)))
                .expiration(Date.from(Instant.now().minusSeconds(10)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        assertTrue(service.parseToken(expiredToken).isEmpty());

        String validToken = service.generateToken(user, sessionId);
        String[] tokenParts = validToken.split("\\.");
        tokenParts[2] = (tokenParts[2].charAt(0) == 'A' ? "B" : "A")
                + tokenParts[2].substring(1);
        String tamperedToken = String.join(".", tokenParts);
        assertTrue(service.parseToken(tamperedToken).isEmpty());

        String longSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String wrongAlgorithm = Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", Role.ROLE_DRIVER.name())
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(longSecret.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS512)
                .compact();
        assertFalse(new JwtService(longSecret, 300_000, clock)
                .parseToken(wrongAlgorithm).isPresent());
    }

    @Test
    void rejectsTokensWithInvalidClaims() {
        JwtService service = new JwtService(SECRET, 300_000, clock);
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String invalidRole = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "ROLE_UNKNOWN")
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        String invalidId = Jwts.builder()
                .subject("not-a-uuid")
                .claim("role", Role.ROLE_DRIVER.name())
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        String missingClaims = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertTrue(service.parseToken(invalidRole).isEmpty());
        assertTrue(service.parseToken(invalidId).isEmpty());
        assertTrue(service.parseToken(missingClaims).isEmpty());
        assertTrue(service.parseToken("not.a.jwt").isEmpty());
    }
}
