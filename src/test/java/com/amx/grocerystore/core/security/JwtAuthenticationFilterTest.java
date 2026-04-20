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
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "jwt.token.here";
    private static final String BEARER = "Bearer " + TOKEN;

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private JwtAuthenticationEntryPoint entryPoint;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    @Mock
    @SuppressWarnings("rawtypes")
    private Jws jws;
    @Mock
    private Claims claims;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtService, userRepository, storeRepository, adminUserRepository, entryPoint);
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_passesThrough() throws Exception {
        given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedJwt_rejectsInvalidToken() throws Exception {
        givenBearer();
        given(jwtService.parse(TOKEN)).willThrow(new JwtException("bad"));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        ArgumentCaptor<AuthenticationException> captor = ArgumentCaptor.forClass(AuthenticationException.class);
        verify(entryPoint).commence(eq(request), eq(response), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InsufficientAuthenticationException.class);
        assertThat(captor.getValue().getMessage()).isEqualTo("invalid_token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void userToken_happyPath_setsTenantAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        givenUserClaims(userId, storeId, UserRole.OWNER, "owner");
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser(userId, storeId)));
        given(storeRepository.findById(storeId)).willReturn(Optional.of(activeStore(storeId)));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(TenantAuthenticationToken.class);
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.storeId()).isEqualTo(storeId);
        assertThat(principal.role()).isEqualTo(UserRole.OWNER);
        verify(chain).doFilter(request, response);
    }

    @Test
    void userToken_userDeactivated_rejectsUserInactive() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        givenUserClaims(userId, storeId, UserRole.CASHIER, "cash");
        User user = activeUser(userId, storeId);
        user.setActive(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        assertRejected("user_inactive");
    }

    @Test
    void userToken_userMissingInDb_rejectsUserInactive() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        givenUserClaims(userId, storeId, UserRole.OWNER, "owner");
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertRejected("user_inactive");
    }

    @Test
    void userToken_storeSuspended_rejectsStoreInactive() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        givenUserClaims(userId, storeId, UserRole.OWNER, "owner");
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser(userId, storeId)));
        Store store = activeStore(storeId);
        store.setStatus(StoreStatus.SUSPENDED);
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store));

        filter.doFilter(request, response, chain);

        assertRejected("store_inactive");
    }

    @Test
    void userToken_storeCanceled_rejectsStoreInactive() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        givenUserClaims(userId, storeId, UserRole.OWNER, "owner");
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser(userId, storeId)));
        Store store = activeStore(storeId);
        store.setStatus(StoreStatus.CANCELED);
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store));

        filter.doFilter(request, response, chain);

        assertRejected("store_inactive");
    }

    @Test
    void userToken_storeMissingInDb_rejectsStoreInactive() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        givenUserClaims(userId, storeId, UserRole.OWNER, "owner");
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser(userId, storeId)));
        given(storeRepository.findById(storeId)).willReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertRejected("store_inactive");
    }

    @Test
    void adminToken_happyPath_setsAdminAuthentication() throws Exception {
        UUID adminId = UUID.randomUUID();
        givenAdminClaims(adminId, "alex");
        given(adminUserRepository.findById(adminId)).willReturn(Optional.of(activeAdmin(adminId)));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(AdminAuthenticationToken.class);
        AdminPrincipal principal = (AdminPrincipal) auth.getPrincipal();
        assertThat(principal.adminId()).isEqualTo(adminId);
        assertThat(principal.role()).isEqualTo(AdminRole.SUPER_ADMIN);
        verify(chain).doFilter(request, response);
    }

    @Test
    void adminToken_adminDeactivated_rejectsAdminInactive() throws Exception {
        UUID adminId = UUID.randomUUID();
        givenAdminClaims(adminId, "alex");
        AdminUser admin = activeAdmin(adminId);
        admin.setActive(false);
        given(adminUserRepository.findById(adminId)).willReturn(Optional.of(admin));

        filter.doFilter(request, response, chain);

        assertRejected("admin_inactive");
    }

    @Test
    void adminToken_adminMissingInDb_rejectsAdminInactive() throws Exception {
        UUID adminId = UUID.randomUUID();
        givenAdminClaims(adminId, "alex");
        given(adminUserRepository.findById(adminId)).willReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertRejected("admin_inactive");
    }

    private void givenBearer() {
        given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(BEARER);
    }

    @SuppressWarnings("unchecked")
    private void givenUserClaims(UUID userId, UUID storeId, UserRole role, String subject) {
        givenBearer();
        given(jwtService.parse(TOKEN)).willReturn(jws);
        given(jws.getPayload()).willReturn(claims);
        given(claims.get(JwtService.CLAIM_ADMIN_ID, String.class)).willReturn(null);
        given(claims.get(JwtService.CLAIM_USER_ID, String.class)).willReturn(userId.toString());
        given(claims.get(JwtService.CLAIM_ROLE, String.class)).willReturn(role.name());
        given(claims.getSubject()).willReturn(subject);
    }

    @SuppressWarnings("unchecked")
    private void givenAdminClaims(UUID adminId, String subject) {
        givenBearer();
        given(jwtService.parse(TOKEN)).willReturn(jws);
        given(jws.getPayload()).willReturn(claims);
        given(claims.get(JwtService.CLAIM_ADMIN_ID, String.class)).willReturn(adminId.toString());
        given(claims.get(JwtService.CLAIM_ROLE, String.class)).willReturn(AdminRole.SUPER_ADMIN.name());
        given(claims.getSubject()).willReturn(subject);
    }

    private void assertRejected(String expectedCode) throws Exception {
        verify(chain, never()).doFilter(any(), any());
        ArgumentCaptor<AuthenticationException> captor = ArgumentCaptor.forClass(AuthenticationException.class);
        verify(entryPoint).commence(eq(request), eq(response), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(AccountInactiveException.class);
        assertThat(((AccountInactiveException) captor.getValue()).getCode()).isEqualTo(expectedCode);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private User activeUser(UUID userId, UUID storeId) {
        return User.builder()
                .id(userId)
                .storeId(storeId)
                .fullName("Owner")
                .username("owner")
                .passwordHash("hash")
                .role(UserRole.OWNER)
                .active(true)
                .build();
    }

    private Store activeStore(UUID storeId) {
        return Store.builder()
                .id(storeId)
                .code("tienda-demo")
                .name("Demo")
                .status(StoreStatus.ACTIVE)
                .build();
    }

    private AdminUser activeAdmin(UUID adminId) {
        return AdminUser.builder()
                .id(adminId)
                .username("alex")
                .passwordHash("hash")
                .fullName("Alex")
                .role(AdminRole.SUPER_ADMIN)
                .active(true)
                .build();
    }
}
