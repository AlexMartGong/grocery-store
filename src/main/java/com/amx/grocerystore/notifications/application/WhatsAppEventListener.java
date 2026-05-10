package com.amx.grocerystore.notifications.application;

import com.amx.grocerystore.cash.domain.events.ShiftClosedEvent;
import com.amx.grocerystore.cash.web.dto.CashShiftResponse;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.domain.events.CustomerPaymentEvent;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import com.amx.grocerystore.inventory.domain.events.InventoryLossEvent;
import com.amx.grocerystore.inventory.domain.events.LowStockEvent;
import com.amx.grocerystore.notifications.infrastructure.EvolutionWhatsAppClient;
import com.amx.grocerystore.sales.domain.events.SaleCancelledEvent;
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

    @Async
    @EventListener
    public void onLowStock(LowStockEvent event) {
        String text = String.format(
                "⚠️ Alerta de Inventario ⚠️%nEl producto *%s* se está agotando.%nQuedan: %s (Mínimo: %s)",
                event.productName(),
                event.remainingStock(),
                event.minStock()
        );
        client.sendMessage(ownerPhone, text);
    }

    @Async
    @EventListener
    public void onSaleCancelled(SaleCancelledEvent event) {
        String text = "🚨 *ALERTA ANTI-FRAUDE* 🚨\n"
                + "Se ha cancelado una venta por un total de *$" + event.totalAmount() + "*.\n"
                + "Motivo: " + event.reason() + "\n"
                + "Ticket ID: " + event.saleId();
        client.sendMessage(ownerPhone, text);
    }

    @Async
    @EventListener
    public void onInventoryLoss(InventoryLossEvent event) {
        String text = "⚠️ *MERMA DE INVENTARIO* ⚠️\n"
                + "Se registró una pérdida de producto.\n"
                + "Producto: *" + event.productName() + "*\n"
                + "Cantidad: " + event.quantity().abs() + "\n"
                + "Notas: " + (event.notes() != null ? event.notes() : "Ninguna");
        client.sendMessage(ownerPhone, text);
    }
}
