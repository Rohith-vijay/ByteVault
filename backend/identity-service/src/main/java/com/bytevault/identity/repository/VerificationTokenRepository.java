package com.bytevault.identity.repository;

import com.bytevault.identity.entity.AuthCredentials;
import com.bytevault.identity.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByToken(String token);
    void deleteByCredentials(AuthCredentials credentials);
}
