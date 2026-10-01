package com.fuelfinder.modules.auth.service;

import com.fuelfinder.common.exception.DuplicateResourceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.dto.AuthResponseDTO;
import com.fuelfinder.modules.auth.dto.AuthResult;
import com.fuelfinder.modules.auth.dto.LoginRequestDTO;
import com.fuelfinder.modules.auth.dto.RegisterRequestDTO;
import com.fuelfinder.modules.auth.dto.UserProfileDTO;
import com.fuelfinder.modules.auth.entity.AuthSession;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.Role;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthSessionRepository authSessionRepository,
            RefreshTokenService refreshTokenService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "Já existe um usuário cadastrado com o e-mail: " + request.email());
        }

        User user = new User(
                request.fullName(),
                request.email(),
                passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_DRIVER);
        user.setStatus(AccountStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        Instant now = clock.instant();
        AuthSession session = authSessionRepository.save(AuthSession.create(
                UUID.randomUUID(),
                savedUser,
                now,
                now.plusMillis(refreshTokenExpirationMs)));
        return createAuthResult(savedUser, session);
    }

    @Transactional
    public AuthResult login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciais inválidas."));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountForbiddenException("A conta não está ativa.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciais inválidas.");
        }

        Instant now = clock.instant();
        AuthSession session = authSessionRepository.save(AuthSession.create(
                UUID.randomUUID(),
                user,
                now,
                now.plusMillis(refreshTokenExpirationMs)));
        return createAuthResult(user, session);
    }

    public AuthResult refresh(String refreshToken) {
        return refreshTokenService.rotate(refreshToken);
    }

    @Transactional
    public void logout(UUID sessionId) {
        authSessionRepository.revoke(sessionId, clock.instant());
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        authSessionRepository.revokeAllByUserId(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        return toProfileDto(user);
    }

    private AuthResult createAuthResult(User user, AuthSession session) {
        return new AuthResult(
                new AuthResponseDTO(
                        jwtService.generateToken(user, session.getId()),
                        "Bearer",
                        jwtService.getExpirationSeconds(),
                        toProfileDto(user)),
                refreshTokenService.issue(session));
    }

    private UserProfileDTO toProfileDto(User user) {
        return new UserProfileDTO(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name());
    }
}
