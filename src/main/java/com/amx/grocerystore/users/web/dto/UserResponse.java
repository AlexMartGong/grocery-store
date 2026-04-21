package com.amx.grocerystore.users.web.dto;

import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String username,
        UserRole role,
        boolean active,
        OffsetDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
