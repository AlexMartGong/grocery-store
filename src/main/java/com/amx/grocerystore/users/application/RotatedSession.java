package com.amx.grocerystore.users.application;

import com.amx.grocerystore.users.domain.User;

/**
 * Outcome of a successful refresh-token rotation: the freshly revalidated user (so the new access
 * token carries the current DB role) and the raw value of the child token for the cookie.
 */
public record RotatedSession(User user, String refreshToken) {
}
