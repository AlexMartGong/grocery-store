package com.amx.grocerystore.users.web;

import com.amx.grocerystore.users.application.UserService;
import com.amx.grocerystore.users.web.dto.CreateUserRequest;
import com.amx.grocerystore.users.web.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/stores/{storeId}/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserProvisioningController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> provision(@PathVariable UUID storeId,
                                                  @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.provisionUserForStore(storeId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/stores/" + storeId + "/users/" + response.id()))
                .body(response);
    }
}
