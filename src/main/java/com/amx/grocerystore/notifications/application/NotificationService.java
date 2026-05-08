package com.amx.grocerystore.notifications.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.notifications.domain.Notification;
import com.amx.grocerystore.notifications.infrastructure.NotificationRepository;
import com.amx.grocerystore.notifications.web.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications() {
        UUID storeId = SecurityUtils.currentStoreId();
        return notificationRepository
                .findByStoreIdAndReadAtIsNullOrderByCreatedAtDesc(storeId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markAsRead(UUID id) {
        UUID storeId = SecurityUtils.currentStoreId();
        Notification notification = notificationRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.notification(id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
    }
}
