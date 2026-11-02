package com.amx.grocerystore.core.web.exception;

import lombok.Getter;

/**
 * Rejected store-user refresh token → 401. Thrown by the refresh flow after any revocation it
 * performs has been made durable (the throwing method declares {@code noRollbackFor}).
 */
@Getter
public class RefreshTokenException extends RuntimeException {

    private final String code;

    public RefreshTokenException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Missing, unknown or expired refresh token. */
    public static RefreshTokenException invalid() {
        return new RefreshTokenException("invalid_refresh_token",
                "Refresh token is missing, unknown or expired.");
    }

    /** An already rotated or revoked token was replayed; its whole family has been revoked. */
    public static RefreshTokenException reused() {
        return new RefreshTokenException("refresh_token_reused",
                "Refresh token was already used or revoked; the session has been revoked.");
    }
}
