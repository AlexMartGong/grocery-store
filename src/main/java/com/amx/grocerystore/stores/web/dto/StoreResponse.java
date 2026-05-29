package com.amx.grocerystore.stores.web.dto;

import com.amx.grocerystore.stores.domain.AdminNotificationChannel;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.domain.StoreStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        String code,
        String name,
        String ownerName,
        String whatsapp,
        String telegramChatId,
        AdminNotificationChannel preferredAdminChannel,
        String address,
        StoreStatus status,
        OffsetDateTime createdAt
) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getCode(),
                store.getName(),
                store.getOwnerName(),
                store.getWhatsapp(),
                store.getTelegramChatId(),
                store.getPreferredAdminChannel(),
                store.getAddress(),
                store.getStatus(),
                store.getCreatedAt()
        );
    }
}
