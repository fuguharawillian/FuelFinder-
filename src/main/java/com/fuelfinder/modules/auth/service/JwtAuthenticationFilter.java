package com.fuelfinder.modules.auth.service;

import com.fuelfinder.config.SecurityConfig;
import com.fuelfinder.modules.auth.AuthPrincipal;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            Clock clock) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        var claims = jwtService.parseToken(authorization.substring(7));
        if (claims.isEmpty()) {
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED).commence(
                    request, response, new InvalidTokenException("Access token inválido."));
            return;
        }

        var token = claims.get();
        boolean activeSession = authSessionRepository
                .existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        token.sessionId(), token.userId(), clock.instant());
        boolean activeUser = userRepository.existsByIdAndStatus(
                token.userId(), AccountStatus.ACTIVE);
        if (!activeSession || !activeUser) {
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED).commence(
                    request, response, new InvalidTokenException("Sessão inválida ou revogada."));
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(token.userId(), token.sessionId()),
                null,
                List.of(new SimpleGrantedAuthority(token.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
