package com.fuelfinder.modules.auth.service;

import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Test
    void rejectsProfileRequestsForUnknownUsers() {
        UUID unknownUserId = UUID.randomUUID();
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                authSessionRepository,
                refreshTokenService,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertThrows(ResourceNotFoundException.class, () -> authService.getProfile(unknownUserId));
    }
}
