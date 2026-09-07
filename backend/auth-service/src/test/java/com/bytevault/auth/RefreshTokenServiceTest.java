package com.bytevault.auth;

import com.bytevault.auth.entity.AuthCredentials;
import com.bytevault.auth.entity.RefreshToken;
import com.bytevault.auth.entity.Role;
import com.bytevault.auth.repository.RefreshTokenRepository;
import com.bytevault.auth.service.RefreshTokenService;
import com.bytevault.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private AuthCredentials credentials;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 604800000L);
        credentials = AuthCredentials.builder()
                .id(UUID.randomUUID())
                .username("test@bytevault.com")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Create refresh token creates new family ID and persists token")
    void testCreateRefreshToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshToken token = refreshTokenService.createRefreshToken(credentials);

        assertNotNull(token);
        assertNotNull(token.getToken());
        assertNotNull(token.getFamilyId());
        assertFalse(token.isRevoked());
        assertEquals(credentials, token.getCredentials());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Rotate refresh token preserves the same family ID")
    void testRotateRefreshToken() {
        String familyId = "family-xyz-123";
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshToken rotated = refreshTokenService.rotateRefreshToken(credentials, familyId);

        assertNotNull(rotated);
        assertEquals(familyId, rotated.getFamilyId());
        assertFalse(rotated.isRevoked());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Token reuse attack detection revokes all tokens in the family")
    void testVerifyRefreshToken_TokenReuseAttack_RevokesFamily() {
        String familyId = "compromised-family";
        RefreshToken revokedToken = RefreshToken.builder()
                .token("stolen-token")
                .credentials(credentials)
                .familyId(familyId)
                .revoked(true)
                .expiryDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("stolen-token")).thenReturn(Optional.of(revokedToken));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> refreshTokenService.verifyRefreshToken("stolen-token"));

        assertTrue(exception.getMessage().contains("Session invalidated"));
        verify(refreshTokenRepository, times(1)).revokeAllByFamilyId(familyId);
    }

    @Test
    @DisplayName("Verify expired refresh token throws BadRequestException")
    void testVerifyRefreshToken_Expired() {
        RefreshToken expiredToken = RefreshToken.builder()
                .token("expired-token")
                .credentials(credentials)
                .familyId("family-1")
                .revoked(false)
                .expiryDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> refreshTokenService.verifyRefreshToken("expired-token"));

        assertTrue(exception.getMessage().contains("Refresh token expired"));
    }
}
