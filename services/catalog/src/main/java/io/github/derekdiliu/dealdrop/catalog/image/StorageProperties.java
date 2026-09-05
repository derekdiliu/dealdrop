package io.github.derekdiliu.dealdrop.catalog.image;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the S3-compatible image store (Cloudflare R2). All values are required,
 * so a missing setting fails startup with the property name rather than at first upload.
 */
@Validated
@ConfigurationProperties("dealdrop.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKeyId,
        @NotBlank String secretAccessKey,
        @NotBlank String publicBaseUrl) {
}
