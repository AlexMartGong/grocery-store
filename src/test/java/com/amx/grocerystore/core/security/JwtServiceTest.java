package com.amx.grocerystore.core.security;

import com.amx.grocerystore.admin.domain.AdminRole;
import com.amx.grocerystore.admin.domain.AdminUser;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1iYXNlNjQtMzItYnl0ZXMtbG9uZy1mb3ItaHMyNTYtYWxnb3JpdGhtLXh4";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60, 480, "grocery-store"));

    @Test
    void userAndAdminLifetimesAreIndependent() {
        assertThat(jwtService.getUserExpirationSeconds()).isEqualTo(3_600L);
        assertThat(jwtService.getAdminExpirationSeconds()).isEqualTo(28_800L);
    }

    @Test
    void storeUserToken_usesUserLifetime_andKeepsTenantClaims() {
        User user = User.builder()
                .id(UUID.randomUUID()).storeId(UUID.randomUUID())
                .username("cashier1").role(UserRole.CASHIER).active(true).build();

        Claims claims = jwtService.parse(jwtService.generateToken(user)).getPayload();

        assertThat(lifetimeSeconds(claims)).isEqualTo(3_600L);
        assertThat(claims.get(JwtService.CLAIM_STORE_ID, String.class)).isEqualTo(user.getStoreId().toString());
        assertThat(claims.get(JwtService.CLAIM_USER_ID, String.class)).isEqualTo(user.getId().toString());
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("CASHIER");
        assertThat(claims.get(JwtService.CLAIM_ADMIN_ID)).isNull();
    }

    @Test
    void adminToken_keepsAdminLifetime_andNeverCarriesStoreId() {
        AdminUser admin = AdminUser.builder()
                .id(UUID.randomUUID()).username("alex").role(AdminRole.SUPER_ADMIN).active(true).build();

        Claims claims = jwtService.parse(jwtService.generateAdminToken(admin)).getPayload();

        assertThat(lifetimeSeconds(claims)).isEqualTo(28_800L);
        assertThat(claims.get(JwtService.CLAIM_ADMIN_ID, String.class)).isEqualTo(admin.getId().toString());
        assertThat(claims.get(JwtService.CLAIM_STORE_ID)).isNull();
    }

    private static long lifetimeSeconds(Claims claims) {
        return (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
    }
}
