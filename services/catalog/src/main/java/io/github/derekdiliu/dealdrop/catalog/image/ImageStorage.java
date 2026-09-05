package io.github.derekdiliu.dealdrop.catalog.image;

import io.github.derekdiliu.dealdrop.catalog.ApiErrors;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Deal images live in object storage. Clients upload directly with a short-lived presigned URL,
 * so image bytes never pass through this service.
 */
@Service
public class ImageStorage {

    static final long MAX_BYTES = 5 * 1024 * 1024;
    static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(10);

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    ImageStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    /**
     * Content type and length are part of the signature, so the upload must match them exactly.
     */
    PresignedUpload presignUpload(String contentType, long contentLength) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw ApiErrors.badRequest("Images must be JPEG, PNG or WebP.");
        }
        if (contentLength <= 0 || contentLength > MAX_BYTES) {
            throw ApiErrors.badRequest("Images must be at most 5 MB.");
        }
        String key = "deals/" + UUID.randomUUID() + "." + extension;
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(put));
        return new PresignedUpload(key, presigned.url().toString(), "PUT",
                Map.of("Content-Type", contentType), presigned.expiration());
    }

    public boolean exists(String key) {
        try {
            s3.headObject(request -> request.bucket(properties.bucket()).key(key));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public String publicUrl(String key) {
        String base = properties.publicBaseUrl();
        return (base.endsWith("/") ? base : base + "/") + key;
    }

    record PresignedUpload(String key, String uploadUrl, String method, Map<String, String> headers,
                           Instant expiresAt) {
    }
}
