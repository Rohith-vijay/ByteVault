package com.bytevault.common;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.crypto.SensitiveDataEncryptionConverter;
import com.bytevault.common.exception.*;
import com.bytevault.common.util.HtmlSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

public class CommonLibraryComprehensiveTest {

    private final SensitiveDataEncryptionConverter converter = new SensitiveDataEncryptionConverter();
    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("AES-GCM encryption handles large text, special characters, and emojis")
    void testAesEncryptionComplexInputs() {
        String input = "SensitiveData_#@!$%^&*()_+~`|}{[]:;?><,./ 12345 🚀 🔥";
        String encrypted = converter.convertToDatabaseColumn(input);

        assertNotNull(encrypted);
        assertNotEquals(input, encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(input, decrypted);
    }

    @Test
    @DisplayName("AES-GCM decryption handles corrupted and tampered ciphertext safely")
    void testAesDecryptionCorruptedCiphertext() {
        // Short corrupted base64
        String shortCiphertext = "YWJj"; // "abc"
        String result = converter.convertToEntityAttribute(shortCiphertext);
        // Fallback returns raw string without crashing
        assertNotNull(result);

        // Invalid base64 characters
        String invalidBase64 = "not-valid-base64-!@#$";
        String fallbackResult = converter.convertToEntityAttribute(invalidBase64);
        assertEquals(invalidBase64, fallbackResult);
    }

    @Test
    @DisplayName("HtmlSanitizer strips javascript: pseudo-protocols from links")
    void testHtmlSanitizerDangerousProtocols() {
        String dangerousLink = "<a href='javascript:alert(1)'>Click me</a>";
        String clean = HtmlSanitizer.sanitize(dangerousLink);

        assertNotNull(clean);
        assertFalse(clean.toLowerCase().contains("javascript:"));
    }

    @Test
    @DisplayName("HtmlSanitizer strips iframe, object, and embed tags")
    void testHtmlSanitizerForbiddenElements() {
        String input = "<iframe src='http://attacker.com'></iframe><object data='malware.swf'></object><embed src='test'>";
        String clean = HtmlSanitizer.sanitize(input);

        assertNotNull(clean);
        assertFalse(clean.contains("<iframe"));
        assertFalse(clean.contains("<object"));
        assertFalse(clean.contains("<embed"));
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps domain exceptions to standardized ApiResponse envelopes")
    void testGlobalExceptionHandler() {
        // ResourceNotFoundException -> 404
        ResponseEntity<ApiResponse<Void>> notFoundResp = exceptionHandler.handleResourceNotFound(
                new ResourceNotFoundException("Item not found"));
        assertEquals(HttpStatus.NOT_FOUND, notFoundResp.getStatusCode());
        assertFalse(notFoundResp.getBody().success());
        assertEquals("Item not found", notFoundResp.getBody().message());

        // BadRequestException -> 400
        ResponseEntity<ApiResponse<Void>> badReqResp = exceptionHandler.handleBadRequest(
                new BadRequestException("Invalid quantity"));
        assertEquals(HttpStatus.BAD_REQUEST, badReqResp.getStatusCode());
        assertFalse(badReqResp.getBody().success());

        // UnauthorizedException -> 401
        ResponseEntity<ApiResponse<Void>> unauthResp = exceptionHandler.handleUnauthorized(
                new UnauthorizedException("Invalid token"));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthResp.getStatusCode());
        assertFalse(unauthResp.getBody().success());

        // DuplicateResourceException -> 409
        ResponseEntity<ApiResponse<Void>> conflictResp = exceptionHandler.handleDuplicateResource(
                new DuplicateResourceException("Email exists"));
        assertEquals(HttpStatus.CONFLICT, conflictResp.getStatusCode());
        assertFalse(conflictResp.getBody().success());

        // AccessDeniedException -> 403
        ResponseEntity<ApiResponse<Void>> accessDeniedResp = exceptionHandler.handleAccessDenied(
                new org.springframework.security.access.AccessDeniedException("Forbidden action"));
        assertEquals(HttpStatus.FORBIDDEN, accessDeniedResp.getStatusCode());
        assertFalse(accessDeniedResp.getBody().success());
    }

    @Test
    @DisplayName("ApiResponse static factory methods create correct status codes and envelopes")
    void testApiResponseFactoryMethods() {
        ApiResponse<String> success = ApiResponse.success("Operation complete", "test-data");
        assertTrue(success.success());
        assertEquals("Operation complete", success.message());
        assertEquals("test-data", success.data());
        assertEquals(200, success.status());
        assertNotNull(success.timestamp());

        ApiResponse<Void> error = ApiResponse.error("Something went wrong", 500);
        assertFalse(error.success());
        assertEquals("Something went wrong", error.message());
        assertNull(error.data());
        assertEquals(500, error.status());
    }
}
