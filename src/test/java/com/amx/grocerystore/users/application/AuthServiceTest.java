package com.amx.grocerystore.users.application;

import com.amx.grocerystore.core.security.JwtService;
import com.amx.grocerystore.core.web.exception.RefreshTokenException;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import com.amx.grocerystore.users.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID STORE_ID = UUID.randomUUID();
    private static final LoginRequest LOGIN = new LoginRequest("tienda-demo", "cashier1", "secret");

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User user(UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .storeId(STORE_ID)
                .fullName("Cajero")
                .username("cashier1")
                .passwordHash("HASH")
                .role(role)
                .active(true)
                .build();
    }

    private void givenStore(StoreStatus status) {
        given(storeRepository.findByCode("tienda-demo"))
                .willReturn(Optional.of(Store.builder().id(STORE_ID).code("tienda-demo").status(status).build()));
    }

    @Test
    void login_validCredentials_returnsAccessTokenAndIssuesRefreshToken() {
        User user = user(UserRole.CASHIER);
        givenStore(StoreStatus.ACTIVE);
        given(userRepository.findByStoreIdAndUsernameAndActiveTrue(STORE_ID, "cashier1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("secret", "HASH")).willReturn(true);
        given(refreshTokenService.issue(user, "UA")).willReturn("raw-refresh");
        given(jwtService.generateToken(user)).willReturn("jwt");
        given(jwtService.getUserExpirationSeconds()).willReturn(3600L);

        AuthSession session = authService.login(LOGIN, "UA");

        assertThat(session.refreshToken()).isEqualTo("raw-refresh");
        assertThat(session.response().token()).isEqualTo("jwt");
        assertThat(session.response().tokenType()).isEqualTo("Bearer");
        assertThat(session.response().expiresInSeconds()).isEqualTo(3600L);
        verify(jwtService, never()).getAdminExpirationSeconds();
    }

    @Test
    void login_wrongPassword_issuesNoRefreshToken() {
        User user = user(UserRole.CASHIER);
        givenStore(StoreStatus.ACTIVE);
        given(userRepository.findByStoreIdAndUsernameAndActiveTrue(STORE_ID, "cashier1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("secret", "HASH")).willReturn(false);

        assertThatThrownBy(() -> authService.login(LOGIN, "UA")).isInstanceOf(BadCredentialsException.class);
        verify(refreshTokenService, never()).issue(any(), any());
    }

    @Test
    void login_storeSuspended_issuesNoRefreshToken() {
        givenStore(StoreStatus.SUSPENDED);

        assertThatThrownBy(() -> authService.login(LOGIN, "UA"))
                .isInstanceOf(DisabledException.class)
                .hasMessage("store_inactive");
        verify(refreshTokenService, never()).issue(any(), any());
    }

    @Test
    void login_isReadWriteTransaction() throws Exception {
        Transactional tx = AuthService.class
                .getMethod("login", LoginRequest.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).as("login INSERTs the refresh token").isFalse();
    }

    @Test
    void refresh_buildsAccessTokenFromRevalidatedDbUser() {
        User promoted = user(UserRole.OWNER);
        given(refreshTokenService.rotate("old-raw", "UA")).willReturn(new RotatedSession(promoted, "new-raw"));
        given(jwtService.generateToken(promoted)).willReturn("fresh-jwt");
        given(jwtService.getUserExpirationSeconds()).willReturn(3600L);

        AuthSession session = authService.refresh("old-raw", "UA");

        assertThat(session.response().token()).isEqualTo("fresh-jwt");
        assertThat(session.response().expiresInSeconds()).isEqualTo(3600L);
        assertThat(session.refreshToken()).isEqualTo("new-raw");
        verify(jwtService).generateToken(promoted);
    }

    @Test
    void refresh_propagatesRejection() {
        given(refreshTokenService.rotate("old-raw", null)).willThrow(RefreshTokenException.reused());

        assertThatThrownBy(() -> authService.refresh("old-raw", null))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("code").isEqualTo("refresh_token_reused");
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void refresh_isNotTransactional_rotateOwnsTheBoundary() throws Exception {
        assertThat(AuthService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(AuthService.class.getMethod("refresh", String.class, String.class)
                .isAnnotationPresent(Transactional.class))
                .as("an outer tx without noRollbackFor would roll back the family revocation")
                .isFalse();
    }

    @Test
    void logout_delegatesRevocation() {
        authService.logout("raw");

        verify(refreshTokenService).revoke("raw");
    }
}
