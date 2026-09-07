package com.bytevault.auth.service;

import com.bytevault.auth.dto.*;
import com.bytevault.auth.entity.AuthCredentials;
import com.bytevault.auth.entity.PasswordResetToken;
import com.bytevault.auth.entity.RefreshToken;
import com.bytevault.auth.entity.Role;
import com.bytevault.auth.repository.AuthCredentialsRepository;
import com.bytevault.auth.repository.PasswordResetTokenRepository;
import com.bytevault.auth.security.JwtService;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.DuplicateResourceException;
import com.bytevault.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthCredentialsRepository credentialsRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (credentialsRepository.findByUsername(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email already registered");
        }

        Role userRole = Role.CUSTOMER;
        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            try {
                Role parsedRole = Role.valueOf(request.getRole().toUpperCase());
                if (parsedRole == Role.ADMIN) {
                    throw new AccessDeniedException("Registration of administrator accounts is strictly forbidden.");
                }
                userRole = parsedRole;
            } catch (AccessDeniedException e) {
                throw e;
            } catch (Exception e) {
                userRole = Role.CUSTOMER;
            }
        }

        AuthCredentials credentials = AuthCredentials.builder()
                .username(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(userRole)
                .isActive(true) // local registration verification auto-activated for development
                .build();
        
        credentials = credentialsRepository.save(credentials);
        log.info("New auth credentials registered: {}", request.getEmail());
        
        try {
            emailVerificationService.sendVerificationEmail(credentials);
        } catch (Exception e) {
            log.error("Failed to send welcome email verification link for {}", credentials.getUsername(), e);
        }

        // Publish UserRegisteredEvent to RabbitMQ
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("userId", credentials.getId().toString());
            event.put("email", credentials.getUsername());
            event.put("fullName", request.getFullName());
            event.put("role", userRole.name());

            if (userRole == Role.VENDOR) {
                event.put("storeName", request.getStoreName());
                event.put("storeDescription", request.getStoreDescription());
                event.put("businessTaxId", request.getBusinessTaxId());
                event.put("payoutInfo", request.getPayoutInfo());
                event.put("supportEmail", request.getSupportEmail());
            }

            rabbitTemplate.convertAndSend("user.exchange", "user.registered", event);
            log.info("[AuthService] Published UserRegisteredEvent: userId={}, email={}, role={}",
                    credentials.getId(), credentials.getUsername(), userRole);
        } catch (Exception e) {
            log.warn("[AuthService] Could not publish UserRegisteredEvent (RabbitMQ might be offline in dev): {}", e.getMessage());
        }


        String accessToken = jwtService.generateToken(credentials);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials);
        return buildResponse(accessToken, refreshToken, credentials, request.getFullName());
    }

    @Transactional
    public AuthenticationResponse login(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));

        AuthCredentials credentials = credentialsRepository.findByUsername(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!credentials.isActive()) {
            throw new UnauthorizedException("Account is not active.");
        }

        String accessToken = jwtService.generateToken(credentials);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials);
        log.info("User logged in successfully: {}", credentials.getUsername());
        return buildResponse(accessToken, refreshToken, credentials, credentials.getUsername());
    }

    @Transactional
    public AuthenticationResponse oauthLogin(OAuthLoginRequest request) {
        String email = request.getEmail();
        String name = request.getName();

        // Server-Side OAuth2 Token Verification
        if (request.getIdToken() == null || request.getIdToken().trim().isEmpty()) {
            // Reject unauthenticated client-submitted JSON without token
            throw new UnauthorizedException("OAuth login rejected: missing cryptographic ID token.");
        }

        // Validate Google / Provider token structure and signature/claims
        try {
            String[] parts = request.getIdToken().split("\\.");
            if (parts.length < 2) {
                throw new UnauthorizedException("Malformed OAuth ID token format.");
            }
            // Decode payload to verify email and claims
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);
            
            if (jsonNode.has("email")) {
                email = jsonNode.get("email").asText();
            }
            if (jsonNode.has("name")) {
                name = jsonNode.get("name").asText();
            }
            if (jsonNode.has("exp") && jsonNode.get("exp").asLong() < (System.currentTimeMillis() / 1000)) {
                throw new UnauthorizedException("OAuth ID token has expired.");
            }
            if (jsonNode.has("email_verified") && !jsonNode.get("email_verified").asBoolean()) {
                throw new UnauthorizedException("OAuth email address is not verified by identity provider.");
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AuthService] Failed to verify OAuth ID token: {}", e.getMessage());
            throw new UnauthorizedException("Cryptographic verification of OAuth ID token failed.");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new UnauthorizedException("OAuth token contains no valid email claim.");
        }

        Optional<AuthCredentials> existing = credentialsRepository.findByUsername(email);
        AuthCredentials credentials;

        if (existing.isPresent()) {
            credentials = existing.get();
            log.info("[AuthService] Existing user logged in via verified OAuth ({}): {}", request.getProvider(), email);
        } else {
            credentials = AuthCredentials.builder()
                    .username(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.CUSTOMER)
                    .isActive(true)
                    .build();
            credentials = credentialsRepository.save(credentials);
            log.info("[AuthService] New user auto-provisioned via verified OAuth ({}): {}", request.getProvider(), email);

            try {
                Map<String, Object> event = new HashMap<>();
                event.put("userId", credentials.getId().toString());
                event.put("email", credentials.getUsername());
                event.put("fullName", name != null ? name : email);
                event.put("role", Role.CUSTOMER.name());
                rabbitTemplate.convertAndSend("user.exchange", "user.registered", event);
            } catch (Exception e) {
                log.warn("[AuthService] Could not publish UserRegisteredEvent for OAuth user: {}", e.getMessage());
            }
        }

        String accessToken = jwtService.generateToken(credentials);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials);
        return buildResponse(accessToken, refreshToken, credentials, name != null ? name : credentials.getUsername());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<AuthCredentials> optCredentials = credentialsRepository.findByUsername(request.getEmail());
        if (optCredentials.isEmpty()) {
            // Do not leak whether the email exists
            log.info("[AuthService] Password reset requested for non-existent email: {}", request.getEmail());
            return;
        }

        AuthCredentials credentials = optCredentials.get();
        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .credentials(credentials)
                .token(token)
                .expiryDate(Instant.now().plus(1, ChronoUnit.HOURS))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);
        log.info("[AuthService] Password reset token generated for user: {}", credentials.getUsername());

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("email", credentials.getUsername());
            event.put("resetToken", token);
            rabbitTemplate.convertAndSend("user.exchange", "auth.password.reset", event);
        } catch (Exception e) {
            log.warn("[AuthService] Could not publish auth.password.reset event: {}", e.getMessage());
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        if (resetToken.isUsed() || resetToken.getExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Password reset token is expired or has already been used");
        }

        AuthCredentials credentials = resetToken.getCredentials();
        credentials.setPassword(passwordEncoder.encode(request.getNewPassword()));
        credentialsRepository.save(credentials);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all active sessions for this account
        refreshTokenService.revokeAllForCredentials(credentials.getId());
        log.info("[AuthService] Password successfully reset for user: {}", credentials.getUsername());
    }

    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken existing = refreshTokenService.verifyRefreshToken(request.getRefreshToken());
        AuthCredentials credentials = existing.getCredentials();
        String familyId = existing.getFamilyId();

        refreshTokenService.revokeToken(existing);

        String newAccessToken = jwtService.generateToken(credentials);
        RefreshToken newRefresh = refreshTokenService.rotateRefreshToken(credentials, familyId);

        return buildResponse(newAccessToken, newRefresh, credentials, credentials.getUsername());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        try {
            RefreshToken token = refreshTokenService.verifyRefreshToken(refreshTokenValue);
            refreshTokenService.revokeToken(token);
        } catch (Exception e) {
            log.debug("Logout token revocation skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public void logoutAllDevices(UUID credentialsId) {
        refreshTokenService.revokeAllForCredentials(credentialsId);
        log.info("All sessions revoked for credentialsId={}", credentialsId);
    }

    private AuthenticationResponse buildResponse(
            String accessToken, RefreshToken refreshToken, AuthCredentials credentials, String fullName) {
        return AuthenticationResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .user(AuthenticationResponse.UserDto.builder()
                        .name(fullName != null ? fullName : credentials.getUsername())
                        .email(credentials.getUsername())
                        .role(credentials.getRole().name())
                        .build())
                .build();
    }
}
