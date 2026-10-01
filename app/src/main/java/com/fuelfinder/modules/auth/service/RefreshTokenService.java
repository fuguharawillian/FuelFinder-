package com.fuelfinder.modules.auth.service;

import com.fuelfinder.modules.auth.dto.AuthResponseDTO;
import com.fuelfinder.modules.auth.dto.AuthResult;
import com.fuelfinder.modules.auth.dto.UserProfileDTO;
import com.fuelfinder.modules.auth.entity.AuthSession;
import com.fuelfinder.modules.auth.entity.RefreshToken;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AuthSessionRepository authSessionRepository,
            JwtService jwtService,
            SecureRandom secureRandom,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authSessionRepository = authSessionRepository;
        this.jwtService = jwtService;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    public String issue(AuthSession session) {
        Instant now = clock.instant();
        if (refreshTokenExpirationMs <= 0) {
            throw new IllegalStateException("JWT refresh token expiration must be positive.");
        }
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = session.getExpiresAt().isBefore(now.plusMillis(refreshTokenExpirationMs))
                ? session.getExpiresAt()
                : now.plusMillis(refreshTokenExpirationMs);
        refreshTokenRepository.save(new RefreshToken(
                session, hashToken(token), expiresAt, now));
        return token;
    }

    @Transactional(noRollbackFor = {InvalidTokenException.class, AccountForbiddenException.class})
    public AuthResult rotate(String presentedToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(hashToken(presentedToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token inválido."));
        AuthSession session = current.getSession();

        if (current.getUsedAt() != null) {
            authSessionRepository.revoke(session.getId(), now);
            throw new InvalidTokenException("Refresh token reutilizado; sessão revogada.");
        }
        if (current.getRevokedAt() != null
                || !current.getExpiresAt().isAfter(now)
                || !session.getExpiresAt().isAfter(now)
                || session.getRevokedAt() != null) {
            throw new InvalidTokenException("Refresh token expirado ou revogado.");
        }

        User user = session.getUser();
        if (user.getStatus() != AccountStatus.ACTIVE) {
            authSessionRepository.revoke(session.getId(), now);
            throw new AccountForbiddenException("A conta não está ativa.");
        }

        current.markUsed(now);
        refreshTokenRepository.save(current);
        String nextToken = issue(session);
        return new AuthResult(
                new AuthResponseDTO(
                        jwtService.generateToken(user, session.getId()),
                        "Bearer",
                        jwtService.getExpirationSeconds(),
                        new UserProfileDTO(
                                user.getId(),
                                user.getFullName(),
                                user.getEmail(),
                                user.getRole().name())),
                nextToken);
    }

    static String hashToken(String token) {
        return hashToken(token, "SHA-256");
    }

    static String hashToken(String token, String algorithm) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm)
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is not available.", exception);
        }
    }
}
