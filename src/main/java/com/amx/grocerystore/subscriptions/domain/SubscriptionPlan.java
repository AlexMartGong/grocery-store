package com.amx.grocerystore.subscriptions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
@Table(name = "subscription_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Stable business key the API subscribes stores by (STANDARD / PRO / BUSINESS).
     * Names are marketing copy and may change; this must not.
     */
    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String name;

    @NotNull
    @Column(name = "monthly_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyFee;

    @NotNull
    @Column(name = "setup_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal setupFee;

    /**
     * Entitlement: how many ACTIVE store_stations (cajas) this tier allows.
     * Enforced by {@code SubscriptionService.validateStationQuota}.
     */
    @Positive
    @Column(name = "max_stations", nullable = false)
    private int maxStations;

    @Column(name = "includes_notifications", nullable = false)
    private boolean includesNotifications;

    @Column(name = "includes_invoicing", nullable = false)
    private boolean includesInvoicing;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
