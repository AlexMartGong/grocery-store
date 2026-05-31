package com.amx.grocerystore.audit.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID userId,
        String userName,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> details,
        OffsetDateTime createdAt
) {
}
