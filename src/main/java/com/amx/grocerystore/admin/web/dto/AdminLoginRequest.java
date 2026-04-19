package com.amx.grocerystore.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank String password
) {
}
