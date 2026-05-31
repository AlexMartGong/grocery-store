package com.amx.grocerystore.audit.application;

import com.amx.grocerystore.audit.domain.AuditLog;
import com.amx.grocerystore.audit.infrastructure.AuditLogRepository;
import com.amx.grocerystore.products.domain.events.ProductPriceChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void onProductPriceChanged(ProductPriceChangedEvent event) {
        AuditLog log = AuditLog.builder()
                .storeId(event.storeId())
                .userId(event.userId())
                .action("PRICE_CHANGED")
                .entityType("PRODUCT")
                .entityId(event.productId())
                .details(Map.of(
                        "oldPrice", event.oldPrice(),
                        "newPrice", event.newPrice()))
                .build();
        auditLogRepository.save(log);
    }
}
