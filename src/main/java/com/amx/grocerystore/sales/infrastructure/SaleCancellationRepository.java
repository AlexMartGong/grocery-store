package com.amx.grocerystore.sales.infrastructure;

import com.amx.grocerystore.sales.domain.SaleCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SaleCancellationRepository extends JpaRepository<SaleCancellation, UUID> {

    boolean existsBySaleId(UUID saleId);
}
