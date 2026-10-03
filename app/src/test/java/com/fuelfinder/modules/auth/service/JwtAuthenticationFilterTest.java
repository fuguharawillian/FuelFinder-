package com.fuelfinder.modules.auth.service;

import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void continuesWhenAuthorizationHeaderIsMissingOrNotBearer() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        MockFilterChain missingHeaderChain = new MockFilterChain();
        filter.doFilter(request(null), new MockHttpServletResponse(), missingHeaderChain);
        assertNotNull(missingHeaderChain.getRequest());

        MockFilterChain unsupportedSchemeChain = new MockFilterChain();
        filter.doFilter(request("Basic credentials"),
                new MockHttpServletResponse(), unsupportedSchemeChain);
        assertNotNull(unsupportedSchemeChain.getRequest());
        verify(jwtService, never()).parseToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsInvalidTokensAndInactiveSessions() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        when(jwtService.parseToken("invalid")).thenReturn(Optional.empty());
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        filter.doFilter(request("Bearer invalid"), invalidResponse, new MockFilterChain());
        assertEquals(401, invalidResponse.getStatus());

        when(jwtService.parseToken("valid")).thenReturn(Optional.of(validClaims()));
        when(authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                sessionId, userId, clock.instant())).thenReturn(false);
        MockHttpServletResponse revokedResponse = new MockHttpServletResponse();
        filter.doFilter(request("Bearer valid"), revokedResponse, new MockFilterChain());
        assertEquals(401, revokedResponse.getStatus());
        verify(userRepository).existsByIdAndStatus(userId, AccountStatus.ACTIVE);
    }

    @Test
    void rejectsInactiveUsersAndAuthenticatesActiveSessions() throws Exception {
        JwtAuthenticationFilter filter = createFilter();
        when(jwtService.parseToken("valid")).thenReturn(Optional.of(validClaims()));
        when(authSessionRepository.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                sessionId, userId, clock.instant())).thenReturn(true);
        when(userRepository.existsByIdAndStatus(userId, AccountStatus.ACTIVE))
                .thenReturn(false, true);

        MockHttpServletResponse inactiveResponse = new MockHttpServletResponse();
        filter.doFilter(request("Bearer valid"), inactiveResponse, new MockFilterChain());
        assertEquals(401, inactiveResponse.getStatus());

        MockFilterChain activeChain = new MockFilterChain();
        filter.doFilter(request("Bearer valid"), new MockHttpServletResponse(), activeChain);
        assertNotNull(activeChain.getRequest());
        assertEquals("ROLE_DRIVER", SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities().iterator().next().getAuthority());
    }

    private JwtAuthenticationFilter createFilter() {
        return new JwtAuthenticationFilter(
                jwtService, userRepository, authSessionRepository, clock);
    }

    private JwtClaims validClaims() {
        return new JwtClaims(
                userId, Role.ROLE_DRIVER, sessionId, clock.instant(), clock.instant().plusSeconds(900));
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
