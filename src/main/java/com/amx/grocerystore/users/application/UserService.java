package com.amx.grocerystore.users.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import com.amx.grocerystore.users.web.dto.CreateUserRequest;
import com.amx.grocerystore.users.web.dto.UpdateUserRequest;
import com.amx.grocerystore.users.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        return persistNewUser(storeId, request);
    }

    @Transactional
    public UserResponse provisionUserForStore(UUID storeId, CreateUserRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> NotFoundException.store(storeId));
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw InvalidResourceStateException.storeNotActive(storeId);
        }
        if (request.role() != UserRole.OWNER) {
            throw InvalidResourceStateException.roleNotAllowedForProvisioning(request.role());
        }
        if (userRepository.existsByStoreIdAndRoleAndActiveTrue(storeId, UserRole.OWNER)) {
            throw DuplicateResourceException.storeOwner(storeId);
        }
        return persistNewUser(storeId, request);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        UUID storeId = SecurityUtils.currentStoreId();
        User user = userRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.user(id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Boolean active, UserRole role, Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        Page<User> page;
        if (active != null && role != null) {
            page = userRepository.findByStoreIdAndActiveAndRole(storeId, active, role, pageable);
        } else if (active != null) {
            page = userRepository.findByStoreIdAndActive(storeId, active, pageable);
        } else if (role != null) {
            page = userRepository.findByStoreIdAndRole(storeId, role, pageable);
        } else {
            page = userRepository.findByStoreId(storeId, pageable);
        }
        return PageResponse.from(page.map(UserResponse::from));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        User user = userRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.user(id));
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void softDelete(UUID id) {
        UUID storeId = SecurityUtils.currentStoreId();
        User user = userRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.user(id));
        if (!user.isActive()) {
            return;
        }
        user.setActive(false);
        userRepository.save(user);
    }

    private UserResponse persistNewUser(UUID storeId, CreateUserRequest request) {
        if (userRepository.existsByStoreIdAndUsername(storeId, request.username())) {
            throw DuplicateResourceException.username(request.username());
        }
        User user = User.builder()
                .storeId(storeId)
                .fullName(request.fullName())
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .active(true)
                .build();
        return UserResponse.from(userRepository.save(user));
    }
}
