package com.amx.grocerystore.notifications.application;

import com.amx.grocerystore.cash.domain.events.ShiftClosedEvent;
import com.amx.grocerystore.cash.web.dto.CashShiftResponse;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.domain.events.CustomerPaymentEvent;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import com.amx.grocerystore.inventory.domain.events.InventoryLossEvent;
import com.amx.grocerystore.inventory.domain.events.LowStockEvent;
import com.amx.grocerystore.notifications.infrastructure.EvolutionWhatsAppClient;
import com.amx.grocerystore.notifications.infrastructure.TelegramBotClient;
import com.amx.grocerystore.sales.domain.events.SaleCancelledEvent;
import com.amx.grocerystore.stores.domain.AdminNotificationChannel;
import com.amx.grocerystore.stores.domain.Store;
import com.amx.grocerystore.stores.infrastructure.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNotificationEventListener {

    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final EvolutionWhatsAppClient whatsappClient;
    private final TelegramBotClient telegramClient;

    /**
     * Routes an owner-facing alert to the store's preferred admin channel.
     * Null channel falls back to WhatsApp. Missing store is a no-op.
     */
    private void sendAdminAlert(UUID storeId, String text) {
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            log.warn("Skip admin alert: store {} not found", storeId);
            return;
        }
        AdminNotificationChannel channel = store.getPreferredAdminChannel() == null
                ? AdminNotificationChannel.WHATSAPP
                : store.getPreferredAdminChannel();
        switch (channel) {
            case TELEGRAM -> telegramClient.sendMessage(store.getTelegramChatId(), text);
            case WHATSAPP -> whatsappClient.sendMessage(store.getWhatsapp(), text);
        }
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
        sendAdminAlert(event.storeId(), text);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
        // Customer-facing: WhatsApp only — clients do not use Telegram.
        whatsappClient.sendMessage(event.customerPhone(), text);
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
        sendAdminAlert(event.storeId(), text);
    }

    @Async
    @EventListener
    public void onSaleCancelled(SaleCancelledEvent event) {
        String text = "🚨 *ALERTA ANTI-FRAUDE* 🚨\n"
                + "Se ha cancelado una venta por un total de *$" + event.totalAmount() + "*.\n"
                + "Motivo: " + translateReason(event.reason()) + "\n"
                + "Ticket ID: " + event.saleId();
        sendAdminAlert(event.storeId(), text);
    }

    private String translateReason(String reason) {
        return switch (reason) {
            case "CASHIER_ERROR" -> "Error de cajero";
            case "CUSTOMER_REGRET" -> "Arrepentimiento del cliente";
            case "WRONG_PRODUCT" -> "Producto equivocado";
            case "OTHER" -> "Otro motivo";
            default -> reason;
        };
    }

    @Async
    @EventListener
    public void onInventoryLoss(InventoryLossEvent event) {
        String text = "⚠️ *MERMA DE INVENTARIO* ⚠️\n"
                + "Se registró una pérdida de producto.\n"
                + "Producto: *" + event.productName() + "*\n"
                + "Cantidad: " + event.quantity().abs() + "\n"
                + "Notas: " + (event.notes() != null ? event.notes() : "Ninguna");
        sendAdminAlert(event.storeId(), text);
    }
}
