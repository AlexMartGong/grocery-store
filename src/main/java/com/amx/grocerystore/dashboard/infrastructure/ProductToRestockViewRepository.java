package com.amx.grocerystore.dashboard.infrastructure;

import com.amx.grocerystore.dashboard.domain.ProductToRestockView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface ProductToRestockViewRepository extends Repository<ProductToRestockView, UUID> {

    Page<ProductToRestockView> findByStoreId(UUID storeId, Pageable pageable);
}
