package com.bytevault.auth.repository;

import com.bytevault.auth.entity.AuthCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthCredentialsRepository extends JpaRepository<AuthCredentials, UUID> {
    Optional<AuthCredentials> findByUsername(String username);
}
