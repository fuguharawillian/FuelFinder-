package com.fuelfinder.modules.user.service;

import com.fuelfinder.common.exception.DuplicateResourceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.dto.UserProfileDTO;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.user.dto.UpdateUserRequestDTO;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Transactional
    public UserProfileDTO updateProfile(UUID userId, UpdateUserRequestDTO request) {
        User user = findUser(userId);
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new DuplicateResourceException(
                        "Já existe um usuário cadastrado com o e-mail: " + request.email());
            }
            user.setEmail(request.email());
        }
        return toProfileDto(userRepository.save(user));
    }

    @Transactional
    public UserProfileDTO updateStatus(UUID userId, AccountStatus status) {
        User user = findUser(userId);
        user.setStatus(status);
        User savedUser = userRepository.save(user);
        if (status != AccountStatus.ACTIVE) {
            authSessionRepository.revokeAllByUserId(userId, clock.instant());
        }
        return toProfileDto(savedUser);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }

    private UserProfileDTO toProfileDto(User user) {
        return new UserProfileDTO(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name());
    }
}
