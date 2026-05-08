package com.amx.grocerystore.notifications.web.dto;

import com.amx.grocerystore.notifications.domain.Notification;
import com.amx.grocerystore.notifications.domain.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        @JsonProperty("reference_id") UUID referenceId,
        @JsonProperty("read_at") OffsetDateTime readAt,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getReferenceId(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
