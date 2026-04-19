package com.amx.grocerystore.users.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 30) String storeCode,
        @NotBlank @Size(max = 50) String username,
        @NotBlank String password
) {
}
