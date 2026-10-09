package com.bytevault.identity.repository;

import com.bytevault.identity.entity.AuthCredentials;
import com.bytevault.identity.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByCredentials(AuthCredentials credentials);
}
