package com.amx.grocerystore.audit.application;

import com.amx.grocerystore.audit.domain.AuditLog;
import com.amx.grocerystore.audit.infrastructure.AuditLogRepository;
import com.amx.grocerystore.products.domain.events.ProductPriceChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditEventListener auditEventListener;

    @Test
    void onProductPriceChanged_mapsEventToAuditLog_andSaves() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        BigDecimal oldPrice = new BigDecimal("20.00");
        BigDecimal newPrice = new BigDecimal("24.50");
        ProductPriceChangedEvent event =
                new ProductPriceChangedEvent(storeId, userId, productId, oldPrice, newPrice);

        auditEventListener.onProductPriceChanged(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(storeId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAction()).isEqualTo("PRICE_CHANGED");
        assertThat(saved.getEntityType()).isEqualTo("PRODUCT");
        assertThat(saved.getEntityId()).isEqualTo(productId);
        assertThat(saved.getDetails())
                .containsEntry("oldPrice", oldPrice)
                .containsEntry("newPrice", newPrice);
    }
}
