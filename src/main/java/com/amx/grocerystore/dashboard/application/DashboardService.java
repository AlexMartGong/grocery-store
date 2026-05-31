package com.amx.grocerystore.dashboard.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.dashboard.domain.ProductToRestockView;
import com.amx.grocerystore.dashboard.infrastructure.ProductToRestockViewRepository;
import com.amx.grocerystore.dashboard.infrastructure.SalesTodayViewRepository;
import com.amx.grocerystore.dashboard.web.dto.ProductToRestockResponse;
import com.amx.grocerystore.dashboard.web.dto.SalesTodayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SalesTodayViewRepository salesTodayViewRepository;
    private final ProductToRestockViewRepository productToRestockViewRepository;

    @Transactional(readOnly = true)
    public SalesTodayResponse getSalesToday() {
        UUID storeId = SecurityUtils.currentStoreId();
        return salesTodayViewRepository.findById(storeId)
                .map(SalesTodayResponse::from)
                .orElseGet(SalesTodayResponse::empty);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductToRestockResponse> getProductsToRestock(Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        Page<ProductToRestockView> products = productToRestockViewRepository.findByStoreId(storeId, pageable);
        return PageResponse.from(products.map(ProductToRestockResponse::from));
    }
}
