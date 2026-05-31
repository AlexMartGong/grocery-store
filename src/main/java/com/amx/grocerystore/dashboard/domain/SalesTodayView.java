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
@Table(name = "v_sales_today")
@Getter
@NoArgsConstructor
public class SalesTodayView {

    @Id
    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "tickets")
    private Long tickets;

    @Column(name = "day_total")
    private BigDecimal dayTotal;

    @Column(name = "day_collected")
    private BigDecimal dayCollected;

    @Column(name = "day_on_credit")
    private BigDecimal dayOnCredit;
}
