package com.bytevault.identity.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.DuplicateResourceException;
import com.bytevault.common.exception.UnauthorizedException;
import com.bytevault.identity.dto.*;
import com.bytevault.identity.entity.*;
import com.bytevault.identity.repository.AuthCredentialsRepository;
import com.bytevault.identity.repository.PasswordResetTokenRepository;
import com.bytevault.identity.security.JwtService;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final AuthCredentialsRepository credentialsRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final UserProfileService userProfileService;
    private final VendorProfileService vendorProfileService;

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (credentialsRepository.findByUsername(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        Role userRole = Role.CUSTOMER;
        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            try {
                Role parsedRole = Role.valueOf(request.getRole().trim().toUpperCase());
                if (parsedRole == Role.ADMIN) {
                    throw new AccessDeniedException("Direct registration of administrator accounts is strictly forbidden.");
                }
                userRole = parsedRole;
            } catch (AccessDeniedException e) {
                throw e;
            } catch (Exception e) {
                userRole = Role.CUSTOMER;
            }
        }

        // 1. Create Auth Credentials
        AuthCredentials credentials = AuthCredentials.builder()
                .username(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(userRole)
                .isActive(true)
                .isEmailVerified(false)
                .build();

        credentials = credentialsRepository.save(credentials);
        log.info("[IdentityService] Saved credentials for user={}, role={}", credentials.getUsername(), userRole);

        // 2. Synchronous User Profile creation
        String fullName = request.getFullName();
        if (fullName == null && request.getFirstName() != null) {
            fullName = request.getFirstName() + (request.getLastName() != null ? " " + request.getLastName() : "");
        }
        UserProfile profile = userProfileService.createProfile(
                credentials.getId(),
                credentials.getUsername(),
                fullName,
                request.getFirstName(),
                request.getLastName()
        );
        if (profile != null) {
            log.info("[IdentityService] Synchronously created user profile: id={}, email={}", profile.getId(), profile.getEmail());
        }

        // 3. If VENDOR, synchronously create VendorProfile in PENDING status
        if (userRole == Role.VENDOR) {
            VendorProfile vendorProfile = vendorProfileService.createPendingVendorProfile(
                    credentials.getId(),
                    credentials.getUsername(),
                    request.getStoreName(),
                    request.getStoreDescription(),
                    request.getBusinessTaxId(),
                    request.getPayoutInfo(),
                    request.getSupportEmail()
            );
            log.info("[IdentityService] Synchronously created PENDING vendor profile: id={}, store={}",
                    vendorProfile.getId(), vendorProfile.getStoreName());
        }

        // 4. Send verification email (defensive/logged)
        try {
            emailVerificationService.sendVerificationEmail(credentials);
        } catch (Exception e) {
            log.warn("[IdentityService] Email verification dispatch failed for {}: {}", credentials.getUsername(), e.getMessage());
        }

        // 5. Generate tokens
        String accessToken = jwtService.generateToken(credentials);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials);

        return AuthenticationResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .userId(credentials.getId())
                .email(credentials.getUsername())
                .role(credentials.getRole().name())
                .fullName(fullName)
                .build();
    }

    @Transactional
    public AuthenticationResponse login(AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail().trim().toLowerCase(), request.getPassword())
            );
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid email or password");
        }

        AuthCredentials credentials = credentialsRepository.findByUsername(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!credentials.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        String accessToken = jwtService.generateToken(credentials);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials);

        String fullName = null;
        try {
            UserProfileResponse profile = userProfileService.getProfile(credentials.getId());
            if (profile != null) {
                fullName = profile.getFullName();
            }
        } catch (Exception ignored) {}

        return AuthenticationResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .userId(credentials.getId())
                .email(credentials.getUsername())
                .role(credentials.getRole().name())
                .fullName(fullName)
                .build();
    }

    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenService.verifyRefreshToken(request.getRefreshToken());
        AuthCredentials credentials = token.getCredentials();

        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(credentials, token.getFamilyId());
        refreshTokenService.revokeToken(token);

        String newAccessToken = jwtService.generateToken(credentials);

        return AuthenticationResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .userId(credentials.getId())
                .email(credentials.getUsername())
                .role(credentials.getRole().name())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        try {
            RefreshToken token = refreshTokenService.verifyRefreshToken(refreshTokenStr);
            refreshTokenService.revokeToken(token);
        } catch (Exception e) {
            log.warn("[IdentityService] Logout token invalid or already revoked: {}", e.getMessage());
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<AuthCredentials> credsOpt = credentialsRepository.findByUsername(request.getEmail().trim().toLowerCase());
        if (credsOpt.isEmpty()) {
            log.info("[IdentityService] Forgot password requested for non-existing email: {}", request.getEmail());
            return;
        }

        AuthCredentials credentials = credsOpt.get();
        passwordResetTokenRepository.deleteByCredentials(credentials);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .credentials(credentials)
                .token(token)
                .expiryDate(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        passwordResetTokenRepository.save(resetToken);
        log.info("[IdentityService] Generated password reset token for: {}", credentials.getUsername());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Password reset token has expired");
        }

        AuthCredentials credentials = resetToken.getCredentials();
        credentials.setPassword(passwordEncoder.encode(request.getNewPassword()));
        credentialsRepository.save(credentials);

        passwordResetTokenRepository.delete(resetToken);
        refreshTokenService.revokeAllForCredentials(credentials.getId());
        log.info("[IdentityService] Successfully reset password for user: {}", credentials.getUsername());
    }

    @Transactional
    public AuthenticationResponse oauthLogin(OAuthLoginRequest request) {
        Optional<AuthCredentials> existing = credentialsRepository.findByOauthProviderAndOauthId(
                request.getProvider(), request.getOauthId());

        AuthCredentials credentials;
        if (existing.isPresent()) {
            credentials = existing.get();
        } else {
            String email = (request.getEmail() != null && !request.getEmail().isBlank())
                    ? request.getEmail().trim().toLowerCase()
                    : request.getProvider() + "_" + request.getOauthId() + "@oauth.bytevault.internal";

            Optional<AuthCredentials> byEmail = credentialsRepository.findByUsername(email);
            if (byEmail.isPresent()) {
                credentials = byEmail.get();
                credentials.setOauthProvider(request.getProvider());
                credentials.setOauthId(request.getOauthId());
                credentials = credentialsRepository.save(credentials);
            } else {
                credentials = AuthCredentials.builder()
                        .username(email)
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .role(Role.CUSTOMER)
                        .isActive(true)
                        .isEmailVerified(true)
                        .oauthProvider(request.getProvider())
                        .oauthId(request.getOauthId())
                        .build();
                credentials = credentialsRepository.save(credentials);

                userProfileService.createProfile(
                        credentials.getId(),
                        email,
                        request.getFullName(),
                        null,
                        null
                );
            }
        }

        String token = jwtService.generateToken(credentials);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(credentials);

        return AuthenticationResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .userId(credentials.getId())
                .email(credentials.getUsername())
                .role(credentials.getRole().name())
                .fullName(request.getFullName())
                .build();
    }
}
