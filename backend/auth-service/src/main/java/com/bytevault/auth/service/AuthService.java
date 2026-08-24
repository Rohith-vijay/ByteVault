package com.bytevault.auth.service;

import com.bytevault.auth.dto.AuthenticationRequest;
import com.bytevault.auth.dto.AuthenticationResponse;
import com.bytevault.auth.dto.RefreshTokenRequest;
import com.bytevault.auth.dto.RegisterRequest;
import com.bytevault.auth.entity.AuthCredentials;
import com.bytevault.auth.entity.RefreshToken;
import com.bytevault.auth.entity.Role;
import com.bytevault.auth.repository.AuthCredentialsRepository;
import com.bytevault.auth.security.JwtService;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;

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
        
        credentialsRepository.save(credentials);
        log.info("New auth credentials registered: {}", request.getEmail());
        
        try {
            emailVerificationService.sendVerificationEmail(credentials);
        } catch (Exception e) {
            log.error("Failed to send welcome email verification link for {}", credentials.getUsername(), e);
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
