package com.amx.grocerystore.customers.domain.events;

import com.amx.grocerystore.customers.web.dto.CustomerPaymentResponse;

public record CustomerPaymentEvent(
        CustomerPaymentResponse payment,
        String customerName,
        String customerPhone
) {
}
