package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.security.AgentSecurityProperties;
import com.amx.grocerystore.stores.application.StoreService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/stations")
@RequiredArgsConstructor
public class StoreAgentController {

    static final String AGENT_KEY_HEADER = "X-Agent-Key";

    private final StoreService storeService;
    private final AgentSecurityProperties agentProperties;

    @PutMapping("/{stationId}/url")
    public ResponseEntity<Void> updateAgentUrl(
            @PathVariable UUID stationId,
            @RequestHeader(value = AGENT_KEY_HEADER, required = false) String agentKey,
            @Valid @RequestBody UpdateAgentUrlRequest request) {
        if (agentKey == null || !agentKey.equals(agentProperties.apiKey())) {
            throw new AccessDeniedException("invalid_agent_key");
        }
        storeService.updateAgentUrl(stationId, request.url());
        return ResponseEntity.noContent().build();
    }
}
