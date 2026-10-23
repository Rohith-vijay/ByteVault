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
}
