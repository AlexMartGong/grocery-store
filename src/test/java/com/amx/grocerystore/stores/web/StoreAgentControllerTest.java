package com.amx.grocerystore.stores.web;

import com.amx.grocerystore.core.security.JwtAuthenticationEntryPoint;
import com.amx.grocerystore.core.security.JwtAuthenticationFilter;
import com.amx.grocerystore.core.security.SecurityConfig;
import com.amx.grocerystore.core.security.TenantAuthenticationToken;
import com.amx.grocerystore.core.security.UserPrincipal;
import com.amx.grocerystore.core.web.GlobalExceptionHandler;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.stores.application.AgentUpdateService;
import com.amx.grocerystore.stores.application.StoreService;
import com.amx.grocerystore.stores.domain.StoreStationStatus;
import com.amx.grocerystore.stores.web.dto.AgentUpdateResponse;
import com.amx.grocerystore.stores.web.dto.StoreStationResponse;
import com.amx.grocerystore.users.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({StoreAgentController.class, StoreProfileController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class StoreAgentControllerTest {

    private static final String AGENT_URL = "/api/v1/agent/stations/%s/url";
    private static final String ME_STATIONS_URL = "/api/v1/stores/me/stations";
    private static final String VALID_KEY = "test-agent-key";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StoreService storeService;

    @MockitoBean
    private AgentUpdateService agentUpdateService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @BeforeEach
    void passThroughFilter() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(
                    inv.getArgument(0, HttpServletRequest.class),
                    inv.getArgument(1, HttpServletResponse.class));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(inv -> {
            ((HttpServletResponse) inv.getArgument(1)).sendError(401);
            return null;
        }).when(jwtAuthenticationEntryPoint).commence(any(), any(), any());
    }

    private static TenantAuthenticationToken tenant(UserRole role) {
        return new TenantAuthenticationToken(
                new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), "tester", role));
    }

    // ---- Agent ingest: PUT /api/v1/agent/stations/{stationId}/url ----

    @Test
    void put_validKey_returns200WithUpdateMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        given(agentUpdateService.handleHeartbeat(eq(id), any()))
                .willReturn(new AgentUpdateResponse(true, "v2.0.0",
                        "https://github.com/AlexMartGong/pos-agent/releases/download/v2.0.0/pos-agent.jar",
                        "a".repeat(64)));

        mvc.perform(put(AGENT_URL.formatted(id)).with(csrf())
                        .header("X-Agent-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"http://192.168.1.50:9100","currentVersion":"1.0.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(true))
                .andExpect(jsonPath("$.latestVersion").value("v2.0.0"))
                .andExpect(jsonPath("$.sha256").value("a".repeat(64)));

        verify(agentUpdateService).handleHeartbeat(eq(id), any());
    }

    @Test
    void put_upToDate_returns200NoUpdate() throws Exception {
        UUID id = UUID.randomUUID();
        given(agentUpdateService.handleHeartbeat(eq(id), any()))
                .willReturn(AgentUpdateResponse.noUpdate());

        mvc.perform(put(AGENT_URL.formatted(id)).with(csrf())
                        .header("X-Agent-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"http://192.168.1.50:9100","currentVersion":"9.9.9"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateAvailable").value(false))
                .andExpect(jsonPath("$.latestVersion").doesNotExist());
    }

    @Test
    void put_wrongKey_returns403() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(put(AGENT_URL.formatted(id)).with(csrf())
                        .header("X-Agent-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"http://192.168.1.50:9100","currentVersion":"1.0.0"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));

        verify(agentUpdateService, never()).handleHeartbeat(any(), any());
    }

    @Test
    void put_missingKey_returns403() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(put(AGENT_URL.formatted(id)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"http://192.168.1.50:9100","currentVersion":"1.0.0"}
                                """))
                .andExpect(status().isForbidden());

        verify(agentUpdateService, never()).handleHeartbeat(any(), any());
    }

    @Test
    void put_blankUrl_returns400() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(put(AGENT_URL.formatted(id)).with(csrf())
                        .header("X-Agent-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.url").exists());
    }

    @Test
    void put_unknownStation_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doAnswer(inv -> {
            throw NotFoundException.station(id);
        }).when(agentUpdateService).handleHeartbeat(eq(id), any());

        mvc.perform(put(AGENT_URL.formatted(id)).with(csrf())
                        .header("X-Agent-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"http://192.168.1.50:9100","currentVersion":"1.0.0"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("station_not_found"));
    }

    // ---- PWA read: GET /api/v1/stores/me/stations ----

    @Test
    void getMeStations_authenticated_returns200() throws Exception {
        given(storeService.getStations(any()))
                .willReturn(List.of(new StoreStationResponse(
                        UUID.randomUUID(),
                        "Caja Principal",
                        "http://192.168.1.50:9100",
                        StoreStationStatus.ACTIVE)));

        mvc.perform(get(ME_STATIONS_URL).with(authentication(tenant(UserRole.CASHIER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Caja Principal"))
                .andExpect(jsonPath("$[0].agentUrl").value("http://192.168.1.50:9100"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void getMeStations_noStations_returnsEmptyArray() throws Exception {
        given(storeService.getStations(any())).willReturn(List.of());

        mvc.perform(get(ME_STATIONS_URL).with(authentication(tenant(UserRole.OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getMeStations_unauthenticated_returns401() throws Exception {
        mvc.perform(get(ME_STATIONS_URL))
                .andExpect(status().isUnauthorized());
    }
}
