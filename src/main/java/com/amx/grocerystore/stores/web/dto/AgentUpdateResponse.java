package com.amx.grocerystore.stores.web.dto;

public record AgentUpdateResponse(
        boolean updateAvailable,
        String latestVersion,
        String downloadUrl,
        String sha256
) {
    public static AgentUpdateResponse noUpdate() {
        return new AgentUpdateResponse(false, null, null, null);
    }
}
