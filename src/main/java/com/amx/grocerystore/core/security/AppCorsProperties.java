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
}
