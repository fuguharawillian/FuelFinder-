package com.fuelfinder.modules.user.service;

import com.fuelfinder.common.exception.DuplicateResourceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.auth.repository.AuthSessionRepository;
import com.fuelfinder.modules.user.dto.UpdateUserRequestDTO;
import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.User;
import com.fuelfinder.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private UserService userService;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, authSessionRepository, clock);
        userId = UUID.randomUUID();
        user = new User("Driver", "driver@example.com", "encoded");
        user.setId(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatesNameAndIgnoresUnchangedEmail() {
        var profile = userService.updateProfile(
                userId, new UpdateUserRequestDTO("Updated", "driver@example.com"));

        assertEquals("Updated", profile.fullName());
        assertEquals("driver@example.com", profile.email());
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updatesProfileWhenEmailIsNotSupplied() {
        var profile = userService.updateProfile(
                userId, new UpdateUserRequestDTO("Updated", null));

        assertEquals("Updated", profile.fullName());
        assertEquals("driver@example.com", profile.email());
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void changesEmailWhenItIsAvailable() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        var profile = userService.updateProfile(
                userId, new UpdateUserRequestDTO(null, "new@example.com"));

        assertEquals("new@example.com", profile.email());
    }

    @Test
    void rejectsEmailAlreadyInUse() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> userService.updateProfile(
                        userId, new UpdateUserRequestDTO(null, "taken@example.com")));
    }

    @Test
    void rejectsUpdatesForMissingUsers() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateProfile(
                        missingId, new UpdateUserRequestDTO("Name", null)));
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateStatus(missingId, AccountStatus.BLOCKED));
    }

    @Test
    void revokesSessionsForNonActiveStatusButNotForActiveStatus() {
        userService.updateStatus(userId, AccountStatus.BLOCKED);
        verify(authSessionRepository).revokeAllByUserId(
                userId, Instant.parse("2026-01-01T00:00:00Z"));

        userService.updateStatus(userId, AccountStatus.ACTIVE);
        verify(authSessionRepository).revokeAllByUserId(
                userId, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
