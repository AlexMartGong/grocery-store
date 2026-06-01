package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.security.AgentSecurityProperties;
import com.amx.grocerystore.stores.application.AgentUpdateService;
import com.amx.grocerystore.stores.web.dto.AgentUpdateResponse;
import com.amx.grocerystore.stores.web.dto.UpdateAgentUrlRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/stations")
@RequiredArgsConstructor
public class StoreAgentController {

    static final String AGENT_KEY_HEADER = "X-Agent-Key";

    private final AgentUpdateService agentUpdateService;
    private final AgentSecurityProperties agentProperties;

    @PutMapping("/{stationId}/url")
    public ResponseEntity<AgentUpdateResponse> updateAgentUrl(
            @PathVariable UUID stationId,
            @RequestHeader(value = AGENT_KEY_HEADER, required = false) String agentKey,
            @Valid @RequestBody UpdateAgentUrlRequest request) {
        if (agentKey == null || !MessageDigest.isEqual(
                agentKey.getBytes(StandardCharsets.UTF_8),
                agentProperties.apiKey().getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("invalid_agent_key");
        }
        return ResponseEntity.ok(agentUpdateService.handleHeartbeat(stationId, request));
    }
}
