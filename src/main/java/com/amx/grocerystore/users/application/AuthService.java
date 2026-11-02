package com.amx.grocerystore.users.application;

import com.amx.grocerystore.core.security.JwtService;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import com.amx.grocerystore.users.web.dto.LoginRequest;
import com.amx.grocerystore.users.web.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /** Read-write: a successful login persists a new refresh-token family. */
    @Transactional
    public AuthSession login(LoginRequest request, String userAgent) {
        Store store = storeRepository.findByCode(request.storeCode())
                .orElseThrow(() -> new BadCredentialsException("invalid_credentials"));

        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new DisabledException("store_inactive");
        }

        User user = userRepository
                .findByStoreIdAndUsernameAndActiveTrue(store.getId(), request.username())
                .orElseThrow(() -> new BadCredentialsException("invalid_credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid_credentials");
        }

        String refreshToken = refreshTokenService.issue(user, userAgent);
        return new AuthSession(accessTokenFor(user), refreshToken);
    }

    /**
     * Deliberately NOT {@code @Transactional}: {@link RefreshTokenService#rotate} is the single
     * transaction boundary and carries the {@code noRollbackFor} that keeps family revocations durable.
     * The access token is built from the revalidated DB user, so role changes apply on refresh.
     */
    public AuthSession refresh(String refreshToken, String userAgent) {
        RotatedSession rotated = refreshTokenService.rotate(refreshToken, userAgent);
        return new AuthSession(accessTokenFor(rotated.user()), rotated.refreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private LoginResponse accessTokenFor(User user) {
        return new LoginResponse(jwtService.generateToken(user), TOKEN_TYPE, jwtService.getUserExpirationSeconds());
    }
}
