package com.amx.grocerystore.products.infrastructure;

import com.amx.grocerystore.products.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Product> findByIdAndStoreIdAndActiveTrue(UUID id, UUID storeId);

    Page<Product> findByStoreId(UUID storeId, Pageable pageable);

    Page<Product> findByStoreIdAndActiveTrue(UUID storeId, Pageable pageable);

    List<Product> findAllByStoreIdAndActiveTrue(UUID storeId);

    Optional<Product> findByStoreIdAndBarcodeAndActiveTrue(UUID storeId, String barcode);

    boolean existsByStoreIdAndBarcode(UUID storeId, String barcode);

    boolean existsByStoreIdAndBarcodeAndIdNot(UUID storeId, String barcode, UUID id);

    @Query("SELECT p FROM Product p WHERE p.storeId = :storeId AND (:active IS NULL OR p.active = :active) AND (:search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> findProducts(@Param("storeId") UUID storeId,
                               @Param("active") Boolean active,
                               @Param("search") String search,
                               Pageable pageable);
}
