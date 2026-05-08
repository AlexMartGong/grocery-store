package com.amx.grocerystore.notifications.application;

import com.amx.grocerystore.cash.domain.events.ShiftClosedEvent;
import com.amx.grocerystore.cash.web.dto.CashShiftResponse;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.domain.events.CustomerPaymentEvent;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppEventListener {

    private final CustomerRepository customerRepository;

    @Async
    @EventListener
    public void onShiftClosed(ShiftClosedEvent event) {
        CashShiftResponse shift = event.shift();
        log.info(
                "Simulando envío a WhatsApp (Evolution API) al dueño: Corte de caja realizado. Esperado: ${}, Real: ${}, Diferencia: ${}",
                shift.expectedBalance(),
                shift.realBalance(),
                shift.difference()
        );
    }

    @Async
    @EventListener
    public void onCustomerPayment(CustomerPaymentEvent event) {
        BigDecimal newBalance = customerRepository.findById(event.payment().customerId())
                .map(Customer::getOutstandingBalance)
                .orElse(BigDecimal.ZERO);
        log.info(
                "Simulando WhatsApp al cliente {}: Confirmamos tu abono de ${}. Tu nuevo saldo es ${}",
                event.customerName(),
                event.payment().amount(),
                newBalance
        );
    }
}
