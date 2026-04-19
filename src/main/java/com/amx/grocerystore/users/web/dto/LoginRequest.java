package com.amx.grocerystore.users.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoginRequest(
        @NotNull UUID storeId,
        @NotBlank String username,
        @NotBlank String password
) {
}
