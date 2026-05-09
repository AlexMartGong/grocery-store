package com.amx.grocerystore.notifications.application;

import com.amx.grocerystore.cash.domain.events.ShiftClosedEvent;
import com.amx.grocerystore.cash.web.dto.CashShiftResponse;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.domain.events.CustomerPaymentEvent;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import com.amx.grocerystore.notifications.infrastructure.EvolutionWhatsAppClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class WhatsAppEventListener {

    private final CustomerRepository customerRepository;
    private final EvolutionWhatsAppClient client;
    private final String ownerPhone;

    public WhatsAppEventListener(
            CustomerRepository customerRepository,
            EvolutionWhatsAppClient client,
            @Value("${store.owner.phone}") String ownerPhone
    ) {
        this.customerRepository = customerRepository;
        this.client = client;
        this.ownerPhone = ownerPhone;
    }

    @Async
    @EventListener
    public void onShiftClosed(ShiftClosedEvent event) {
        CashShiftResponse shift = event.shift();
        String text = String.format(
                "Corte de caja realizado.%nEsperado: $%s%nReal: $%s%nDiferencia: $%s",
                shift.expectedBalance(),
                shift.realBalance(),
                shift.difference()
        );
        client.sendMessage(ownerPhone, text);
    }

    @Async
    @EventListener
    public void onCustomerPayment(CustomerPaymentEvent event) {
        BigDecimal newBalance = customerRepository.findById(event.payment().customerId())
                .map(Customer::getOutstandingBalance)
                .orElse(BigDecimal.ZERO);
        String text = String.format(
                "Hola %s, confirmamos tu abono de $%s. Tu nuevo saldo es $%s.",
                event.customerName(),
                event.payment().amount(),
                newBalance
        );
        client.sendMessage(event.customerPhone(), text);
    }
}
