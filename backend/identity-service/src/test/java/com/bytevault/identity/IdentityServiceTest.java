package com.bytevault.identity;

import com.bytevault.common.exception.DuplicateResourceException;
import com.bytevault.common.exception.UnauthorizedException;
import com.bytevault.identity.dto.*;
import com.bytevault.identity.entity.*;
import com.bytevault.identity.repository.AuthCredentialsRepository;
import com.bytevault.identity.repository.PasswordResetTokenRepository;
import com.bytevault.identity.security.JwtService;
import com.bytevault.identity.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

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
    private UserProfileService userProfileService;
    @Mock
    private VendorProfileService vendorProfileService;

    @InjectMocks
    private IdentityService identityService;

    private AuthCredentials customerCredentials;
    private AuthCredentials vendorCredentials;
    private RefreshToken mockRefreshToken;

    @BeforeEach
    void setUp() {
        UUID customerId = UUID.randomUUID();
        customerCredentials = AuthCredentials.builder()
                .id(customerId)
                .username("customer@bytevault.com")
                .password("encoded_pass")
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();

        UUID vendorId = UUID.randomUUID();
        vendorCredentials = AuthCredentials.builder()
                .id(vendorId)
                .username("vendor@bytevault.com")
                .password("encoded_pass")
                .role(Role.VENDOR)
                .isActive(true)
                .build();

        mockRefreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("mock-refresh-token")
                .credentials(customerCredentials)
                .build();

        org.mockito.Mockito.lenient().when(userProfileService.createProfile(any(), any(), any(), any(), any()))
                .thenAnswer(i -> UserProfile.builder()
                        .id(UUID.randomUUID())
                        .userId((UUID) i.getArgument(0))
                        .email((String) i.getArgument(1))
                        .fullName((String) i.getArgument(2))
                        .build());
    }

    @Test
    @DisplayName("Register Customer: creates credentials, synchronous UserProfile, no RabbitMQ call")
    void testRegisterCustomer_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("newcustomer@bytevault.com")
                .password("Password123!")
                .fullName("John Doe")
                .role("CUSTOMER")
                .build();

        when(credentialsRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_pass");
        when(credentialsRepository.save(any(AuthCredentials.class))).thenReturn(customerCredentials);
        when(jwtService.generateToken(any(AuthCredentials.class))).thenReturn("mock_jwt");
        when(refreshTokenService.createRefreshToken(any(AuthCredentials.class))).thenReturn(mockRefreshToken);

        AuthenticationResponse response = identityService.register(request);

        assertNotNull(response);
        assertEquals("mock_jwt", response.getToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals("CUSTOMER", response.getRole());

        // Verify synchronous UserProfile creation
        verify(userProfileService, times(1)).createProfile(
                eq(customerCredentials.getId()),
                eq(customerCredentials.getUsername()),
                eq("John Doe"),
                isNull(),
                isNull()
        );

        // Verify VendorProfile is NOT created for customer
        verify(vendorProfileService, never()).createPendingVendorProfile(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Register Vendor: creates credentials, UserProfile, and immediate PENDING VendorProfile")
    void testRegisterVendor_ImmediatePendingVendorProfile() {
        RegisterRequest request = RegisterRequest.builder()
                .email("vendor@bytevault.com")
                .password("Password123!")
                .fullName("Alice Vendor")
                .role("VENDOR")
                .storeName("Alice Digital Hub")
                .storeDescription("Creative 3D assets")
                .businessTaxId("TAX-999")
                .payoutInfo("Bank Transfer")
                .supportEmail("support@alicehub.com")
                .build();

        when(credentialsRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_pass");
        when(credentialsRepository.save(any(AuthCredentials.class))).thenReturn(vendorCredentials);
        when(jwtService.generateToken(any(AuthCredentials.class))).thenReturn("vendor_jwt");
        when(refreshTokenService.createRefreshToken(any(AuthCredentials.class))).thenReturn(mockRefreshToken);

        VendorProfile pendingProfile = VendorProfile.builder()
                .id(UUID.randomUUID())
                .userId(vendorCredentials.getId())
                .storeName("Alice Digital Hub")
                .status(VendorStatus.PENDING)
                .build();
        when(vendorProfileService.createPendingVendorProfile(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pendingProfile);

        AuthenticationResponse response = identityService.register(request);

        assertNotNull(response);
        assertEquals("vendor_jwt", response.getToken());
        assertEquals("VENDOR", response.getRole());

        // Verify synchronous UserProfile
        verify(userProfileService, times(1)).createProfile(
                eq(vendorCredentials.getId()),
                eq(vendorCredentials.getUsername()),
                eq("Alice Vendor"),
                isNull(),
                isNull()
        );

        // Verify synchronous PENDING VendorProfile creation
        verify(vendorProfileService, times(1)).createPendingVendorProfile(
                eq(vendorCredentials.getId()),
                eq(vendorCredentials.getUsername()),
                eq("Alice Digital Hub"),
                eq("Creative 3D assets"),
                eq("TAX-999"),
                eq("Bank Transfer"),
                eq("support@alicehub.com")
        );
    }

    @Test
    @DisplayName("Register Admin: direct administrator registration is strictly forbidden")
    void testRegisterAdmin_Forbidden() {
        RegisterRequest request = RegisterRequest.builder()
                .email("eviladmin@bytevault.com")
                .password("Password123!")
                .role("ADMIN")
                .build();

        when(credentialsRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> identityService.register(request));
        verify(credentialsRepository, never()).save(any());
    }

    @Test
    @DisplayName("Register Duplicate Email: throws DuplicateResourceException")
    void testRegister_DuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@bytevault.com")
                .password("Password123!")
                .build();

        when(credentialsRepository.findByUsername(anyString())).thenReturn(Optional.of(customerCredentials));

        assertThrows(DuplicateResourceException.class, () -> identityService.register(request));
    }

    @Test
    @DisplayName("Login Success: authenticates and returns tokens")
    void testLogin_Success() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("customer@bytevault.com")
                .password("Password123!")
                .build();

        when(credentialsRepository.findByUsername("customer@bytevault.com")).thenReturn(Optional.of(customerCredentials));
        when(jwtService.generateToken(customerCredentials)).thenReturn("access_token");
        when(refreshTokenService.createRefreshToken(customerCredentials)).thenReturn(mockRefreshToken);

        AuthenticationResponse response = identityService.login(request);

        assertNotNull(response);
        assertEquals("access_token", response.getToken());
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Login Failure: bad credentials throws UnauthorizedException")
    void testLogin_BadCredentials() {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("customer@bytevault.com")
                .password("WrongPass")
                .build();

        doThrow(new org.springframework.security.authentication.BadCredentialsException("Invalid"))
                .when(authenticationManager).authenticate(any());

        assertThrows(UnauthorizedException.class, () -> identityService.login(request));
    }
}
