package com.amx.grocerystore.users.infrastructure;

import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByStoreIdAndUsernameAndActiveTrue(UUID storeId, String username);

    List<User> findByStoreId(UUID storeId);

    Optional<User> findByIdAndStoreId(UUID id, UUID storeId);

    Page<User> findByStoreId(UUID storeId, Pageable pageable);

    Page<User> findByStoreIdAndActive(UUID storeId, boolean active, Pageable pageable);

    Page<User> findByStoreIdAndRole(UUID storeId, UserRole role, Pageable pageable);

    Page<User> findByStoreIdAndActiveAndRole(UUID storeId, boolean active, UserRole role, Pageable pageable);

    boolean existsByStoreIdAndUsername(UUID storeId, String username);

    boolean existsByStoreIdAndRoleAndActiveTrue(UUID storeId, UserRole role);
}
