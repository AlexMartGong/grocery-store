package com.amx.grocerystore.users.web.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
}
