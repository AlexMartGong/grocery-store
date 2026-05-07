package com.amx.grocerystore.cash.web;

import com.amx.grocerystore.cash.application.CashShiftService;
import com.amx.grocerystore.cash.web.dto.CashShiftResponse;
import com.amx.grocerystore.cash.web.dto.CloseShiftRequest;
import com.amx.grocerystore.cash.web.dto.OpenShiftRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/cash/shifts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','CASHIER')")
public class CashShiftController {

    private final CashShiftService cashShiftService;

    @PostMapping("/open")
    public ResponseEntity<CashShiftResponse> open(@Valid @RequestBody OpenShiftRequest request) {
        CashShiftResponse response = cashShiftService.openShift(request);
        return ResponseEntity
                .created(URI.create("/api/v1/cash/shifts/" + response.id()))
                .body(response);
    }

    @GetMapping("/current")
    public ResponseEntity<CashShiftResponse> current() {
        return ResponseEntity.ok(cashShiftService.getCurrentShift());
    }

    @PostMapping("/current/close")
    public ResponseEntity<CashShiftResponse> close(@Valid @RequestBody CloseShiftRequest request) {
        return ResponseEntity.ok(cashShiftService.closeShift(request));
    }
}
