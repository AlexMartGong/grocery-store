package com.amx.grocerystore.products.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code current_stock} is written concurrently by DB triggers (sales / cancellations), so this entity
 * must never rewrite it from a stale snapshot. {@link DynamicUpdate} limits each UPDATE to the columns
 * that actually changed: editing the price, image or status leaves {@code current_stock} alone.
 * Stock deltas go through {@code ProductRepository.addToCurrentStock} (relative, single statement).
 */
@Entity
@DynamicUpdate
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "barcode"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Size(max = 50)
    @Column(length = 50)
    private String barcode;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @DecimalMin("0.00")
    @Column(name = "purchase_cost", precision = 10, scale = 2)
    private BigDecimal purchaseCost;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "sale_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal salePrice;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "current_stock", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentStock;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "minimum_stock", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumStock;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 10)
    private UnitOfMeasure unitOfMeasure;

    @Size(max = 500)
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
