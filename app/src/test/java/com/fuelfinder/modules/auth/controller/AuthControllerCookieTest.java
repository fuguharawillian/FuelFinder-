package com.fuelfinder.modules.auth.controller;

import com.fuelfinder.modules.auth.dto.AuthResponseDTO;
import com.fuelfinder.modules.auth.dto.AuthResult;
import com.fuelfinder.modules.auth.dto.LoginRequestDTO;
import com.fuelfinder.modules.auth.dto.UserProfileDTO;
import com.fuelfinder.modules.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerCookieTest {

    @Test
    void marksCookieSecureInProductionEvenWhenLocalFlagIsFalse() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AuthController controller = controller(environment, false);

        var response = controller.login(
                new LoginRequestDTO("driver@example.com", "SecurePass1!"));

        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("Secure"));
    }

    @Test
    void honorsExplicitSecureCookieSettingOutsideProduction() {
        AuthController controller = controller(new MockEnvironment(), true);

        var response = controller.login(
                new LoginRequestDTO("driver@example.com", "SecurePass1!"));

        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("Secure"));
    }

    private AuthController controller(MockEnvironment environment, boolean secureCookie) {
        AuthService authService = mock(AuthService.class);
        AuthResult result = new AuthResult(
                new AuthResponseDTO(
                        "access-token",
                        "Bearer",
                        900,
                        new UserProfileDTO(
                                UUID.randomUUID(),
                                "Driver",
                                "driver@example.com",
                                "ROLE_DRIVER")),
                "opaque-refresh-token");
        when(authService.login(org.mockito.ArgumentMatchers.any())).thenReturn(result);
        return new AuthController(
                authService,
                environment,
                "refreshToken",
                "/auth",
                "Lax",
                secureCookie,
                2_592_000_000L);
    }
}
