package com.amx.grocerystore.users.web.dto;

import com.amx.grocerystore.users.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Size(max = 150)
        String fullName,

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @NotNull
        UserRole role
) {
}
