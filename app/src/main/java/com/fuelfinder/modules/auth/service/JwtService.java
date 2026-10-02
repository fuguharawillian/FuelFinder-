package com.fuelfinder.modules.auth.service;

import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final Clock clock;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMs,
            Clock clock) {
        if (accessTokenExpirationMs <= 0) {
            throw new IllegalArgumentException("JWT access token expiration must be positive.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.clock = clock;
    }

    public String generateToken(User user, UUID sessionId) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(accessTokenExpirationMs)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<JwtClaims> parseToken(String token) {
        try {
            var signedClaims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            if (!"HS256".equals(signedClaims.getHeader().getAlgorithm())) {
                return Optional.empty();
            }

            Claims claims = signedClaims.getPayload();
            Role role = Role.valueOf(claims.get("role", String.class));
            return Optional.of(new JwtClaims(
                    UUID.fromString(claims.getSubject()),
                    role,
                    UUID.fromString(claims.get("sid", String.class)),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }
}
