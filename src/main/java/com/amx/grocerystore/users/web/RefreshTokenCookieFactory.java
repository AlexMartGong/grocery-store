package com.amx.grocerystore.users.web;

import com.amx.grocerystore.core.security.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the store-user refresh cookie. Host-only (no {@code Domain}: only the API host needs it),
 * {@code HttpOnly} (unreachable from JS), {@code SameSite=Lax} (frontend {@code https://ventacore.app} and
 * API {@code https://api.ventacore.app} share the registrable domain {@code ventacore.app}, i.e. they are
 * same-site, so the cookie travels on {@code fetch}) and scoped to {@code /api/v1/auth} so it
 * travels only to login/refresh/logout — never to business endpoints or the admin login.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE = "Lax";

    private final RefreshTokenProperties properties;

    /** Persistent cookie: Max-Age equals the token lifetime, so it survives browser/PWA restarts. */
    public ResponseCookie issue(String refreshToken) {
        return base(refreshToken).maxAge(Duration.ofDays(properties.days())).build();
    }

    /** Deletion cookie: must repeat name, path and attributes of the issued one or browsers keep it. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH);
    }
}
