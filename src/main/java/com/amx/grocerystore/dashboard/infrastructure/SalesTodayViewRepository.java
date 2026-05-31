package com.amx.grocerystore.dashboard.infrastructure;

import com.amx.grocerystore.dashboard.domain.SalesTodayView;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface SalesTodayViewRepository extends Repository<SalesTodayView, UUID> {

    Optional<SalesTodayView> findById(UUID storeId);
}
