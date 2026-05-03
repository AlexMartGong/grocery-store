package com.amx.grocerystore.products.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import com.amx.grocerystore.products.web.dto.ProductRequest;
import com.amx.grocerystore.products.web.dto.ProductResponse;
import com.amx.grocerystore.products.web.dto.UpdateProductPricesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        if (request.barcode() != null
                && productRepository.existsByStoreIdAndBarcode(storeId, request.barcode())) {
            throw DuplicateResourceException.barcode(request.barcode());
        }
        Product product = Product.builder()
                .storeId(storeId)
                .barcode(request.barcode())
                .name(request.name())
                .purchaseCost(request.purchaseCost())
                .salePrice(request.salePrice())
                .currentStock(request.currentStock())
                .minimumStock(request.minimumStock())
                .unitOfMeasure(request.unitOfMeasure())
                .active(true)
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.product(id));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Boolean active, Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        Page<Product> page;
        if (active == null || active) {
            page = productRepository.findByStoreIdAndActiveTrue(storeId, pageable);
        } else {
            page = productRepository.findByStoreId(storeId, pageable);
        }
        return PageResponse.from(page.map(ProductResponse::from));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.product(id));
        if (request.barcode() != null
                && productRepository.existsByStoreIdAndBarcodeAndIdNot(storeId, request.barcode(), id)) {
            throw DuplicateResourceException.barcode(request.barcode());
        }
        product.setBarcode(request.barcode());
        product.setName(request.name());
        product.setPurchaseCost(request.purchaseCost());
        product.setSalePrice(request.salePrice());
        product.setCurrentStock(request.currentStock());
        product.setMinimumStock(request.minimumStock());
        product.setUnitOfMeasure(request.unitOfMeasure());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void softDelete(UUID id) {
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.product(id));
        if (!product.isActive()) {
            return;
        }
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional
    public ProductResponse updatePrices(UUID id, UpdateProductPricesRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByIdAndStoreIdAndActiveTrue(id, storeId)
                .orElseThrow(() -> NotFoundException.product(id));
        if (request.salePrice() != null) {
            product.setSalePrice(request.salePrice());
        }
        if (request.purchaseCost() != null) {
            product.setPurchaseCost(request.purchaseCost());
        }
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByStoreIdAndBarcodeAndActiveTrue(storeId, barcode)
                .orElseThrow(() -> NotFoundException.productByBarcode(barcode));
        return ProductResponse.from(product);
    }
}
