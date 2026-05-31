package com.amx.grocerystore.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "v_products_to_restock")
@Getter
@NoArgsConstructor
public class ProductToRestockView {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "name")
    private String name;

    @Column(name = "current_stock")
    private BigDecimal currentStock;

    @Column(name = "minimum_stock")
    private BigDecimal minimumStock;
}
