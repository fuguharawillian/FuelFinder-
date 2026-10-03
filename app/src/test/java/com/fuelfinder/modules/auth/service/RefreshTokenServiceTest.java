package com.fuelfinder.modules.auth.service;

import com.fuelfinder.modules.auth.dto.AuthResult;
import com.fuelfinder.modules.auth.entity.AuthSession;
import com.fuelfinder.modules.auth.entity.RefreshToken;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.auth.repository.RefreshTokenRepository;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private JwtService jwtService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private RefreshTokenService refreshTokenService;
    private User user;
    private AuthSession session;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                authSessionRepository,
                jwtService,
                new SecureRandom(),
                clock);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationMs", 3_600_000L);
        user = new User("Driver", "driver@example.com", "encoded");
        user.setId(UUID.randomUUID());
        user.setRole(Role.ROLE_DRIVER);
        session = AuthSession.create(
                UUID.randomUUID(), user, NOW, NOW.plusSeconds(7200));
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issuesUnpredictableOpaqueTokenAndPersistsOnlyItsHash() {
        String rawToken = refreshTokenService.issue(session);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();

        assertEquals(43, rawToken.length());
        assertNotEquals(rawToken, stored.getTokenHash());
        assertEquals(RefreshTokenService.hashToken(rawToken), stored.getTokenHash());
        assertEquals(NOW.plusSeconds(3600), stored.getExpiresAt());
    }

    @Test
    void limitsRefreshTokenExpirationToSessionExpiration() {
        AuthSession shortSession = AuthSession.create(
                UUID.randomUUID(), user, NOW, NOW.plusSeconds(600));

        refreshTokenService.issue(shortSession);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals(NOW.plusSeconds(600), captor.getValue().getExpiresAt());
    }

    @Test
    void rejectsIssuanceWhenConfiguredExpirationIsNotPositive() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationMs", 0L);

        assertThrows(IllegalStateException.class, () -> refreshTokenService.issue(session));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void reportsUnavailableDigestAlgorithm() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> RefreshTokenService.hashToken("token", "UNAVAILABLE"));

        assertEquals("UNAVAILABLE is not available.", exception.getMessage());
    }

    @Test
    void rotatesValidTokenAndMarksPreviousTokenAsUsed() {
        RefreshToken current = createToken("known-hash", NOW.plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(current));
        when(jwtService.generateToken(user, session.getId())).thenReturn("access-token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);

        AuthResult result = refreshTokenService.rotate("old-refresh-token");

        assertEquals("access-token", result.response().accessToken());
        assertTrue(result.refreshToken().length() > 30);
        assertEquals(NOW, current.getUsedAt());
        verify(refreshTokenRepository, org.mockito.Mockito.times(2))
                .save(any(RefreshToken.class));
    }

    @Test
    void rejectsUnknownOrReusedTokensAndRevokesSessionOnReplay() {
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.empty());
        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate("unknown-token"));

        RefreshToken reused = createToken("reused-hash", NOW.plusSeconds(3600));
        reused.markUsed(NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(reused));
        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate("reused-token"));
        verify(authSessionRepository).revoke(session.getId(), NOW);
    }

    @Test
    void rejectsExpiredAndRevokedTokensOrSessions() {
        RefreshToken expired = createToken("expired-hash", NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(expired));
        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate("expired-token"));

        RefreshToken revoked = createToken("revoked-hash", NOW.plusSeconds(3600));
        revoked.revoke(NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(revoked));
        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate("revoked-token"));

        AuthSession expiredSession = AuthSession.create(
                UUID.randomUUID(), user, NOW.minusSeconds(7200), NOW.minusSeconds(1));
        RefreshToken forExpiredSession = new RefreshToken(
                expiredSession, "session-hash", NOW.plusSeconds(100), NOW);
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(forExpiredSession));
        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate("expired-session-token"));

        AuthSession revokedSession = AuthSession.create(
                UUID.randomUUID(), user, NOW.minusSeconds(10), NOW.plusSeconds(3600));
        revokedSession.revoke(NOW.minusSeconds(1));
        RefreshToken forRevokedSession = new RefreshToken(
                revokedSession, "revoked-session-hash", NOW.plusSeconds(100), NOW);
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(forRevokedSession));
        assertThrows(InvalidTokenException.class,
                () -> refreshTokenService.rotate("revoked-session-token"));
    }

    @Test
    void revokesSessionWhenAccountIsNotActive() {
        user.setStatus(AccountStatus.BLOCKED);
        RefreshToken current = createToken("blocked-hash", NOW.plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(current));

        assertThrows(AccountForbiddenException.class,
                () -> refreshTokenService.rotate("blocked-account-token"));
        verify(authSessionRepository).revoke(session.getId(), NOW);
    }

    private RefreshToken createToken(String tokenHash, Instant expiresAt) {
        return new RefreshToken(session, tokenHash, expiresAt, NOW.minusSeconds(10));
    }
}
