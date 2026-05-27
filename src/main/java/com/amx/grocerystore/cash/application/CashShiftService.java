package com.amx.grocerystore.cash.application;

import com.amx.grocerystore.cash.domain.CashRegisterShift;
import com.amx.grocerystore.cash.domain.ShiftStatus;
import com.amx.grocerystore.cash.domain.events.ShiftClosedEvent;
import com.amx.grocerystore.cash.infrastructure.CashRegisterShiftRepository;
import com.amx.grocerystore.cash.web.dto.CashShiftResponse;
import com.amx.grocerystore.cash.web.dto.CloseShiftRequest;
import com.amx.grocerystore.cash.web.dto.OpenShiftRequest;
import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.exception.InvalidResourceStateException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.customers.infrastructure.CustomerPaymentRepository;
import com.amx.grocerystore.sales.infrastructure.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashShiftService {

    private final CashRegisterShiftRepository shiftRepository;
    private final SaleRepository saleRepository;
    private final CustomerPaymentRepository customerPaymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CashShiftResponse openShift(OpenShiftRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        UUID userId = SecurityUtils.currentUserId();

        shiftRepository.findByStoreIdAndStatus(storeId, ShiftStatus.OPEN)
                .ifPresent(existing -> {
                    throw InvalidResourceStateException.shiftAlreadyOpen();
                });

        CashRegisterShift shift = CashRegisterShift.builder()
                .storeId(storeId)
                .userId(userId)
                .status(ShiftStatus.OPEN)
                .openingBalance(request.openingBalance().setScale(2, RoundingMode.HALF_UP))
                .build();

        return CashShiftResponse.from(shiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public CashShiftResponse getCurrentShift() {
        return CashShiftResponse.from(loadOpenShift(SecurityUtils.currentStoreId()));
    }

    @Transactional(readOnly = true)
    public Page<CashShiftResponse> getShiftsHistory(Pageable pageable) {
        return shiftRepository.findByStoreIdOrderByOpenedAtDesc(SecurityUtils.currentStoreId(), pageable)
                .map(CashShiftResponse::from);
    }

    @Transactional(readOnly = true)
    public CashShiftResponse previewCurrentShift() {
        CashRegisterShift shift = loadOpenShift(SecurityUtils.currentStoreId());
        BigDecimal expected = computeExpectedBalance(shift);

        return new CashShiftResponse(
                shift.getId(), shift.getStoreId(), shift.getUserId(),
                shift.getStatus(), shift.getOpenedAt(), shift.getClosedAt(),
                shift.getOpeningBalance(),
                expected,
                null,
                null,
                shift.getNotes()
        );
    }

    @Transactional
    public CashShiftResponse closeShift(CloseShiftRequest request) {
        CashRegisterShift shift = loadOpenShift(SecurityUtils.currentStoreId());
        BigDecimal expected = computeExpectedBalance(shift);
        BigDecimal real = request.realBalance().setScale(2, RoundingMode.HALF_UP);

        shift.setExpectedBalance(expected);
        shift.setRealBalance(real);
        shift.setDifference(real.subtract(expected));
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setClosedAt(OffsetDateTime.now());
        shift.setNotes(request.notes());

        CashShiftResponse response = CashShiftResponse.from(shiftRepository.save(shift));
        eventPublisher.publishEvent(new ShiftClosedEvent(response.storeId(), response));
        return response;
    }

    private CashRegisterShift loadOpenShift(UUID storeId) {
        return shiftRepository.findByStoreIdAndStatus(storeId, ShiftStatus.OPEN)
                .orElseThrow(NotFoundException::openShift);
    }

    private BigDecimal computeExpectedBalance(CashRegisterShift shift) {
        UUID storeId = shift.getStoreId();
        OffsetDateTime openedAt = shift.getOpenedAt();
        BigDecimal cashSales = saleRepository.sumCashInflow(storeId, openedAt);
        BigDecimal cashPayments = customerPaymentRepository.sumPaymentsSince(storeId, openedAt);
        return shift.getOpeningBalance()
                .add(cashSales)
                .add(cashPayments)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
