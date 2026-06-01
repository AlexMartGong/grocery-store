package com.amx.grocerystore.stores.application;

import com.amx.grocerystore.stores.infrastructure.client.GitHubReleaseClient;
import com.amx.grocerystore.stores.infrastructure.client.GitHubReleaseClient.GitHubRelease;
import com.amx.grocerystore.stores.web.dto.AgentUpdateResponse;
import com.amx.grocerystore.stores.web.dto.UpdateAgentUrlRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentUpdateServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private GitHubReleaseClient gitHubReleaseClient;

    @InjectMocks
    private AgentUpdateService agentUpdateService;

    private static final String JAR_URL = "https://github.com/AlexMartGong/pos-agent/releases/download/v2.0.0/pos-agent.jar";
    private static final String SHA = "a".repeat(64);

    private UpdateAgentUrlRequest request(String currentVersion) {
        return new UpdateAgentUrlRequest("http://10.0.0.5:9100", currentVersion);
    }

    @Test
    void newerRelease_returnsUpdateAvailableAndSavesUrl() {
        UUID stationId = UUID.randomUUID();
        UpdateAgentUrlRequest req = request("1.0.0");
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("v2.0.0", JAR_URL, SHA)));

        AgentUpdateResponse response = agentUpdateService.handleHeartbeat(stationId, req);

        assertTrue(response.updateAvailable());
        assertEquals("v2.0.0", response.latestVersion());
        assertEquals(JAR_URL, response.downloadUrl());
        assertEquals(SHA, response.sha256());
        verify(storeService).updateAgentUrl(stationId, "http://10.0.0.5:9100");
    }

    @Test
    void sameVersion_returnsNoUpdateButStillSavesUrl() {
        UUID stationId = UUID.randomUUID();
        UpdateAgentUrlRequest req = request("2.0.0");
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("v2.0.0", JAR_URL, SHA)));

        AgentUpdateResponse response = agentUpdateService.handleHeartbeat(stationId, req);

        assertFalse(response.updateAvailable());
        assertNull(response.latestVersion());
        verify(storeService).updateAgentUrl(stationId, "http://10.0.0.5:9100");
    }

    @Test
    void olderRelease_returnsNoUpdate() {
        UpdateAgentUrlRequest req = request("3.1.0");
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("v3.0.5", JAR_URL, SHA)));

        AgentUpdateResponse response = agentUpdateService.handleHeartbeat(UUID.randomUUID(), req);

        assertFalse(response.updateAvailable());
    }

    @Test
    void gitHubUnavailable_returnsNoUpdateButStillSavesUrl() {
        UUID stationId = UUID.randomUUID();
        UpdateAgentUrlRequest req = request("1.0.0");
        when(gitHubReleaseClient.fetchLatestRelease()).thenReturn(Optional.empty());

        AgentUpdateResponse response = agentUpdateService.handleHeartbeat(stationId, req);

        assertFalse(response.updateAvailable());
        assertNull(response.downloadUrl());
        verify(storeService).updateAgentUrl(stationId, "http://10.0.0.5:9100");
    }

    @Test
    void nullCurrentVersion_neverForcesUpdate() {
        UpdateAgentUrlRequest req = request(null);
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("v2.0.0", JAR_URL, SHA)));

        AgentUpdateResponse response = agentUpdateService.handleHeartbeat(UUID.randomUUID(), req);

        assertFalse(response.updateAvailable());
    }

    @Test
    void blankCurrentVersion_neverForcesUpdate() {
        UpdateAgentUrlRequest req = request("   ");
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("v2.0.0", JAR_URL, SHA)));

        AgentUpdateResponse response = agentUpdateService.handleHeartbeat(UUID.randomUUID(), req);

        assertFalse(response.updateAvailable());
    }

    @Test
    void prefixedAndUnprefixedEqual_returnsNoUpdate() {
        UpdateAgentUrlRequest req = request("1.2.0");
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("v1.2.0", JAR_URL, SHA)));

        AgentUpdateResponse response = agentUpdateService.handleHeartbeat(UUID.randomUUID(), req);

        assertFalse(response.updateAvailable());
    }

    @Test
    void differingSegmentCounts_treatsMissingAsZero() {
        // latest 1.2 vs current 1.2.0 -> equal -> no update
        UpdateAgentUrlRequest req = request("1.2.0");
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("1.2", JAR_URL, SHA)));

        AgentUpdateResponse equal = agentUpdateService.handleHeartbeat(UUID.randomUUID(), req);
        assertFalse(equal.updateAvailable());

        // latest 1.2.1 vs current 1.2 -> newer
        when(gitHubReleaseClient.fetchLatestRelease())
                .thenReturn(Optional.of(new GitHubRelease("1.2.1", JAR_URL, SHA)));
        AgentUpdateResponse newer = agentUpdateService.handleHeartbeat(UUID.randomUUID(), request("1.2"));
        assertTrue(newer.updateAvailable());
    }
}
