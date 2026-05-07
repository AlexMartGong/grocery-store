package com.amx.grocerystore.cash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_register_shifts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CashRegisterShift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status;

    @Column(name = "opened_at", insertable = false, updatable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @NotNull
    @Column(name = "opening_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "expected_balance", precision = 10, scale = 2)
    private BigDecimal expectedBalance;

    @Column(name = "real_balance", precision = 10, scale = 2)
    private BigDecimal realBalance;

    @Column(precision = 10, scale = 2)
    private BigDecimal difference;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
