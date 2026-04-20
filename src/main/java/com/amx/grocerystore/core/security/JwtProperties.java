package com.amx.grocerystore.core.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Min(1) long expirationMinutes,
        @NotBlank String issuer
) {
    public JwtProperties {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.jwt.secret must be Base64-encoded", e);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must decode to >= 32 bytes for HS256 (got " + decoded.length + ")");
        }
    }
}
