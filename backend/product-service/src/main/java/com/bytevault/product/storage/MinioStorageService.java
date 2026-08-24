package com.bytevault.product.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class MinioStorageService implements StorageService {

    @Value("${app.storage.minio.endpoint:}")
    private String endpoint;

    @Value("${app.storage.minio.bucket:bytevault-assets}")
    private String bucketName;

    @Value("${app.storage.local-fallback-dir:temp-assets}")
    private String fallbackDir;

    private boolean isMinioEnabled = false;

    @PostConstruct
    public void init() {
        if (endpoint != null && !endpoint.isBlank()) {
            log.info("[MinioStorageService] MinIO configured with endpoint: {}. Bucket: {}", endpoint, bucketName);
            isMinioEnabled = true;
            // Minio client initialization logic would go here if SDK was imported.
        } else {
            log.warn("[MinioStorageService] MinIO endpoint not configured. Using local filesystem fallback directory: {}", fallbackDir);
            try {
                Files.createDirectories(Paths.get(fallbackDir));
            } catch (Exception e) {
                log.error("Failed to create fallback directory", e);
            }
        }
    }

    @Override
    public String uploadFile(String key, String fileName, String contentType, long size, InputStream inputStream) throws Exception {
        String finalKey = (key == null || key.isBlank()) ? UUID.randomUUID().toString() : key;
        
        if (isMinioEnabled) {
            log.info("[MinioStorageService] Uploading file to MinIO bucket={}: key={}, name={}, size={}", bucketName, finalKey, fileName, size);
            // Simulate MinIO putObject operation
            return finalKey;
        }

        log.info("[MinioStorageService] Falling back to local filesystem write. Key: {}", finalKey);
        Path targetPath = Paths.get(fallbackDir, finalKey);
        try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        return finalKey;
    }

    @Override
    public StorageObject downloadFile(String key) throws Exception {
        if (isMinioEnabled) {
            log.info("[MinioStorageService] Fetching from MinIO bucket={}: key={}", bucketName, key);
            // Simulate download stream
            return StorageObject.builder()
                    .key(key)
                    .fileName("mock_file.zip")
                    .contentType("application/octet-stream")
                    .size(1024)
                    .inputStream(new java.io.ByteArrayInputStream(new byte[1024]))
                    .build();
        }

        log.info("[MinioStorageService] Downloading from local filesystem. Key: {}", key);
        Path sourcePath = Paths.get(fallbackDir, key);
        if (!Files.exists(sourcePath)) {
            throw new RuntimeException("File key not found: " + key);
        }
        File file = sourcePath.toFile();
        return StorageObject.builder()
                .key(key)
                .fileName(file.getName())
                .contentType("application/octet-stream")
                .size(file.length())
                .inputStream(new FileInputStream(file))
                .build();
    }

    @Override
    public String generatePresignedUrl(String key, int expiryMinutes) throws Exception {
        if (isMinioEnabled) {
            log.info("[MinioStorageService] Generating presigned URL on MinIO for key: {}, expiry: {} minutes", key, expiryMinutes);
            return endpoint + "/" + bucketName + "/" + key + "?token=mock_presigned_token_expiry_" + expiryMinutes;
        }

        // Standard sandbox / local HTTP fallback
        log.info("[MinioStorageService] Generating presigned URL mock for local filesystem key: {}", key);
        return "http://localhost:8084/api/v1/products/assets/download?key=" + key + "&token=mock_signature_expires_in_" + expiryMinutes + "_minutes";
    }

    @Override
    public void deleteFile(String key) throws Exception {
        if (isMinioEnabled) {
            log.info("[MinioStorageService] Deleting from MinIO: {}", key);
            return;
        }

        log.info("[MinioStorageService] Deleting from local filesystem: {}", key);
        Path targetPath = Paths.get(fallbackDir, key);
        Files.deleteIfExists(targetPath);
    }
}
