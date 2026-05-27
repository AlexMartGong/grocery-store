package com.amx.grocerystore.stores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stores")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$")
    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 150)
    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Size(max = 20)
    @Column(length = 20)
    private String whatsapp;

    @Size(max = 50)
    @Column(name = "telegram_chat_id", length = 50)
    private String telegramChatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_admin_channel", length = 20)
    private AdminNotificationChannel preferredAdminChannel;

    @Column(columnDefinition = "TEXT")
    private String address;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
