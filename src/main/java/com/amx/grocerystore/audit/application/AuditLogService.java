package com.amx.grocerystore.audit.application;

import com.amx.grocerystore.audit.domain.AuditLog;
import com.amx.grocerystore.audit.infrastructure.AuditLogRepository;
import com.amx.grocerystore.audit.web.dto.AuditLogResponse;
import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.users.domain.User;
import com.amx.grocerystore.users.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SYSTEM_ACTOR = "Sistema";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getStoreAuditLogs(Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        Page<AuditLog> logs = auditLogRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable);
        Map<UUID, String> userNames = userRepository.findByStoreId(storeId).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.from(logs.map(log -> toResponse(log, userNames)));
    }

    private AuditLogResponse toResponse(AuditLog log, Map<UUID, String> userNames) {
        String userName = log.getUserId() == null
                ? SYSTEM_ACTOR
                : userNames.getOrDefault(log.getUserId(), SYSTEM_ACTOR);
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                userName,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetails(),
                log.getCreatedAt());
    }
}
