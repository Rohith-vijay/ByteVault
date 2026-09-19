package com.bytevault.common.integration;

import com.bytevault.common.crypto.SensitiveDataEncryptionConverter;
import com.bytevault.common.security.DownstreamSecurityFilter;
import com.bytevault.common.util.HtmlSanitizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CrossServiceSecurityBoundaryTest {

    private DownstreamSecurityFilter filter;
    private final String gatewaySecret = "platform_super_secure_gateway_secret_123456";

    @BeforeEach
    void setUp() {
        filter = new DownstreamSecurityFilter();
        ReflectionTestUtils.setField(filter, "gatewaySecret", gatewaySecret);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Direct downstream call without valid X-Gateway-Secret is rejected with HTTP 403 Forbidden")
    void testDirectDownstreamCallRejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentAsString().contains("Access Forbidden: Direct calls not allowed"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Downstream request from API Gateway with valid secret populates Spring SecurityContext")
    void testValidGatewayRequestEstablishesSecurityContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/123");
        request.addHeader("X-Gateway-Secret", gatewaySecret);
        request.addHeader("X-User-Id", "11111111-2222-3333-4444-555555555555");
        request.addHeader("X-User-Roles", "ROLE_CUSTOMER");
        request.addHeader("X-User-Email", "testuser@bytevault.com");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(chain, times(1)).doFilter(request, response);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("testuser@bytevault.com", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")));
    }

    @Test
    @DisplayName("AES-256-GCM encryption produces randomized ciphertext due to dynamic IV")
    void testAesGcmDynamicIvRandomization() {
        SensitiveDataEncryptionConverter converter = new SensitiveDataEncryptionConverter();

        String plaintext = "+1 (555) 234-5678";
        String cipher1 = converter.convertToDatabaseColumn(plaintext);
        String cipher2 = converter.convertToDatabaseColumn(plaintext);

        assertNotNull(cipher1);
        assertNotNull(cipher2);
        // Ciphertexts must differ because each encryption uses a fresh secure random IV
        assertNotEquals(cipher1, cipher2);

        // Both decrypt back to identical original plaintext
        assertEquals(plaintext, converter.convertToEntityAttribute(cipher1));
        assertEquals(plaintext, converter.convertToEntityAttribute(cipher2));
    }

    @Test
    @DisplayName("HtmlSanitizer eliminates XSS vectors, javascript URIs, and dangerous attributes")
    void testXssSanitization() {
        String dirtyHtml = "<script>alert('pwned')</script><img src='x' onerror='stealCookie()'><a href='javascript:evil()'>Click</a><p>Safe content</p>";
        String clean = HtmlSanitizer.sanitize(dirtyHtml);

        assertFalse(clean.contains("<script>"));
        assertFalse(clean.contains("onerror"));
        assertFalse(clean.contains("javascript:"));
        assertTrue(clean.contains("<p>Safe content</p>"));
    }
}
