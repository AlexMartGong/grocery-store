package com.amx.grocerystore.users.web;

import com.amx.grocerystore.core.web.exception.RefreshTokenException;
import com.amx.grocerystore.users.application.AuthService;
import com.amx.grocerystore.users.application.AuthSession;
import com.amx.grocerystore.users.web.dto.LoginRequest;
import com.amx.grocerystore.users.web.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store-user (OWNER/CASHIER) authentication. The access token travels in the JSON body; the refresh
 * token travels only in an httpOnly cookie. Clients must send {@code credentials: 'include'} on all
 * three endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withRefreshCookie(authService.login(request, userAgent));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw RefreshTokenException.invalid();
        }
        return withRefreshCookie(authService.refresh(refreshToken, userAgent));
    }

    /** Always 204 + deletion cookie, even when the cookie is missing, unknown or already revoked. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.issue(session.refreshToken()).toString())
                .body(session.response());
    }
}
