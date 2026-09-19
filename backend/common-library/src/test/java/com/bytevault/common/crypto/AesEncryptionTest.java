package com.bytevault.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AesEncryptionTest {

    @Test
    @DisplayName("AES-GCM encryption and decryption round-trip succeeds")
    void testAesEncryptionAndDecryption() {
        SensitiveDataEncryptionConverter converter = new SensitiveDataEncryptionConverter();

        String plaintext = "ByteVault_Sensitive_CreditCard_Or_TaxId_123456789";
        String ciphertext = converter.convertToDatabaseColumn(plaintext);

        assertNotNull(ciphertext);
        assertNotEquals(plaintext, ciphertext);

        String decrypted = converter.convertToEntityAttribute(ciphertext);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Null or empty strings pass through unchanged")
    void testNullOrEmptyPassthrough() {
        SensitiveDataEncryptionConverter converter = new SensitiveDataEncryptionConverter();

        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertEquals("", converter.convertToEntityAttribute(""));
    }
}
