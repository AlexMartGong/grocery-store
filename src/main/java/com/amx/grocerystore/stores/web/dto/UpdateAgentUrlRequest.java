package com.amx.grocerystore.stores.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAgentUrlRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^https?://.*", message = "Solo se permiten URLs seguras (http/https)")
        String url,

        @Size(max = 50)
        String currentVersion
) {
}
