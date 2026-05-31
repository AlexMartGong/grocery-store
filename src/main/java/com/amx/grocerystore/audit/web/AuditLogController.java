package com.amx.grocerystore.audit.web;

import com.amx.grocerystore.audit.application.AuditLogService;
import com.amx.grocerystore.audit.web.dto.AuditLogResponse;
import com.amx.grocerystore.core.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PageResponse<AuditLogResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(auditLogService.getStoreAuditLogs(pageable));
    }
}
