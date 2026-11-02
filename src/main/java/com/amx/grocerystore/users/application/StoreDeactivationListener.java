package com.amx.grocerystore.users.application;

import com.amx.grocerystore.stores.domain.events.StoreDeactivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Revokes every refresh token of a store that leaves {@code ACTIVE}.
 * <p>
 * Deliberately a <b>synchronous</b> {@link EventListener} — not the project's usual
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)}: it runs inside the publisher's
 * transaction so the revocation commits (or rolls back) atomically with the status change. The event
 * keeps {@code stores} unaware of {@code users} ({@code users} already depends on {@code stores}).
 */
@Component
@RequiredArgsConstructor
public class StoreDeactivationListener {

    private final RefreshTokenService refreshTokenService;

    @EventListener
    public void onStoreDeactivated(StoreDeactivatedEvent event) {
        refreshTokenService.revokeAllForStore(event.storeId());
    }
}
