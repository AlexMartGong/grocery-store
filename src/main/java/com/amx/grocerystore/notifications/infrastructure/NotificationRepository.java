package com.amx.grocerystore.notifications.infrastructure;

import com.amx.grocerystore.notifications.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByStoreIdAndReadAtIsNullOrderByCreatedAtDesc(UUID storeId);

    Optional<Notification> findByIdAndStoreId(UUID id, UUID storeId);
}
