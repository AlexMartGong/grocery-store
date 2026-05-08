package com.amx.grocerystore.core.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record AppCorsProperties(
        @NotEmpty List<String> allowedOrigins,
        @NotEmpty List<String> allowedMethods,
        @NotEmpty List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        @Min(0) long maxAge
) {
    public AppCorsProperties {
        if (allowCredentials && allowedOrigins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException(
                    "app.cors: allowCredentials=true is incompatible with wildcard origin");
        }
        allowedOrigins.forEach(o -> {
            if (!o.startsWith("https://") && !o.startsWith("http://localhost") && !o.startsWith("http://192.168.68.")) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins must use https:// (or http://localhost): " + o);
            }
        });
    }
}
