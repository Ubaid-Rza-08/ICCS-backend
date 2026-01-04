package com.ubaid.Auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Upload image to Cloudinary with optimizations for profile pictures
     * @param file MultipartFile to upload
//     * @param folder Folder name in Cloudinary
     * @return URL of uploaded image
     * @throws IOException if upload fails
     */

    public String uploadProductImage(MultipartFile file) throws IOException {
        // You can organize by date or just keep a flat structure in "products"
        return uploadImage(file, "products");
    }

    public String uploadImage(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        // Only validate file size (max 5MB) - no file type restrictions as requested
        if (!isValidFileSize(file, 5.0)) {
            throw new IllegalArgumentException("File size too large. Maximum allowed size is 5MB.");
        }

        // Generate unique public_id for the image
        String publicId = folder + "/" + UUID.randomUUID().toString();

        // FIXED: Removed nested transformation map and simplified upload options
        Map<String, Object> uploadOptions = ObjectUtils.asMap(
                "public_id", publicId,
                "folder", folder,
                "resource_type", "image",
                "quality", "auto:good", // Optimize quality automatically
                "fetch_format", "auto" // Auto format selection based on browser
        );

        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadOptions);
            String imageUrl = uploadResult.get("secure_url").toString();
            log.info("Image uploaded successfully to Cloudinary. Folder: {}, Size: {} MB, URL: {}",
                    folder, getFileSizeInMB(file), imageUrl);
            return imageUrl;
        } catch (Exception e) {
            log.error("Failed to upload image to Cloudinary. Folder: {}, Error: {}", folder, e.getMessage(), e);
            throw new IOException("Failed to upload image to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Upload user profile image to specific folder structure
     */
    public String uploadUserProfileImage(MultipartFile file, String userId) throws IOException {
        return uploadImage(file, "auth/users/profiles/" + userId);
    }

    /**
     * Delete image from Cloudinary using the image URL
     * @param imageUrl Full URL of the image to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            log.warn("Cannot delete image: URL is null or empty");
            return false;
        }

        // Skip deletion for non-Cloudinary URLs (like OAuth2 profile pictures)
        if (!imageUrl.contains("cloudinary.com")) {
            log.debug("Skipping deletion for non-Cloudinary URL: {}", imageUrl);
            return true; // Return true since it's not an error
        }

        try {
            String publicId = extractPublicIdFromUrl(imageUrl);
            if (publicId == null) {
                log.warn("Could not extract public_id from Cloudinary URL: {}", imageUrl);
                return false;
            }

            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String deletionResult = (String) result.get("result");
            boolean success = "ok".equals(deletionResult);

            if (success) {
                log.info("Image deleted successfully from Cloudinary: {}", imageUrl);
            } else {
                log.warn("Image deletion failed. URL: {}, Cloudinary result: {}", imageUrl, deletionResult);
            }

            return success;
        } catch (Exception e) {
            log.error("Error deleting image from Cloudinary. URL: {}, Error: {}", imageUrl, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extract public_id from Cloudinary URL for deletion
     * Handles various Cloudinary URL formats including transformed URLs
     */
    private String extractPublicIdFromUrl(String imageUrl) {
        try {
            if (imageUrl == null || !imageUrl.contains("cloudinary.com")) {
                return null;
            }

            // Split URL by forward slashes
            String[] urlParts = imageUrl.split("/");
            if (urlParts.length < 8) { // Minimum parts for a valid Cloudinary URL
                return null;
            }

            // Find the version part (starts with 'v' followed by numbers)
            int versionIndex = -1;
            for (int i = 0; i < urlParts.length; i++) {
                if (urlParts[i].startsWith("v") && urlParts[i].length() > 1) {
                    String versionPart = urlParts[i].substring(1);
                    if (versionPart.matches("\\d+")) {
                        versionIndex = i;
                        break;
                    }
                }
            }

            if (versionIndex == -1 || versionIndex + 1 >= urlParts.length) {
                log.warn("Could not find version part in Cloudinary URL: {}", imageUrl);
                return null;
            }

            // Reconstruct the public_id from parts after version
            StringBuilder publicId = new StringBuilder();
            for (int i = versionIndex + 1; i < urlParts.length; i++) {
                if (i > versionIndex + 1) {
                    publicId.append("/");
                }

                String part = urlParts[i];
                // Remove file extension from the last part
                if (i == urlParts.length - 1) {
                    int dotIndex = part.lastIndexOf('.');
                    if (dotIndex > 0) {
                        part = part.substring(0, dotIndex);
                    }
                }
                publicId.append(part);
            }

            String result = publicId.toString();
            log.debug("Extracted public_id: {} from URL: {}", result, imageUrl);
            return result;

        } catch (Exception e) {
            log.error("Error extracting public_id from Cloudinary URL: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * Generate transformation URL for resizing images
     * @param originalUrl Original Cloudinary URL
     * @param width Desired width
     * @param height Desired height
     * @return Transformed URL or original URL if transformation fails
     */
    public String getResizedImageUrl(String originalUrl, int width, int height) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            return null;
        }

        // Skip transformation for non-Cloudinary URLs
        if (!originalUrl.contains("cloudinary.com")) {
            return originalUrl;
        }

        try {
            // Create transformation string with face detection for better cropping
            String transformation = String.format("c_fill,w_%d,h_%d,g_face,q_auto,f_auto", width, height);

            // Insert transformation after '/upload/'
            String transformedUrl = originalUrl.replace("/upload/", "/upload/" + transformation + "/");
            log.debug("Generated resized URL: {} -> {}", originalUrl, transformedUrl);
            return transformedUrl;

        } catch (Exception e) {
            log.error("Error generating resized URL for: {}", originalUrl, e);
            return originalUrl; // Return original URL if transformation fails
        }
    }

    /**
     * Get circular profile image URL (perfect for avatars)
     * @param originalUrl Original image URL
     * @param size Diameter of the circular image
     * @return Circular image URL
     */
    public String getCircularProfileUrl(String originalUrl, int size) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            return null;
        }

        // Skip transformation for non-Cloudinary URLs
        if (!originalUrl.contains("cloudinary.com")) {
            return originalUrl;
        }

        try {
            // Create circular transformation with face detection
            String transformation = String.format("c_fill,w_%d,h_%d,r_max,g_face,q_auto,f_auto", size, size);

            String transformedUrl = originalUrl.replace("/upload/", "/upload/" + transformation + "/");
            log.debug("Generated circular URL: {} -> {}", originalUrl, transformedUrl);
            return transformedUrl;

        } catch (Exception e) {
            log.error("Error generating circular URL for: {}", originalUrl, e);
            return originalUrl;
        }
    }

    // Predefined sizes for profile images

    /**
     * Get thumbnail URL (100x100) - for navbar/small displays
     */
    public String getThumbnailUrl(String originalUrl) {
        return getResizedImageUrl(originalUrl, 100, 100);
    }

    /**
     * Get small size URL (150x150) - for user lists/cards
     */
    public String getSmallImageUrl(String originalUrl) {
        return getResizedImageUrl(originalUrl, 150, 150);
    }

    /**
     * Get medium size URL (300x300) - for profile pages
     */
    public String getMediumImageUrl(String originalUrl) {
        return getResizedImageUrl(originalUrl, 300, 300);
    }

    /**
     * Get large size URL (600x600) - for detailed view/editing
     */
    public String getLargeImageUrl(String originalUrl) {
        return getResizedImageUrl(originalUrl, 600, 600);
    }

    /**
     * Get circular thumbnail (100x100) - for avatars in UI
     */
    public String getCircularThumbnail(String originalUrl) {
        return getCircularProfileUrl(originalUrl, 100);
    }

    /**
     * Get circular small (150x150) - for user cards
     */
    public String getCircularSmall(String originalUrl) {
        return getCircularProfileUrl(originalUrl, 150);
    }

    /**
     * Get circular medium (200x200) - for profile headers
     */
    public String getCircularMedium(String originalUrl) {
        return getCircularProfileUrl(originalUrl, 200);
    }

    /**
     * Generate multiple sizes for a profile image URL
     * @param originalUrl Original image URL
     * @return Map containing different sized URLs
     */
    public Map<String, String> getProfileImageSizes(String originalUrl) {
        return ObjectUtils.asMap(
                "original", originalUrl,
                "thumbnail", getThumbnailUrl(originalUrl),    // 100x100
                "small", getSmallImageUrl(originalUrl),       // 150x150
                "medium", getMediumImageUrl(originalUrl),     // 300x300
                "large", getLargeImageUrl(originalUrl)        // 600x600
        );
    }

    /**
     * Get circular image variations
     * @param originalUrl Original image URL
     * @return Map containing circular image URLs
     */
    public Map<String, String> getCircularImageSizes(String originalUrl) {
        return ObjectUtils.asMap(
                "thumbnail", getCircularThumbnail(originalUrl),    // 100x100
                "small", getCircularSmall(originalUrl),            // 150x150
                "medium", getCircularMedium(originalUrl)           // 200x200
        );
    }

    /**
     * Get file size in MB
     * @param file File to check
     * @return Size in MB
     */
    public double getFileSizeInMB(MultipartFile file) {
        if (file == null) {
            return 0;
        }
        return file.getSize() / (1024.0 * 1024.0);
    }

    /**
     * Validate file size
     * @param file File to validate
     * @param maxSizeMB Maximum allowed size in MB
     * @return true if file size is within limit, false otherwise
     */
    public boolean isValidFileSize(MultipartFile file, double maxSizeMB) {
        double fileSizeMB = getFileSizeInMB(file);
        boolean isValid = fileSizeMB <= maxSizeMB;

        if (!isValid) {
            log.warn("File size too large: {} MB (max: {} MB)", fileSizeMB, maxSizeMB);
        }

        return isValid;
    }

    /**
     * Get image information from Cloudinary
     * @param imageUrl Cloudinary image URL
     * @return Map containing image information
     */
    public Map<String, Object> getImageInfo(String imageUrl) {
        if (imageUrl == null || !imageUrl.contains("cloudinary.com")) {
            return ObjectUtils.emptyMap();
        }

        try {
            String publicId = extractPublicIdFromUrl(imageUrl);
            if (publicId == null) {
                return ObjectUtils.emptyMap();
            }

            Map result = cloudinary.api().resource(publicId, ObjectUtils.emptyMap());
            log.debug("Retrieved image info for public_id: {}", publicId);
            return result;

        } catch (Exception e) {
            log.error("Error retrieving image info for URL: {}", imageUrl, e);
            return ObjectUtils.emptyMap();
        }
    }

    /**
     * Batch delete multiple images
     * @param imageUrls Array of image URLs to delete
     * @return Number of successfully deleted images
     */
    public int deleteImages(String... imageUrls) {
        if (imageUrls == null || imageUrls.length == 0) {
            return 0;
        }

        int deletedCount = 0;
        for (String imageUrl : imageUrls) {
            if (deleteImage(imageUrl)) {
                deletedCount++;
            }
        }

        log.info("Batch deletion completed: {}/{} images deleted successfully", deletedCount, imageUrls.length);
        return deletedCount;
    }

    /**
     * Check if URL is a Cloudinary URL
     * @param url URL to check
     * @return true if it's a Cloudinary URL
     */
    public boolean isCloudinaryUrl(String url) {
        return url != null && url.contains("cloudinary.com");
    }

    /**
     * Get optimized URL for web display
     * @param originalUrl Original image URL
     * @return Optimized URL with automatic format and quality
     */
    public String getOptimizedUrl(String originalUrl) {
        if (!isCloudinaryUrl(originalUrl)) {
            return originalUrl;
        }

        try {
            String transformation = "q_auto,f_auto";
            return originalUrl.replace("/upload/", "/upload/" + transformation + "/");
        } catch (Exception e) {
            log.error("Error generating optimized URL for: {}", originalUrl, e);
            return originalUrl;
        }
    }
}