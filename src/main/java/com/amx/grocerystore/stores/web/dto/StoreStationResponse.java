package com.amx.grocerystore.stores.web.dto;

import com.amx.grocerystore.stores.domain.StoreStation;
import com.amx.grocerystore.stores.domain.StoreStationStatus;

import java.util.UUID;

public record StoreStationResponse(
        UUID id,
        String name,
        String agentUrl,
        StoreStationStatus status
) {
    public static StoreStationResponse from(StoreStation station) {
        return new StoreStationResponse(
                station.getId(),
                station.getName(),
                station.getAgentUrl(),
                station.getStatus());
    }
}
