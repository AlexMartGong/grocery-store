package com.amx.grocerystore.admin.infrastructure;

import com.amx.grocerystore.admin.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUsernameAndActiveTrue(String username);
}
