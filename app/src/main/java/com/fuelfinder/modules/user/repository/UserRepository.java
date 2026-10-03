package com.fuelfinder.modules.user.repository;

import com.fuelfinder.modules.user.entity.AccountStatus;
import com.fuelfinder.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByIdAndStatus(UUID id, AccountStatus status);
}
