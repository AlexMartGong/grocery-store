package com.amx.grocerystore.sales.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import com.amx.grocerystore.sales.domain.Sale;
import com.amx.grocerystore.sales.domain.SaleItem;
import com.amx.grocerystore.sales.domain.SalePaymentMethod;
import com.amx.grocerystore.sales.domain.SaleStatus;
import com.amx.grocerystore.sales.infrastructure.SaleRepository;
import com.amx.grocerystore.sales.web.dto.CreateSaleRequest;
import com.amx.grocerystore.sales.web.dto.SaleItemRequest;
import com.amx.grocerystore.sales.web.dto.SaleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

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

        switch (method) {
            case CASH, CARD, TRANSFER -> {
                amountPaid = total;
                amountCredited = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            case CREDIT -> {
                if (customerId == null) {
                    throw InvalidResourceStateException.customerRequiredForCredit();
                }
                Customer customer = customerRepository.findByIdAndStoreId(customerId, storeId)
                        .orElseThrow(() -> NotFoundException.customer(customerId));
                if (customer.getOutstandingBalance().add(total).compareTo(customer.getCreditLimit()) > 0) {
                    throw InvalidResourceStateException.creditLimitExceeded();
                }
                amountPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                amountCredited = total;
            }
            case MIXED -> {
                if (customerId == null) {
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
                Customer customer = customerRepository.findByIdAndStoreId(customerId, storeId)
                        .orElseThrow(() -> NotFoundException.customer(customerId));
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
        return SaleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> list(Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        Page<Sale> page = saleRepository.findByStoreId(storeId, pageable);
        return PageResponse.from(page.map(SaleResponse::from));
    }
}
