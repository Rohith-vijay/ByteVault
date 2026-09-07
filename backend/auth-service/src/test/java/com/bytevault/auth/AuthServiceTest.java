package com.bytevault.auth;

import com.bytevault.auth.dto.*;
import com.bytevault.auth.entity.AuthCredentials;
import com.bytevault.auth.entity.PasswordResetToken;
import com.bytevault.auth.entity.RefreshToken;
import com.bytevault.auth.entity.Role;
import com.bytevault.auth.repository.AuthCredentialsRepository;
import com.bytevault.auth.repository.PasswordResetTokenRepository;
import com.bytevault.auth.security.JwtService;
import com.bytevault.auth.service.AuthService;
import com.bytevault.auth.service.EmailVerificationService;
import com.bytevault.auth.service.RefreshTokenService;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.DuplicateResourceException;
import com.bytevault.common.exception.UnauthorizedException;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

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

    @Test
    @DisplayName("User registration succeeds and creates credentials")
    void testRegister_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("newuser@bytevault.com")
                .password("StrongPassword123")
                .fullName("John Doe")
                .build();

        when(credentialsRepository.findByUsername(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded_hash");
        when(credentialsRepository.save(any(AuthCredentials.class))).thenAnswer(i -> {
            AuthCredentials c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(jwtService.generateToken(any())).thenReturn("mock_jwt_token");
        when(refreshTokenService.createRefreshToken(any())).thenReturn(RefreshToken.builder().token("mock_refresh").build());

        AuthenticationResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock_jwt_token", response.getToken());
        assertEquals("mock_refresh", response.getRefreshToken());
        assertEquals("newuser@bytevault.com", response.getUser().getEmail());

        verify(credentialsRepository, times(1)).save(any(AuthCredentials.class));
    }

    @Test
    @DisplayName("Duplicate registration -> Throws DuplicateResourceException")
    void testRegister_DuplicateEmail_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@bytevault.com")
                .password("Password123")
                .build();

        when(credentialsRepository.findByUsername(request.getEmail()))
                .thenReturn(Optional.of(AuthCredentials.builder().username(request.getEmail()).build()));

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(credentialsRepository, never()).save(any(AuthCredentials.class));
    }

    @Test
    @DisplayName("Admin role self-registration attempt -> Throws AccessDeniedException")
    void testRegister_AdminRole_ThrowsAccessDeniedException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("hacker@bytevault.com")
                .password("Password123")
                .role("ADMIN")
                .build();

        when(credentialsRepository.findByUsername(request.getEmail())).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("Login with valid credentials -> Returns tokens")
    void testLogin_Success() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("user@bytevault.com")
                .password("ValidPassword")
                .build();

        AuthCredentials credentials = AuthCredentials.builder()
                .id(UUID.randomUUID())
                .username("user@bytevault.com")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();

        when(credentialsRepository.findByUsername(request.getEmail())).thenReturn(Optional.of(credentials));
        when(jwtService.generateToken(credentials)).thenReturn("access_token_123");
        when(refreshTokenService.createRefreshToken(credentials)).thenReturn(RefreshToken.builder().token("refresh_123").build());

        AuthenticationResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access_token_123", response.getToken());
        assertEquals("refresh_123", response.getRefreshToken());
        verify(authenticationManager, times(1)).authenticate(any());
    }

    @Test
    @DisplayName("Login with invalid password -> Throws BadCredentialsException")
    void testLogin_BadCredentials() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("user@bytevault.com")
                .password("WrongPassword")
                .build();

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Login with inactive account -> Throws UnauthorizedException")
    void testLogin_InactiveAccount() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("inactive@bytevault.com")
                .password("Password123")
                .build();

        AuthCredentials credentials = AuthCredentials.builder()
                .username("inactive@bytevault.com")
                .isActive(false)
                .build();

        when(credentialsRepository.findByUsername(request.getEmail())).thenReturn(Optional.of(credentials));

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Forgot password token generation (Safe response preventing account enumeration)")
    void testForgotPassword() {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("user@bytevault.com")
                .build();

        AuthCredentials credentials = AuthCredentials.builder()
                .id(UUID.randomUUID())
                .username("user@bytevault.com")
                .build();

        when(credentialsRepository.findByUsername(request.getEmail())).thenReturn(Optional.of(credentials));

        authService.forgotPassword(request);

        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Reset password with valid token -> Updates hashed password and marks token used")
    void testResetPassword_Success() {
        AuthCredentials credentials = AuthCredentials.builder()
                .id(UUID.randomUUID())
                .username("user@bytevault.com")
                .password("old_hash")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid_reset_token")
                .credentials(credentials)
                .expiryDate(Instant.now().plus(1, ChronoUnit.HOURS))
                .used(false)
                .build();

        when(passwordResetTokenRepository.findByToken("valid_reset_token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecretPassword123")).thenReturn("new_hash");

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid_reset_token")
                .newPassword("NewSecretPassword123")
                .build();

        authService.resetPassword(request);

        assertEquals("new_hash", credentials.getPassword());
        assertTrue(token.isUsed());
        verify(credentialsRepository, times(1)).save(credentials);
        verify(refreshTokenService, times(1)).revokeAllForCredentials(credentials.getId());
    }

    @Test
    @DisplayName("Reset password with expired or used token -> Throws BadRequestException")
    void testResetPassword_ExpiredOrUsed_ThrowsBadRequestException() {
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .token("used_token")
                .used(true)
                .expiryDate(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(passwordResetTokenRepository.findByToken("used_token")).thenReturn(Optional.of(usedToken));

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("used_token")
                .newPassword("NewSecretPassword123")
                .build();

        assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
    }

    @Test
    @DisplayName("OAuth login with valid ID token auto-provisions new customer account")
    void testOAuthLogin_WithValidIdToken_Success() {
        String validIdToken = "eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6Im9hdXRoLnVzZXJAZ21haWwuY29tIiwibmFtZSI6Ik9BdXRoIFVzZXIiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjo0MTAyNDQ0ODAwfQ.mock_sig";
        OAuthLoginRequest request = OAuthLoginRequest.builder()
                .email("oauth.user@gmail.com")
                .name("OAuth User")
                .provider("GOOGLE")
                .providerId("google-12345")
                .idToken(validIdToken)
                .build();

        when(credentialsRepository.findByUsername(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("oauth_random_hash");
        when(credentialsRepository.save(any(AuthCredentials.class))).thenAnswer(i -> {
            AuthCredentials c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(jwtService.generateToken(any())).thenReturn("oauth_jwt");
        when(refreshTokenService.createRefreshToken(any())).thenReturn(RefreshToken.builder().token("oauth_refresh").build());

        AuthenticationResponse response = authService.oauthLogin(request);

        assertNotNull(response);
        assertEquals("oauth_jwt", response.getToken());
        assertEquals(Role.CUSTOMER.name(), response.getUser().getRole());
        verify(credentialsRepository, times(1)).save(any(AuthCredentials.class));
    }

    @Test
    @DisplayName("OAuth login without ID token -> Throws UnauthorizedException (prevents forged identity)")
    void testOAuthLogin_MissingIdToken_ThrowsUnauthorizedException() {
        OAuthLoginRequest request = OAuthLoginRequest.builder()
                .email("forged.user@gmail.com")
                .name("Forged User")
                .provider("GOOGLE")
                .idToken(null)
                .build();

        assertThrows(UnauthorizedException.class, () -> authService.oauthLogin(request));
        verify(credentialsRepository, never()).save(any(AuthCredentials.class));
    }
}
