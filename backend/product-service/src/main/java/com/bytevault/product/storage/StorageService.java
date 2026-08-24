package com.bytevault.product.storage;

import java.io.InputStream;

public interface StorageService {
    String uploadFile(String key, String fileName, String contentType, long size, InputStream inputStream) throws Exception;
    StorageObject downloadFile(String key) throws Exception;
    String generatePresignedUrl(String key, int expiryMinutes) throws Exception;
    void deleteFile(String key) throws Exception;
}
