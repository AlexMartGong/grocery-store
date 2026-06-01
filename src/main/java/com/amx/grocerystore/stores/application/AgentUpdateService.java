package com.amx.grocerystore.stores.application;

import com.amx.grocerystore.stores.infrastructure.client.GitHubReleaseClient;
import com.amx.grocerystore.stores.web.dto.AgentUpdateResponse;
import com.amx.grocerystore.stores.web.dto.UpdateAgentUrlRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentUpdateService {

    private final StoreService storeService;
    private final GitHubReleaseClient gitHubReleaseClient;

    public AgentUpdateResponse handleHeartbeat(UUID stationId, UpdateAgentUrlRequest request) {
        storeService.updateAgentUrl(stationId, request.url());

        return gitHubReleaseClient.fetchLatestRelease()
                .filter(release -> isNewer(release.latestVersion(), request.currentVersion()))
                .map(release -> new AgentUpdateResponse(
                        true,
                        release.latestVersion(),
                        release.downloadUrl(),
                        release.sha256()))
                .orElseGet(AgentUpdateResponse::noUpdate);
    }

    /**
     * True when {@code latest} is strictly greater than {@code current} using a
     * numeric, dotted-segment comparison (e.g. {@code v1.2.0} > {@code 1.1.9}).
     * Null/blank {@code current} → false (cannot compare; do not force an update).
     */
    private boolean isNewer(String latest, String current) {
        if (current == null || current.isBlank() || latest == null || latest.isBlank()) {
            return false;
        }
        int[] latestParts = parse(latest);
        int[] currentParts = parse(current);
        int max = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < max; i++) {
            int l = i < latestParts.length ? latestParts[i] : 0;
            int c = i < currentParts.length ? currentParts[i] : 0;
            if (l != c) {
                return l > c;
            }
        }
        return false;
    }

    private int[] parse(String version) {
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        String[] segments = normalized.split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            parts[i] = leadingNumber(segments[i]);
        }
        return parts;
    }

    private int leadingNumber(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(segment.substring(0, end));
    }
}
