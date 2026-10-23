package com.bytevault.auth.service;

import com.bytevault.auth.entity.AuthCredentials;
import com.bytevault.auth.entity.VerificationToken;
import com.bytevault.auth.repository.AuthCredentialsRepository;
import com.bytevault.auth.repository.VerificationTokenRepository;
import com.bytevault.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final VerificationTokenRepository tokenRepository;
    private final AuthCredentialsRepository credentialsRepository;

    @Value("${app.verification.expiration:86400000}") // 24 hours
    private Long verificationExpirationMs;

    public void sendVerificationEmail(AuthCredentials credentials) {
        String tokenValue = UUID.randomUUID().toString();

        VerificationToken token = VerificationToken.builder()
                .token(tokenValue)
                .credentials(credentials)
                .expiryDate(Instant.now().plusMillis(verificationExpirationMs))
                .build();

        tokenRepository.save(token);

        String backendBaseUrl = "http://localhost:8080";
        try {
            backendBaseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                    .fromCurrentContextPath().build().toUriString();
        } catch (Exception e) {
            // Fallback in non-web thread contexts
        }

        String verificationLink = backendBaseUrl + "/api/v1/auth/verify?token=" + tokenValue;

        log.info("==================================================");
        log.info("EMAIL VERIFICATION LINK GENERATED FOR {}:", credentials.getUsername());
        log.info(verificationLink);
        log.info("==================================================");
        
        // Asynchronously publish a mail notification event later
    }

    @Transactional
    public void verifyToken(String tokenValue) {
        VerificationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Verification token expired");
        }

        AuthCredentials credentials = token.getCredentials();
        credentials.setActive(true);
        credentialsRepository.save(credentials);

        tokenRepository.delete(token);
        log.info("Successfully verified email for user: {}", credentials.getUsername());
    }
}
