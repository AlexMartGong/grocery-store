package com.amx.grocerystore.sales.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import com.amx.grocerystore.inventory.domain.events.LowStockEvent;
import com.amx.grocerystore.notifications.domain.Notification;
import com.amx.grocerystore.notifications.domain.NotificationType;
import com.amx.grocerystore.notifications.infrastructure.NotificationRepository;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import com.amx.grocerystore.sales.domain.Sale;
import com.amx.grocerystore.sales.domain.SaleCancellation;
import com.amx.grocerystore.sales.domain.SaleItem;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.amx.grocerystore.sales.domain.SaleStatus;
import com.amx.grocerystore.sales.domain.events.SaleCancelledEvent;
import com.amx.grocerystore.sales.infrastructure.SaleCancellationRepository;
import com.amx.grocerystore.sales.infrastructure.SaleRepository;
import com.amx.grocerystore.sales.web.dto.CancelSaleRequest;
import com.amx.grocerystore.sales.web.dto.CreateSaleRequest;
import com.amx.grocerystore.sales.web.dto.SaleItemRequest;
import com.amx.grocerystore.sales.web.dto.SaleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleService {

    private static final Pattern STOCK_PATTERN =
            Pattern.compile("Quedan\\s+(\\d+(?:\\.\\d+)?)\\s+\\(mínimo\\s+(\\d+(?:\\.\\d+)?)\\)");
    private static final String LOW_PREFIX = "Poco inventario: ";
    private static final String OUT_PREFIX = "Agotado: ";

    private final SaleRepository saleRepository;
    private final SaleCancellationRepository saleCancellationRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Retryable(
            retryFor = {DataIntegrityViolationException.class, CannotAcquireLockException.class},
            backoff = @Backoff(delay = 200, multiplier = 2))
    @Transactional
    public SaleResponse createSale(CreateSaleRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        UUID cashierId = SecurityUtils.currentUserId();
        SalePaymentMethod method = request.paymentMethod();

        for (SaleItemRequest item : request.items()) {
            productRepository.findByIdAndStoreIdAndActiveTrue(item.productId(), storeId)
                    .orElseThrow(() -> NotFoundException.product(item.productId()));
        }

        BigDecimal total = request.items().stream()
                .map(i -> i.quantity().multiply(i.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountPaid;
        BigDecimal amountCredited;
        UUID customerId = request.customerId();

        // Tenant scoping: any supplied customer MUST belong to the current store, regardless of
        // payment method (VULN-01 IDOR fix). Loaded once and reused by the CREDIT/MIXED limit checks.
        Customer customer = null;
        if (customerId != null) {
            customer = customerRepository.findByIdAndStoreId(customerId, storeId)
                    .orElseThrow(() -> NotFoundException.customer(customerId));
        }

        switch (method) {
            case CASH, CARD, TRANSFER -> {
                amountPaid = total;
                amountCredited = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            case CREDIT -> {
                if (customer == null) {
                    throw InvalidResourceStateException.customerRequiredForCredit();
                }
                if (customer.getOutstandingBalance().add(total).compareTo(customer.getCreditLimit()) > 0) {
                    throw InvalidResourceStateException.creditLimitExceeded();
                }
                amountPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                amountCredited = total;
            }
            case MIXED -> {
                if (customer == null) {
                    throw InvalidResourceStateException.customerRequiredForCredit();
                }
                BigDecimal requested = request.amountPaid();
                if (requested == null
                        || requested.compareTo(BigDecimal.ZERO) <= 0
                        || requested.compareTo(total) >= 0) {
                    throw InvalidResourceStateException.invalidMixedPayment();
                }
                amountPaid = requested.setScale(2, RoundingMode.HALF_UP);
                amountCredited = total.subtract(amountPaid);
                if (customer.getOutstandingBalance().add(amountCredited).compareTo(customer.getCreditLimit()) > 0) {
                    throw InvalidResourceStateException.creditLimitExceeded();
                }
            }
            default -> throw new IllegalStateException("Unhandled payment method: " + method);
        }

        Sale sale = Sale.builder()
                .storeId(storeId)
                .userId(cashierId)
                .customerId(customerId)
                .totalAmount(total)
                .amountPaid(amountPaid)
                .amountCredited(amountCredited)
                .paymentMethod(method)
                .status(SaleStatus.COMPLETED)
                .build();

        for (SaleItemRequest itemReq : request.items()) {
            SaleItem item = SaleItem.builder()
                    .sale(sale)
                    .productId(itemReq.productId())
                    .quantity(itemReq.quantity())
                    .unitPrice(itemReq.unitPrice())
                    .build();
            sale.getItems().add(item);
        }

        Sale saved = saleRepository.save(sale);
        SaleResponse response = SaleResponse.from(saved);
        publishLowStockEvents(storeId);
        return response;
    }

    @Retryable(
            retryFor = {DataIntegrityViolationException.class, CannotAcquireLockException.class},
            backoff = @Backoff(delay = 200, multiplier = 2))
    @Transactional
    public void cancelSale(UUID saleId, CancelSaleRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        UUID userId = SecurityUtils.currentUserId();
        Sale sale = saleRepository.findByIdAndStoreId(saleId, storeId)
                .orElseThrow(() -> NotFoundException.sale(saleId));
        if (sale.getStatus() == SaleStatus.CANCELED) {
            throw InvalidResourceStateException.saleAlreadyCanceled();
        }
        SaleCancellation cancellation = SaleCancellation.builder()
                .sale(sale)
                .canceledBy(userId)
                .reason(request.reason())
                .notes(request.notes())
                .build();
        saleCancellationRepository.save(cancellation);
        eventPublisher.publishEvent(
                new SaleCancelledEvent(storeId, sale.getId(), sale.getTotalAmount(), request.reason().name()));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> list(UUID customerId, SalePaymentMethod paymentMethod,
                                           String search, LocalDate startDate, LocalDate endDate,
                                           Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();

        if (customerId != null) {
            customerRepository.findByIdAndStoreId(customerId, storeId)
                    .orElseThrow(() -> NotFoundException.customer(customerId));
        }

        String searchTerm = (search == null) ? "" : search;
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime from = (startDate == null)
                ? OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                : startDate.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = (endDate == null)
                ? OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                : endDate.atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();

        Page<Sale> page = saleRepository.findSales(
                storeId, customerId, paymentMethod, searchTerm, from, to, pageable);
        return PageResponse.from(page.map(SaleResponse::from));
    }

    private void publishLowStockEvents(UUID storeId) {
        List<Notification> pending = notificationRepository.findByStoreIdAndTypeInAndReadAtIsNull(
                storeId, List.of(NotificationType.LOW_STOCK, NotificationType.OUT_OF_STOCK));
        if (pending.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (Notification n : pending) {
            LowStockEvent ev = parseLowStock(n, storeId);
            if (ev != null) {
                eventPublisher.publishEvent(ev);
            }
            n.setReadAt(now);
        }
        notificationRepository.saveAll(pending);
    }

    private LowStockEvent parseLowStock(Notification n, UUID storeId) {
        String title = n.getTitle();
        String productName;
        if (title.startsWith(LOW_PREFIX)) {
            productName = title.substring(LOW_PREFIX.length());
        } else if (title.startsWith(OUT_PREFIX)) {
            productName = title.substring(OUT_PREFIX.length());
        } else {
            productName = title;
        }
        String message = n.getMessage() == null ? "" : n.getMessage();
        Matcher m = STOCK_PATTERN.matcher(message);
        if (!m.find()) {
            log.warn("low-stock notification {} message not parseable: {}", n.getId(), message);
            return null;
        }
        return new LowStockEvent(storeId, productName, new BigDecimal(m.group(1)), new BigDecimal(m.group(2)));
    }
}
