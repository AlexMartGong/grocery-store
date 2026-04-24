package com.amx.grocerystore.users.web.dto;

import com.amx.grocerystore.users.domain.UserRole;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 150)
        String fullName,

        UserRole role,

        Boolean active,

        @Size(min = 8, max = 100)
        String password
) {
}
