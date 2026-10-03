package com.fuelfinder.modules.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserTest {

    @Test
    void storesAndUpdatesUserProperties() {
        User user = new User("Driver", "driver@example.com", "encoded");
        UUID userId = UUID.randomUUID();

        assertEquals(Role.ROLE_DRIVER, user.getRole());
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        user.setId(userId);
        user.setFullName("Updated Driver");
        user.setEmail("updated@example.com");
        user.setPasswordHash("new-encoded");
        user.setRole(Role.ROLE_ADMIN);
        user.setStatus(AccountStatus.BLOCKED);

        assertEquals(userId, user.getId());
        assertEquals("Updated Driver", user.getFullName());
        assertEquals("updated@example.com", user.getEmail());
        assertEquals("new-encoded", user.getPasswordHash());
        assertEquals(Role.ROLE_ADMIN, user.getRole());
        assertEquals(AccountStatus.BLOCKED, user.getStatus());
    }

    @Test
    void updatesTimestampBeforeEntityUpdate() {
        User user = new User("Driver", "driver@example.com", "encoded");
        LocalDateTime originalUpdatedAt = user.getUpdatedAt();

        user.updateTimestamp();

        assertNotNull(user.getUpdatedAt());
        assertEquals(false, user.getUpdatedAt().isBefore(originalUpdatedAt));
    }
}
