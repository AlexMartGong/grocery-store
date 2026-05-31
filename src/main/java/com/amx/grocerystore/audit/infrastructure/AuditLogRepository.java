package com.amx.grocerystore.audit.infrastructure;

import com.amx.grocerystore.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByStoreIdOrderByCreatedAtDesc(UUID storeId, Pageable pageable);
}
