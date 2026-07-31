package com.amx.grocerystore.core.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cloudflare R2 (S3 API) credentials and bucket settings. Values default to
 * empty in dev — the app must boot without R2 configured; uploads then fail
 * at request time, never at startup.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage.r2")
public record R2StorageProperties(
        String accountId,
        String accessKeyId,
        String secretAccessKey,
        String bucketName,
        String publicUrl
) {
}
