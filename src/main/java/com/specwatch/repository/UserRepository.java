package com.specwatch.repository;

import com.specwatch.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByResetToken(String resetToken);
    Optional<User> findByEmail(String email);
    Optional<User> findByVerificationToken(String verificationToken);
    boolean existsByEmail(String email);
    long countByCreatedAtAfter(LocalDateTime date);
}
