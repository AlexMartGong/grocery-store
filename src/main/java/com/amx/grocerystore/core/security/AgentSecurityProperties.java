package com.amx.grocerystore.core.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.agent")
public record AgentSecurityProperties(
        @NotBlank String apiKey
) {
}
