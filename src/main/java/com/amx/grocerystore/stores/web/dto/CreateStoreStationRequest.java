package com.amx.grocerystore.stores.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStoreStationRequest(
        @NotBlank @Size(max = 100) String name) {
}
