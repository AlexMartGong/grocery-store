package com.amx.grocerystore.core.security;

import com.amx.grocerystore.admin.domain.AdminRole;

import java.util.UUID;

public record AdminPrincipal(
        UUID adminId,
        String username,
        AdminRole role
) {
}
