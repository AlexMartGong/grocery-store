package com.amx.grocerystore.stores.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAgentUrlRequest(
        @NotBlank
        @Size(max = 100)
        String url
) {
}
