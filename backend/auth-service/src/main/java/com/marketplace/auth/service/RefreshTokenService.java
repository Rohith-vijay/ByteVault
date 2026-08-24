package com.marketplace.auth.service;

import com.marketplace.auth.entity.AuthCredentials;
import com.marketplace.auth.entity.RefreshToken;
import com.marketplace.auth.repository.RefreshTokenRepository;
import com.marketplace.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days
    private Long refreshExpirationMs;

    @Transactional
    public RefreshToken createRefreshToken(AuthCredentials credentials) {
        return createInFamily(credentials, UUID.randomUUID().toString());
    }

    @Transactional
    public RefreshToken rotateRefreshToken(AuthCredentials credentials, String familyId) {
        return createInFamily(credentials, familyId);
    }

    @Transactional
    public RefreshToken verifyRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            log.error("SECURITY ALERT: Revoked token reuse detected. username={}, familyId={}",
                    refreshToken.getCredentials().getUsername(), refreshToken.getFamilyId());
            refreshTokenRepository.revokeAllByFamilyId(refreshToken.getFamilyId());
            throw new BadRequestException("Session invalidated. Please log in again.");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Refresh token expired. Please log in again.");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void revokeAllForCredentials(UUID credentialsId) {
        refreshTokenRepository.revokeAllActiveByCredentialsId(credentialsId);
        log.info("All refresh tokens revoked for credentialsId={}", credentialsId);
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
        log.info("Expired refresh tokens cleaned up");
    }

    private RefreshToken createInFamily(AuthCredentials credentials, String familyId) {
        RefreshToken token = RefreshToken.builder()
                .credentials(credentials)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .familyId(familyId)
                .build();
        return refreshTokenRepository.save(token);
    }
}
