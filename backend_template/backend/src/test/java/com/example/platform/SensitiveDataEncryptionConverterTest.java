package com.example.platform;

import com.example.platform.common.crypto.SensitiveDataEncryptionConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SensitiveDataEncryptionConverterTest {

    private SensitiveDataEncryptionConverter converter;
    private final String rawKey = "dummy_encryption_key_must_be_32_bytes_long!";

    @BeforeEach
    public void setUp() {
        Environment env = Mockito.mock(Environment.class);
        Mockito.when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
        converter = new SensitiveDataEncryptionConverter(rawKey, env);
    }

    @Test
    public void testEncryptionAndDecryption() {
        String sensitiveValue = "SensitiveValue123";

        // Encrypt
        String encryptedValue = converter.convertToDatabaseColumn(sensitiveValue);
        assertNotNull(encryptedValue);
        assertNotEquals(sensitiveValue, encryptedValue);

        // Decrypt
        String decryptedValue = converter.convertToEntityAttribute(encryptedValue);
        assertNotNull(decryptedValue);
        assertEquals(sensitiveValue, decryptedValue);
    }
}
