package com.amx.grocerystore.users.application;

import com.amx.grocerystore.users.web.dto.LoginResponse;

/**
 * Store-user session material: the JSON body (access token) plus the raw refresh token, which the
 * web layer moves into an httpOnly cookie and never serializes into the body.
 */
public record AuthSession(LoginResponse response, String refreshToken) {
}
