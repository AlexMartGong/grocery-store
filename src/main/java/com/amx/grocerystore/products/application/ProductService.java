package com.amx.grocerystore.products.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.storage.StorageService;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.DuplicateResourceException;
import com.amx.grocerystore.core.web.exception.InvalidFileException;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.products.domain.Product;
import com.amx.grocerystore.products.domain.events.ProductPriceChangedEvent;
import com.amx.grocerystore.products.infrastructure.ProductRepository;
import com.amx.grocerystore.products.web.dto.ProductRequest;
import com.amx.grocerystore.products.web.dto.ProductResponse;
import com.amx.grocerystore.products.web.dto.UpdateProductPricesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    // extension derived from the validated MIME type, never from the client filename
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StorageService storageService;

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
    public PageResponse<ProductResponse> list(Boolean active, String search, Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        Boolean activeFilter = (active == null || active) ? Boolean.TRUE : null;
        String searchTerm = (search == null) ? "" : search;
        Page<Product> page = productRepository.findProducts(storeId, activeFilter, searchTerm, pageable);
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
        BigDecimal oldSalePrice = product.getSalePrice();
        boolean salePriceChanged = request.salePrice() != null
                && request.salePrice().compareTo(oldSalePrice) != 0;
        if (request.salePrice() != null) {
            product.setSalePrice(request.salePrice());
        }
        if (request.purchaseCost() != null) {
            product.setPurchaseCost(request.purchaseCost());
        }
        Product saved = productRepository.save(product);
        if (salePriceChanged) {
            eventPublisher.publishEvent(new ProductPriceChangedEvent(
                    storeId, SecurityUtils.currentUserId(), saved.getId(),
                    oldSalePrice, saved.getSalePrice()));
        }
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse uploadProductImage(UUID productId, MultipartFile file) {
        String extension = validateImage(file);
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByIdAndStoreIdAndActiveTrue(productId, storeId)
                .orElseThrow(() -> NotFoundException.product(productId));
        if (product.getImageUrl() != null) {
            storageService.deleteFile(extractObjectKey(product.getImageUrl()));
        }
        // timestamp suffix busts browser caches when the image is replaced
        String objectKey = "products/%s/%s_%d.%s"
                .formatted(storeId, productId, System.currentTimeMillis(), extension);
        String url = storageService.uploadFile(file, objectKey);
        product.setImageUrl(url);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse deleteProductImage(UUID productId) {
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByIdAndStoreIdAndActiveTrue(productId, storeId)
                .orElseThrow(() -> NotFoundException.product(productId));
        if (product.getImageUrl() != null) {
            storageService.deleteFile(extractObjectKey(product.getImageUrl()));
            product.setImageUrl(null);
            product = productRepository.save(product);
        }
        return ProductResponse.from(product);
    }

    private static String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw InvalidFileException.emptyFile();
        }
        String extension = ALLOWED_IMAGE_TYPES.get(file.getContentType());
        if (extension == null) {
            throw InvalidFileException.imageTypeNotAllowed();
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw InvalidFileException.imageTooLarge();
        }
        return extension;
    }

    private static String extractObjectKey(String imageUrl) {
        // public URL is <publicUrl>/<objectKey>; the key always starts at "products/"
        int index = imageUrl.indexOf("products/");
        return index >= 0 ? imageUrl.substring(index) : imageUrl;
    }

    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        UUID storeId = SecurityUtils.currentStoreId();
        Product product = productRepository.findByStoreIdAndBarcodeAndActiveTrue(storeId, barcode)
                .orElseThrow(() -> NotFoundException.productByBarcode(barcode));
        return ProductResponse.from(product);
    }
}
