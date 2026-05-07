package com.amx.grocerystore.cash.infrastructure;

import com.amx.grocerystore.cash.domain.CashRegisterShift;
import com.amx.grocerystore.cash.domain.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CashRegisterShiftRepository extends JpaRepository<CashRegisterShift, UUID> {

    Optional<CashRegisterShift> findByStoreIdAndStatus(UUID storeId, ShiftStatus status);
}
