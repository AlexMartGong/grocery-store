package com.amx.grocerystore.users.application;

import com.amx.grocerystore.stores.domain.StoreStatus;
import com.amx.grocerystore.stores.domain.events.StoreDeactivatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreDeactivationListenerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private StoreDeactivationListener listener;

    @Test
    void storeDeactivated_revokesAllStoreRefreshTokens() {
        UUID storeId = UUID.randomUUID();

        listener.onStoreDeactivated(new StoreDeactivatedEvent(storeId, StoreStatus.SUSPENDED));

        verify(refreshTokenService).revokeAllForStore(storeId);
    }

    @Test
    void listenerIsSynchronousInsidePublisherTransaction() throws Exception {
        var method = StoreDeactivationListener.class.getMethod("onStoreDeactivated", StoreDeactivatedEvent.class);

        assertThat(method.isAnnotationPresent(EventListener.class)).isTrue();
        assertThat(method.isAnnotationPresent(Async.class)).isFalse();
        assertThat(method.isAnnotationPresent(TransactionalEventListener.class)).isFalse();
    }
}
