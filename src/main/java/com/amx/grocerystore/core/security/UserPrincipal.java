package com.amx.grocerystore.core.security;

import com.amx.grocerystore.users.domain.UserRole;

import java.util.UUID;

public record UserPrincipal(
        UUID userId,
        UUID storeId,
        String username,
        UserRole role
) {
}
