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

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
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

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }
}
