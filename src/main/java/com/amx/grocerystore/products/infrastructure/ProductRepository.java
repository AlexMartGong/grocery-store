package com.amx.grocerystore.products.infrastructure;

import com.amx.grocerystore.products.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    /**
     * Relative, single-statement stock change ({@code delta} is signed). The UPDATE's row lock serializes
     * it with concurrent sales ({@code fn_apply_sale_item}), so a committed decrement is never overwritten
     * the way a read-modify-write through the entity would. A negative result trips
     * {@code chk_positive_stock} → {@code DataIntegrityViolationException} → 409 {@code conflict}.
     * No {@code clearAutomatically}: clearing would drop the caller's pending persistence-context changes.
     */
    @Modifying
    @Query("UPDATE Product p SET p.currentStock = p.currentStock + :delta WHERE p.id = :id")
    int addToCurrentStock(@Param("id") UUID id, @Param("delta") BigDecimal delta);

    @Query("SELECT p FROM Product p WHERE p.storeId = :storeId AND (:active IS NULL OR p.active = :active) AND (:search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> findProducts(@Param("storeId") UUID storeId,
                               @Param("active") Boolean active,
                               @Param("search") String search,
                               Pageable pageable);
}
