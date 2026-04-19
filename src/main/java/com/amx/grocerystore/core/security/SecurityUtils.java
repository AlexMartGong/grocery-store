package com.amx.grocerystore.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        return principal instanceof UserPrincipal up ? Optional.of(up) : Optional.empty();
    }

    public static UUID currentStoreId() {
        return currentPrincipal()
                .map(UserPrincipal::storeId)
                .orElseThrow(() -> new IllegalStateException("No authenticated tenant in security context"));
    }

    public static UUID currentUserId() {
        return currentPrincipal()
                .map(UserPrincipal::userId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
    }
}
