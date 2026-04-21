package com.amx.grocerystore.stores.web.dto;

import com.amx.grocerystore.stores.domain.StoreStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStoreRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 150)
        String ownerName,

        @Size(max = 20)
        String whatsapp,

        String address,

        @NotNull
        StoreStatus status
) {
}
