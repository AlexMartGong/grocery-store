package com.amx.grocerystore.core.security;

import com.amx.grocerystore.admin.domain.AdminRole;
import com.amx.grocerystore.admin.domain.AdminUser;
import com.amx.grocerystore.admin.infrastructure.AdminUserRepository;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final AdminUserRepository adminUserRepository;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parse(token).getPayload();
            Authentication authentication = claims.get(JwtService.CLAIM_ADMIN_ID, String.class) != null
                    ? revalidateAdmin(claims)
                    : revalidateUser(claims);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            reject(request, response, new InsufficientAuthenticationException("invalid_token"));
            return;
        } catch (AccountInactiveException ex) {
            reject(request, response, ex);
            return;
        }

        chain.doFilter(request, response);
    }

    private Authentication revalidateUser(Claims claims) {
        UUID userId = UUID.fromString(claims.get(JwtService.CLAIM_USER_ID, String.class));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountInactiveException("user_inactive"));
        if (!user.isActive()) {
            throw new AccountInactiveException("user_inactive");
        }
        Store store = storeRepository.findById(user.getStoreId())
                .orElseThrow(() -> new AccountInactiveException("store_inactive"));
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new AccountInactiveException("store_inactive");
        }
        UserPrincipal principal = new UserPrincipal(
                userId,
                user.getStoreId(),
                claims.getSubject(),
                UserRole.valueOf(claims.get(JwtService.CLAIM_ROLE, String.class))
        );
        return new TenantAuthenticationToken(principal);
    }

    private Authentication revalidateAdmin(Claims claims) {
        UUID adminId = UUID.fromString(claims.get(JwtService.CLAIM_ADMIN_ID, String.class));
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new AccountInactiveException("admin_inactive"));
        if (!admin.isActive()) {
            throw new AccountInactiveException("admin_inactive");
        }
        AdminPrincipal principal = new AdminPrincipal(
                adminId,
                claims.getSubject(),
                AdminRole.valueOf(claims.get(JwtService.CLAIM_ROLE, String.class))
        );
        return new AdminAuthenticationToken(principal);
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException ex) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, ex);
    }
}
