package com.amx.grocerystore.cash.application;

import com.amx.grocerystore.cash.domain.CashRegisterShift;
import com.amx.grocerystore.cash.domain.ShiftStatus;
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
        UUID storeId = SecurityUtils.currentStoreId();
        return shiftRepository.findByStoreIdAndStatus(storeId, ShiftStatus.OPEN)
                .map(CashShiftResponse::from)
                .orElseThrow(NotFoundException::openShift);
    }

    @Transactional
    public CashShiftResponse closeShift(CloseShiftRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        CashRegisterShift shift = shiftRepository.findByStoreIdAndStatus(storeId, ShiftStatus.OPEN)
                .orElseThrow(NotFoundException::openShift);

        OffsetDateTime openedAt = shift.getOpenedAt();
        BigDecimal cashSales = saleRepository.sumCashInflow(storeId, openedAt);
        BigDecimal cashPayments = customerPaymentRepository.sumPaymentsSince(storeId, openedAt);

        BigDecimal expected = shift.getOpeningBalance()
                .add(cashSales)
                .add(cashPayments)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal real = request.realBalance().setScale(2, RoundingMode.HALF_UP);

        shift.setExpectedBalance(expected);
        shift.setRealBalance(real);
        shift.setDifference(real.subtract(expected));
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setClosedAt(OffsetDateTime.now());
        shift.setNotes(request.notes());

        return CashShiftResponse.from(shiftRepository.save(shift));
    }
}
