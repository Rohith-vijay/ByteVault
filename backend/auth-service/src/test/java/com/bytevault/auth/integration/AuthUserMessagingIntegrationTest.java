package com.bytevault.auth.integration;

import com.bytevault.auth.dto.*;
import com.bytevault.auth.entity.AuthCredentials;
import com.bytevault.auth.entity.RefreshToken;
import com.bytevault.auth.entity.Role;
import com.bytevault.auth.repository.AuthCredentialsRepository;
import com.bytevault.auth.repository.PasswordResetTokenRepository;
import com.bytevault.auth.security.JwtService;
import com.bytevault.auth.service.AuthService;
import com.bytevault.auth.service.EmailVerificationService;
import com.bytevault.auth.service.RefreshTokenService;
import com.bytevault.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthUserMessagingIntegrationTest {

    @Mock
    private AuthCredentialsRepository credentialsRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AuthService authService;

    private AuthCredentials testCredentials;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testCredentials = AuthCredentials.builder()
                .id(testUserId)
                .username("alex@bytevault.com")
                .password("hashed_secure_password")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Complete registration flow: Saves BCrypt user, generates tokens, and dispatches UserRegisteredEvent")
    void testRegistrationAndMessagingFlow() {
        RegisterRequest req = RegisterRequest.builder()
                .email("alex@bytevault.com")
                .password("Password123!")
                .fullName("Alex Merchant")
                .build();

        when(credentialsRepository.findByUsername("alex@bytevault.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_secure_password");
        when(credentialsRepository.save(any(AuthCredentials.class))).thenAnswer(i -> {
            AuthCredentials c = i.getArgument(0);
            c.setId(testUserId);
            return c;
        });
        when(jwtService.generateToken(any())).thenReturn("access_token_123");
        when(refreshTokenService.createRefreshToken(any())).thenReturn(RefreshToken.builder().token("refresh_abc").build());

        AuthenticationResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("access_token_123", resp.getToken());
        assertEquals("refresh_abc", resp.getRefreshToken());
        assertEquals("alex@bytevault.com", resp.getUser().getEmail());

        verify(credentialsRepository, times(1)).save(any(AuthCredentials.class));
        verify(rabbitTemplate, times(1)).convertAndSend(eq("user.exchange"), eq("user.registered"), any(Map.class));
    }

    @Test
    @DisplayName("Privilege escalation blocked: Attempting self-assignment of ADMIN throws AccessDeniedException")
    void testPrivilegeEscalationPrevention() {
        RegisterRequest req = RegisterRequest.builder()
                .email("hacker@bytevault.com")
                .password("Password123!")
                .fullName("Hacker Admin")
                .role("ADMIN")
                .build();

        when(credentialsRepository.findByUsername("hacker@bytevault.com")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> authService.register(req));
        verify(credentialsRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Duplicate registration throws DuplicateResourceException (409 Conflict)")
    void testDuplicateRegistration() {
        RegisterRequest req = RegisterRequest.builder()
                .email("alex@bytevault.com")
                .password("Password123!")
                .fullName("Alex Merchant")
                .build();

        when(credentialsRepository.findByUsername("alex@bytevault.com"))
                .thenReturn(Optional.of(testCredentials));

        assertThrows(DuplicateResourceException.class, () -> authService.register(req));
        verify(credentialsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login with valid credentials returns tokens; invalid credentials throws BadCredentialsException")
    void testLoginFlow() {
        AuthenticationRequest validReq = AuthenticationRequest.builder()
                .email("alex@bytevault.com")
                .password("Password123!")
                .build();

        when(credentialsRepository.findByUsername("alex@bytevault.com")).thenReturn(Optional.of(testCredentials));
        when(jwtService.generateToken(testCredentials)).thenReturn("access_token_123");
        when(refreshTokenService.createRefreshToken(testCredentials)).thenReturn(RefreshToken.builder().token("refresh_abc").build());

        AuthenticationResponse resp = authService.login(validReq);

        assertNotNull(resp);
        assertEquals("access_token_123", resp.getToken());
        assertEquals("refresh_abc", resp.getRefreshToken());

        // Test invalid password
        AuthenticationRequest invalidReq = AuthenticationRequest.builder()
                .email("alex@bytevault.com")
                .password("WrongPassword")
                .build();
        doThrow(new BadCredentialsException("Bad credentials")).when(authenticationManager).authenticate(any());

        assertThrows(BadCredentialsException.class, () -> authService.login(invalidReq));
    }
}
