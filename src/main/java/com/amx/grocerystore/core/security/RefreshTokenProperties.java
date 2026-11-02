package com.amx.grocerystore.core.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Store-user refresh-token policy ({@code app.security.refresh.*}).
 *
 * @param days                 lifetime of each refresh token; sliding — every rotation issues a token with a fresh window
 * @param cookieSecure         {@code Secure} cookie attribute; keep {@code true} outside LAN dev over plain http
 * @param reuseGraceSeconds    a just-rotated token may rotate again within this window if its child was never used
 *                             (multi-tab / mobile-retry race) instead of being treated as reuse
 * @param cleanupRetentionDays expired/revoked rows older than this are purged by the cleanup job
 */
@Validated
@ConfigurationProperties(prefix = "app.security.refresh")
public record RefreshTokenProperties(
        @Min(1) int days,
        boolean cookieSecure,
        @Min(0) long reuseGraceSeconds,
        @Min(1) int cleanupRetentionDays
) {
}
