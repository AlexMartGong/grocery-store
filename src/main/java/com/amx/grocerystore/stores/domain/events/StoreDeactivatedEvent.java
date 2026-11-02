package com.amx.grocerystore.stores.domain.events;

import com.amx.grocerystore.stores.domain.StoreStatus;

import java.util.UUID;

/**
 * A store transitioned out of {@code ACTIVE} ({@code SUSPENDED} or {@code CANCELED}). Consumed
 * synchronously, inside the publishing transaction, to revoke the store's refresh tokens.
 */
public record StoreDeactivatedEvent(
        UUID storeId,
        StoreStatus status
) {
}
