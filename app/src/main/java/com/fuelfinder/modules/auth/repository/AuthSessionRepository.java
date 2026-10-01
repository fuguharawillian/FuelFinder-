package com.fuelfinder.modules.auth.repository;

import com.fuelfinder.modules.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    boolean existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID id, UUID userId, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("""
            update AuthSession session
            set session.revokedAt = :now
            where session.id = :id and session.revokedAt is null
            """)
    int revoke(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("""
            update AuthSession session
            set session.revokedAt = :now
            where session.user.id = :userId and session.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
