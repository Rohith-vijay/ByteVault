package com.bytevault.product.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MinioStorageServiceTest {

    @Test
    @DisplayName("Local Fallback: uploadFile creates parent subdirectories when writing nested asset key")
    void testUploadFileCreatesParentDirectories(@TempDir Path tempDir) throws Exception {
        MinioStorageService storageService = new MinioStorageService();
        ReflectionTestUtils.setField(storageService, "isMinioEnabled", false);
        ReflectionTestUtils.setField(storageService, "fallbackDir", tempDir.toString());

        String key = "assets/" + UUID.randomUUID() + "/handbook.pdf";
        byte[] content = "PDF sample content".getBytes(StandardCharsets.UTF_8);

        String uploadedKey = storageService.uploadFile(
                key,
                "handbook.pdf",
                "application/pdf",
                content.length,
                new ByteArrayInputStream(content)
        );

        assertEquals(key, uploadedKey);
        StorageObject downloaded = storageService.downloadFile(uploadedKey);
        assertNotNull(downloaded);
        assertEquals(content.length, downloaded.getSize());
        if (downloaded.getInputStream() != null) {
            downloaded.getInputStream().close();
        }
    }

    @Test
    @DisplayName("Local Fallback: generatePresignedUrl produces signed URL with key, expires, and signature")
    void testGeneratePresignedUrl_LocalFallback_ContainsExpiresAndSignature() throws Exception {
        MinioStorageService storageService = new MinioStorageService();
        ReflectionTestUtils.setField(storageService, "isMinioEnabled", false);
        ReflectionTestUtils.setField(storageService, "signingSecret", "test-signing-secret-key-12345");

        String key = "assets/item123/file.pdf";
        String url = storageService.generatePresignedUrl(key, 15);

        assertNotNull(url);
        assertTrue(url.contains("key=" + key));
        assertTrue(url.contains("expires="));
        assertTrue(url.contains("signature="));
    }

    @Test
    @DisplayName("Local Fallback: validateSignedUrl succeeds with valid signature and unexpired timestamp")
    void testValidateSignedUrl_Valid_ReturnsTrue() throws Exception {
        MinioStorageService storageService = new MinioStorageService();
        ReflectionTestUtils.setField(storageService, "isMinioEnabled", false);
        ReflectionTestUtils.setField(storageService, "signingSecret", "test-signing-secret-key-12345");

        String key = "assets/item123/file.pdf";
        long futureExpiry = System.currentTimeMillis() + 600000; // 10 minutes ahead

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-signing-secret-key-12345".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((key + ":" + futureExpiry).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String validSig = sb.toString();

        assertTrue(storageService.validateSignedUrl(key, futureExpiry, validSig));
    }

    @Test
    @DisplayName("Local Fallback: validateSignedUrl rejects tampered signature or expired timestamp")
    void testValidateSignedUrl_RejectsInvalidOrExpired() throws Exception {
        MinioStorageService storageService = new MinioStorageService();
        ReflectionTestUtils.setField(storageService, "isMinioEnabled", false);
        ReflectionTestUtils.setField(storageService, "signingSecret", "test-signing-secret-key-12345");

        String key = "assets/item123/file.pdf";
        long futureExpiry = System.currentTimeMillis() + 600000;
        long pastExpiry = System.currentTimeMillis() - 1000;

        // Tampered signature
        assertFalse(storageService.validateSignedUrl(key, futureExpiry, "forged_signature_123456789"));

        // Missing signature or expiry
        assertFalse(storageService.validateSignedUrl(key, null, "sig"));
        assertFalse(storageService.validateSignedUrl(key, futureExpiry, null));
        assertFalse(storageService.validateSignedUrl(null, futureExpiry, "sig"));

        // Expired timestamp
        assertFalse(storageService.validateSignedUrl(key, pastExpiry, "valid_or_invalid"));
    }
}
