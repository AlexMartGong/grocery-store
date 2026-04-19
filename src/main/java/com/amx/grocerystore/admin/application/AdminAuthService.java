package com.amx.grocerystore.admin.application;

import com.amx.grocerystore.admin.domain.AdminUser;
import com.amx.grocerystore.admin.infrastructure.AdminUserRepository;
import com.amx.grocerystore.admin.web.dto.AdminLoginRequest;
import com.amx.grocerystore.core.security.JwtService;
import com.amx.grocerystore.users.web.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(AdminLoginRequest request) {
        AdminUser admin = adminUserRepository.findByUsernameAndActiveTrue(request.username())
                .orElseThrow(() -> new BadCredentialsException("invalid_credentials"));

        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BadCredentialsException("invalid_credentials");
        }

        String token = jwtService.generateAdminToken(admin);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }
}
