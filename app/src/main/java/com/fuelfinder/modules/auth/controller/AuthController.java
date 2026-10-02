package com.fuelfinder.modules.auth.controller;

import com.fuelfinder.modules.auth.AuthPrincipal;
import com.fuelfinder.modules.auth.dto.AuthResponseDTO;
import com.fuelfinder.modules.auth.dto.AuthResult;
import com.fuelfinder.modules.auth.dto.LoginRequestDTO;
import com.fuelfinder.modules.auth.dto.RegisterRequestDTO;
import com.fuelfinder.modules.auth.dto.UserProfileDTO;
import com.fuelfinder.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final Environment environment;
    private final String cookieName;
    private final String cookiePath;
    private final String cookieSameSite;
    private final boolean cookieSecure;
    private final long refreshTokenExpirationMs;

    public AuthController(
            AuthService authService,
            Environment environment,
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-path}") String cookiePath,
            @Value("${app.auth.cookie-same-site}") String cookieSameSite,
            @Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.authService = authService;
        this.environment = environment;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSameSite = cookieSameSite;
        this.cookieSecure = cookieSecure;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO request) {
        return withRefreshCookie(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request) {
        return withRefreshCookie(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal.sessionId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue("${app.auth.cookie-name}") String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Void> revokeAllSessions(
            @AuthenticationPrincipal AuthPrincipal principal) {
        authService.revokeAllSessions(principal.userId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> me(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(authService.getProfile(principal.userId()));
    }

    private ResponseEntity<AuthResponseDTO> withRefreshCookie(
            AuthResult result,
            HttpStatus status) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, result.refreshToken())
                .httpOnly(true)
                .secure(isSecureCookie())
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(refreshTokenExpirationMs / 1000)
                .build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    private String expiredRefreshCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(isSecureCookie())
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(0)
                .build()
                .toString();
    }

    private boolean isSecureCookie() {
        return cookieSecure || environment.acceptsProfiles(Profiles.of("prod"));
    }
}
