package com.bytevault.product.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    @Autowired(required = false)
    private Cloudinary cloudinary;
    private final ImageOptimizer imageOptimizer;

    @Value("${cloudinary.folder-name:app_media}")
    private String folderName;

    public boolean isConfigured() {
        return cloudinary != null;
    }

    @SuppressWarnings("rawtypes")
    public Map<String, Object> uploadMedia(MultipartFile file, String mediaTypeStr) throws IOException {
        String resourceType = "VIDEO".equalsIgnoreCase(mediaTypeStr) ? "video" : "image";

        if (isConfigured()) {
            try {
                log.info("Cloudinary is active. Uploading {} file: {}", resourceType, file.getOriginalFilename());
                Map params = ObjectUtils.asMap(
                        "resource_type", resourceType,
                        "folder", folderName
                );

                Map uploadResult = cloudinary.uploader().upload(file.getBytes(), params);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("secure_url", uploadResult.get("secure_url"));
                metadata.put("public_id", uploadResult.get("public_id"));
                
                Integer width = (Integer) uploadResult.get("width");
                Integer height = (Integer) uploadResult.get("height");
                metadata.put("width", width);
                metadata.put("height", height);

                Double aspectRatio = null;
                if (uploadResult.containsKey("aspect_ratio")) {
                    Object ar = uploadResult.get("aspect_ratio");
                    if (ar instanceof Number) {
                        aspectRatio = ((Number) ar).doubleValue();
                    }
                }
                if (aspectRatio == null && width != null && height != null && height > 0) {
                    aspectRatio = (double) width / height;
                }
                metadata.put("aspect_ratio", aspectRatio);

                Double duration = null;
                if (uploadResult.containsKey("duration")) {
                    Object dur = uploadResult.get("duration");
                    if (dur instanceof Number) {
                        duration = ((Number) dur).doubleValue();
                    }
                }
                metadata.put("duration", duration);
                metadata.put("media_type", "video".equals(resourceType) ? "VIDEO" : "IMAGE");

                log.info("Cloudinary upload successful. public_id: {}, url: {}", metadata.get("public_id"), metadata.get("secure_url"));
                return metadata;
            } catch (Exception e) {
                log.error("Cloudinary upload failed for {}. Falling back to local storage...", file.getOriginalFilename(), e);
            }
        }

        log.warn("Cloudinary not configured or failed. Falling back to local storage for: {}", file.getOriginalFilename());
        return uploadLocalFallback(file, mediaTypeStr);
    }

    private Map<String, Object> uploadLocalFallback(MultipartFile file, String mediaTypeStr) throws IOException {
        String uploadDir = "uploads";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Map<String, Object> metadata = new HashMap<>();
        if ("VIDEO".equalsIgnoreCase(mediaTypeStr)) {
            String uniqueId = UUID.randomUUID().toString();
            String extension = "";
            String origName = file.getOriginalFilename();
            if (origName != null && origName.contains(".")) {
                extension = origName.substring(origName.lastIndexOf("."));
            }
            String filename = uniqueId + extension;
            File targetFile = new File(dir, filename);
            Files.copy(file.getInputStream(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            metadata.put("secure_url", "/uploads/" + filename);
            metadata.put("public_id", uniqueId);
            metadata.put("media_type", "VIDEO");
        } else {
            String webpUrl = imageOptimizer.optimizeAndSave(file, uploadDir);
            String publicId = webpUrl.substring(webpUrl.lastIndexOf("/") + 1, webpUrl.lastIndexOf("."));
            metadata.put("secure_url", webpUrl);
            metadata.put("public_id", publicId);
            metadata.put("media_type", "IMAGE");
        }

        return metadata;
    }
}
