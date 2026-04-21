package com.amx.grocerystore.users.application;

import com.amx.grocerystore.core.security.TenantAuthenticationToken;
import com.amx.grocerystore.core.security.UserPrincipal;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import com.amx.grocerystore.users.web.dto.CreateUserRequest;
import com.amx.grocerystore.users.web.dto.UpdateUserRequest;
import com.amx.grocerystore.users.web.dto.UserResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), TENANT_ID, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(new TenantAuthenticationToken(principal));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(UUID id, UUID storeId, boolean active) {
        return User.builder()
                .id(id)
                .storeId(storeId)
                .fullName("Full Name")
                .username("cashier1")
                .passwordHash("hash")
                .role(UserRole.CASHIER)
                .active(active)
                .build();
    }

    @Test
    void create_encodesPassword_usesStoreIdFromContext() {
        CreateUserRequest req = new CreateUserRequest("Cajero Uno", "cashier1", "Secret12", UserRole.CASHIER);
        given(userRepository.existsByStoreIdAndUsername(TENANT_ID, "cashier1")).willReturn(false);
        given(passwordEncoder.encode("Secret12")).willReturn("ENCODED");
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserResponse response = userService.create(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(TENANT_ID);
        assertThat(saved.getPasswordHash()).isEqualTo("ENCODED");
        assertThat(saved.isActive()).isTrue();
        assertThat(response.username()).isEqualTo("cashier1");
    }

    @Test
    void create_whenUsernameTakenInTenant_throwsDuplicate() {
        CreateUserRequest req = new CreateUserRequest("Cajero Uno", "cashier1", "Secret12", UserRole.CASHIER);
        given(userRepository.existsByStoreIdAndUsername(TENANT_ID, "cashier1")).willReturn(true);

        assertThatThrownBy(() -> userService.create(req))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("code").isEqualTo("username_taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void findById_whenInOtherStore_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("user_not_found");
    }

    @Test
    void findById_whenSameStore_returnsResponse() {
        UUID id = UUID.randomUUID();
        User u = user(id, TENANT_ID, true);
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(u));

        UserResponse response = userService.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.username()).isEqualTo("cashier1");
        assertThat(response.active()).isTrue();
    }

    @Test
    void list_noFilters_usesFindByStoreId() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user(UUID.randomUUID(), TENANT_ID, true)));
        given(userRepository.findByStoreId(TENANT_ID, pageable)).willReturn(page);

        PageResponse<UserResponse> response = userService.list(null, null, pageable);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void list_withActive_usesFindByStoreIdAndActive() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user(UUID.randomUUID(), TENANT_ID, true)));
        given(userRepository.findByStoreIdAndActive(TENANT_ID, true, pageable)).willReturn(page);

        userService.list(true, null, pageable);

        verify(userRepository).findByStoreIdAndActive(TENANT_ID, true, pageable);
    }

    @Test
    void list_withRole_usesFindByStoreIdAndRole() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user(UUID.randomUUID(), TENANT_ID, true)));
        given(userRepository.findByStoreIdAndRole(TENANT_ID, UserRole.CASHIER, pageable)).willReturn(page);

        userService.list(null, UserRole.CASHIER, pageable);

        verify(userRepository).findByStoreIdAndRole(TENANT_ID, UserRole.CASHIER, pageable);
    }

    @Test
    void list_withBoth_usesFindByStoreIdAndActiveAndRole() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user(UUID.randomUUID(), TENANT_ID, true)));
        given(userRepository.findByStoreIdAndActiveAndRole(TENANT_ID, true, UserRole.OWNER, pageable)).willReturn(page);

        userService.list(true, UserRole.OWNER, pageable);

        verify(userRepository).findByStoreIdAndActiveAndRole(TENANT_ID, true, UserRole.OWNER, pageable);
    }

    @Test
    void update_withPassword_encodesAndSaves() {
        UUID id = UUID.randomUUID();
        User existing = user(id, TENANT_ID, true);
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(existing));
        given(passwordEncoder.encode("NewPass123")).willReturn("NEW_HASH");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest req = new UpdateUserRequest("New Name", null, null, "NewPass123");
        UserResponse response = userService.update(id, req);

        assertThat(existing.getPasswordHash()).isEqualTo("NEW_HASH");
        assertThat(existing.getFullName()).isEqualTo("New Name");
        assertThat(response.fullName()).isEqualTo("New Name");
    }

    @Test
    void update_withoutPassword_leavesHashUntouched() {
        UUID id = UUID.randomUUID();
        User existing = user(id, TENANT_ID, true);
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(existing));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest req = new UpdateUserRequest(null, UserRole.OWNER, null, null);
        userService.update(id, req);

        assertThat(existing.getPasswordHash()).isEqualTo("hash");
        assertThat(existing.getRole()).isEqualTo(UserRole.OWNER);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void softDelete_active_deactivatesAndSaves() {
        UUID id = UUID.randomUUID();
        User existing = user(id, TENANT_ID, true);
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(existing));

        userService.softDelete(id);

        assertThat(existing.isActive()).isFalse();
        verify(userRepository).save(existing);
    }

    @Test
    void softDelete_alreadyInactive_returnsEarly_noSave() {
        UUID id = UUID.randomUUID();
        User existing = user(id, TENANT_ID, false);
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.of(existing));

        userService.softDelete(id);

        verify(userRepository, never()).save(any());
    }

    @Test
    void softDelete_otherTenant_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.softDelete(id))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("user_not_found");
    }

    @Test
    void tenantIdNeverLeaksAcrossStores() {
        UUID id = UUID.randomUUID();
        User other = user(id, OTHER_TENANT, true);
        given(userRepository.findByIdAndStoreId(id, TENANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
                .isInstanceOf(NotFoundException.class);

        verify(userRepository).findByIdAndStoreId(id, TENANT_ID);
        assertThat(other.getStoreId()).isEqualTo(OTHER_TENANT);
    }
}
