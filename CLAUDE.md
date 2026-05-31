# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Sibling doc:** `AGENTS.md` is a near-duplicate maintained for OpenCode / other agents. Keep both in sync when editing architectural rules, env vars, or endpoint contracts.

## Role & Context

Act as a Senior Backend Engineer specialized in Java 21 developing the `grocery-store` project — a POS system built with Spring Boot 4.0.6.
**Base package:** `com.amx.grocerystore` — entry point `GroceryStoreApplication`.
**Stack:** Spring Web MVC, Spring Data JPA (PostgreSQL), Spring Security, Lombok, Bean Validation, JJWT 0.13.0 (pinned — Spring Boot 4.x does not manage `io.jsonwebtoken`), Spring Retry 2.0.12 (pinned — Boot 4.x does not manage `org.springframework.retry`) + `aspectjweaver` (BOM-managed; Boot 4 dropped `spring-boot-starter-aop`, `spring-aop` ships transitively via `spring-context`), bucket4j-core 8.10.1 (pinned — Boot 4.x does not manage `com.bucket4j`; powers login rate limiting).
**Tooling:** Maven wrapper (`./mvnw`) for all build/test commands.

## Build & Development Commands

- **Build:** `./mvnw compile`
- **Run tests:** `./mvnw test`
- **Single test class:** `./mvnw test -Dtest=ClassName`
- **Single test method:** `./mvnw test -Dtest=ClassName#methodName`
- **Package:** `./mvnw package`
- **Run application:** `./mvnw spring-boot:run`
- **Static analysis (lint):** Qodana — config in `qodana.yaml` (`profile: qodana.starter`, linter `jetbrains/qodana-jvm-community:2026.1`, JDK 21). Runs in CI/CD; locally via `qodana scan` (Docker). No failure thresholds set yet (`failureConditions` commented out).

## Architecture

Java 21 Spring Boot 4.0 app. **Domain-Oriented (Modular) Architecture** — code organized by feature (one aggregate per module), not by technical layer. Each module has `web`, `application`, `domain`, `infrastructure` sub-packages. Constructor injection only. DTOs at the API boundary — never expose JPA entities directly.

### Module Map

One aggregate per module, all under `com.amx.grocerystore.<module>.domain`:

|Module|Entities|
|-|-|
|`admin`|`AdminUser`|
|`stores`|`Store`, `StoreStation`|
|`subscriptions`|`SubscriptionPlan`, `Subscription`, `SaasPayment`|
|`users`|`User`|
|`products`|`Product`|
|`customers`|`Customer`, `CustomerPayment`|
|`sales`|`Sale`, `SaleItem`, `SaleCancellation`|
|`inventory`|`InventoryMovement`|
|`cash`|`CashRegisterShift`|
|`notifications`|`Notification`|
|`audit`|`AuditLog`|
|`dashboard`|`SalesTodayView`, `ProductToRestockView` (read-only `@Immutable` views — no aggregate)|

### FK Strategy (bounded-context friendly)

- **Same module (same aggregate):** `@ManyToOne(fetch = LAZY)` with `@JoinColumn`. Examples: `SaleItem.sale`, `CustomerPayment.customer`, `Subscription.plan`, `SaleCancellation.sale` (`@OneToOne` via UNIQUE), `StoreStation.store`.
- **Cross-module FKs:** plain `UUID` field, no JPA association. Examples: `storeId`, `userId`, `productId`, `customerId`, `saleId`, `canceledBy`, `referenceId`, `AuditLog.storeId`/`userId`/`entityId`. Resolve via repositories.

### Auth Architecture

Two flows share the same JWT secret and filter, distinguished by claim shape:

|Actor|Endpoint|Token claims|Spring principal|
|-|-|-|-|
|Store users|`POST /api/v1/auth/login`|`storeId`, `userId`, `role` (`OWNER`/`CASHIER`)|`UserPrincipal` → `TenantAuthenticationToken`|
|SaaS admins|`POST /api/v1/admin/login`|`adminId`, `role` (`SUPER_ADMIN`)|`AdminPrincipal` → `AdminAuthenticationToken`|

`JwtAuthenticationFilter` distinguishes by presence of `adminId` claim. **Never add `storeId` to admin tokens.**

**Per-request state revalidation:** filter re-queries the DB on every authenticated request.
- Store-user tokens: `User.findById` + `Store.findById(user.storeId)` — reject if `!user.active`, user missing, `store.status != ACTIVE`, or store missing.
- Admin tokens: `AdminUser.findById` — reject if `!admin.active` or admin missing.

Rejection delegates to `JwtAuthenticationEntryPoint` → JSON `ErrorResponse` with 401. Filter throws `AccountInactiveException(code)` for revoked state; claim/signature parse failures throw `InsufficientAuthenticationException("invalid_token")`.

**Login rate limiting (mandatory):** `AuthRateLimitFilter` (`core.security`, `OncePerRequestFilter`) sits ahead of `JwtAuthenticationFilter` (`addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter.class)`) and acts **only** on `POST /api/v1/auth/login` + `POST /api/v1/admin/login` (`shouldNotFilter` skips everything else). Per-IP token bucket (bucket4j, keyed by `request.getRemoteAddr()`): 10 attempts/minute, greedy refill. Overflow short-circuits with 429 `too_many_requests` + `Retry-After: 60` (writes `ErrorResponse` via the Jackson `ObjectMapper`, never calls the chain). Buckets live in an in-memory `ConcurrentHashMap` — `getRemoteAddr()` is the proxy IP behind a reverse proxy; `X-Forwarded-For` parsing + distributed/Redis-backed buckets are a future hardening, not yet wired.

### Error Handling

Single `@RestControllerAdvice`: `core.web.GlobalExceptionHandler` (no per-controller advices).
Error DTO: `core.web.dto.ErrorResponse` record (`error`, `message`, `fields`). Factories: `ErrorResponse.of(code)`, `ErrorResponse.validation(fields)`.

|Exception|Status|`error` code|
|-|-|-|
|`BadCredentialsException`|401|`invalid_credentials`|
|`DisabledException`|403|message (e.g. `store_inactive`)|
|`AccessDeniedException`|403|`forbidden`|
|`AuthenticationException` (rest)|401|`unauthenticated`|
|`MethodArgumentNotValidException`|400|`validation_failed` + `fields`|
|`NotFoundException`|404|from `ex.getCode()` (e.g. `store_not_found`, `user_not_found`, `product_not_found`, `open_shift_not_found`, `sale_not_found`, `station_not_found`)|
|`DuplicateResourceException`|409|from `ex.getCode()` (e.g. `store_code_taken`, `username_taken`, `store_owner_exists`, `barcode_taken`)|
|`InvalidResourceStateException`|409|from `ex.getCode()` (e.g. `store_not_active`, `role_not_allowed`, `reason_not_allowed`, `customer_required`, `credit_limit_exceeded`, `invalid_mixed_payment`, `shift_already_open`, `shift_not_open`, `sale_already_canceled`)|
|`DataIntegrityViolationException`|409|`conflict`|
|Rate-limit filter (`AuthRateLimitFilter`)|429|`too_many_requests` (+ `Retry-After: 60`)|
|Filter-layer (via entry point)|401|`invalid_token` / `user_inactive` / `store_inactive` / `admin_inactive`|

**Domain exceptions** (`core.web.exception.*`) carry codes via static factories — e.g. `NotFoundException.store(id)`, `NotFoundException.station(id)`, `DuplicateResourceException.username(name)`, `InvalidResourceStateException.creditLimitExceeded()`. **Add new factories here** rather than instantiating with raw strings.
- `InvalidResourceStateException` — precondition failures on existing resources (wrong status, disallowed role/reason, invalid payment split).
- `DuplicateResourceException` — uniqueness conflicts.
- `NotFoundException` — missing resources.

**Concurrency resilience (mandatory):** any service operation prone to deadlocks or concurrency-driven integrity violations — multi-row writes that contend on the same stock/balance rows under concurrent cashiers (e.g. `SaleService.createSale`, `cancelSale`) — **MUST** be wrapped in `@Retryable(retryFor = {DataIntegrityViolationException, CannotAcquireLockException}, backoff = @Backoff(delay = 200, multiplier = 2))`. `@EnableRetry` keeps the retry advisor outer to `@Transactional` so each attempt rolls back and re-opens the tx; after attempts exhaust the original exception propagates → `GlobalExceptionHandler` → 409. Do **not** catch-and-swallow these inside the service.

### Management Endpoints

CRUD endpoints share shape: `POST` (201 + `Location`), `GET /{id}`, `GET` (paginated, optional filters), `PUT /{id}`, `DELETE /{id}` (soft delete via status flag — **never physical removal**).

|Resource|Base path|Role|Tenant scoping|Soft-delete|
|-|-|-|-|-|
|Stores|`/api/v1/stores`|`SUPER_ADMIN`|None — global|`status = CANCELED`; idempotent|
|Users|`/api/v1/users`|`OWNER`|`SecurityUtils.currentStoreId()`|`active = false`; idempotent|
|Products|`/api/v1/products`|`OWNER` (write), `OWNER`/`CASHIER` (read)|`SecurityUtils.currentStoreId()`|`active = false`; idempotent|
|Customers|`/api/v1/customers`|`OWNER`/`CASHIER`|`SecurityUtils.currentStoreId()`|`active = false`; idempotent|

`UserService` resolves `storeId` from the authenticated principal (never trust client input); uses `findByIdAndStoreId` / `findByStoreIdAnd*` for all reads/writes. Cross-store access returns `user_not_found`.

**Pagination DTO:** `core.web.dto.PageResponse<T>` (record) — exposes `content`, `page`, `size`, `totalElements`, `totalPages`. Use `PageResponse.from(page.map(Response::from))`.

**Server-side search (stores, products, customers):** optional `search` `@RequestParam`. Case-insensitive `LIKE %search%` over `name`/`code` (stores), `name`/`barcode` (products), `fullName`/`phone` (customers). Single `@Query` per repo combines filters using `(:status IS NULL OR ...)` for typed enums/booleans and `(:search = '' OR ...)` for text — the empty-string sentinel avoids PostgreSQL's `function lower(bytea) does not exist` error from JDBC binding `null` without type metadata. **Service contract:** convert `search == null` → `""` before calling the repo. Repos: `StoreRepository.findStores(status, search, pageable)` (status as `String`, service passes `statusFilter.name()`), `ProductRepository.findProducts(storeId, active, search, pageable)`, `CustomerRepository.findCustomers(storeId, active, search, pageable)`. `users` module intentionally excluded (low-volume).

### Endpoint Contracts (one-line — see controller/service for details)

- **`GET /api/v1/products/search?barcode={barcode}`** (`ProductController`) — active barcode lookup, current-store scope; `product_not_found` if missing/inactive.
- **`PATCH /api/v1/products/{id}/price`** (`OWNER`) — partial price update via `UpdateProductPricesRequest`; both fields `@PositiveOrZero` and optional; only non-null fields written. When the **sale price actually changes** (`salePrice` non-null and `compareTo` differs), publishes `ProductPriceChangedEvent` after save → audit trail (see Audit Module). Purchase-cost-only/no-op edits do not emit.
- **`GET /api/v1/audit-logs`** (`AuditLogController`, `OWNER`, `@PageableDefault(size = 20)`) — paginated audit history for the current store (`SecurityUtils.currentStoreId()`), `created_at DESC` (`AuditLogRepository.findByStoreIdOrderByCreatedAtDesc`). `AuditLogService` builds an in-memory `userId → fullName` map from `UserRepository.findByStoreId(storeId)`; `AuditLogResponse.userName` → `"Sistema"` when `userId` is null or unresolved. Returns `PageResponse<AuditLogResponse>` (see Audit Module).
- **`POST /api/v1/admin/stores/{storeId}/users`** (`AdminUserProvisioningController`, `SUPER_ADMIN`) — bootstraps the initial `OWNER`. Calls `UserService.provisionUserForStore`; enforces store active, `role == OWNER`, no existing active owner, username free.
- **`POST /api/v1/customers/{id}/payments`** (`OWNER`/`CASHIER`) — credit repayment. Trigger `fn_apply_payment` decrements `customers.outstanding_balance` AFTER INSERT — service never mutates balance directly. Publishes `CustomerPaymentEvent`.
- **`POST /api/v1/inventory/movements`** (`OWNER`/`CASHIER`) — `AdjustStockRequest`. `quantity` is a **signed ledger delta** (positive = in, negative = out). Rejects `SALE`/`CANCELLATION` reasons (`reason_not_allowed`) — those are trigger-owned. Allowed: `RESTOCK`, `LOSS`, `ADJUSTMENT`. Negative stock → 409 `conflict` (DB CHECK). On `LOSS`, publishes `InventoryLossEvent`.
- **`GET /api/v1/inventory/movements?productId={id}`** (`OWNER`/`CASHIER`) — paginated **Kárdex** (movement history) for one product, tenant-scoped via `SecurityUtils.currentStoreId()`, `created_at DESC` (`InventoryMovementRepository.findByStoreIdAndProductIdOrderByCreatedAtDesc`). Returns `PageResponse<InventoryMovementResponse>` (`id`, `productId`, `quantity`, `reason`, `notes`, `createdAt`). **No search** (deliberate — simple list). `productId` is a **required** `@RequestParam` (missing → 400). Foreign-store `productId` → empty page (store scoping, never 404).
- **`GET /api/v1/dashboard/sales-today`** (`DashboardController`, `OWNER`) — day-of-sales summary for the current store (`SecurityUtils.currentStoreId()`), reads `v_sales_today` via `SalesTodayViewRepository.findById(storeId)`. Returns `SalesTodayResponse` (`tickets`, `dayTotal`, `dayCollected`, `dayOnCredit`). **No view row (no completed sales yet today) → zero DTO** (`SalesTodayResponse.empty()`: `0` + `BigDecimal.ZERO`), never 404 (see Dashboard Module).
- **`GET /api/v1/dashboard/products-to-restock`** (`DashboardController`, `OWNER`, `@PageableDefault`) — paginated restock list for the current store, reads `v_products_to_restock` via `ProductToRestockViewRepository.findByStoreId(storeId, pageable)` (view orders by `current_stock`). Returns `PageResponse<ProductToRestockResponse>` (`productId`, `name`, `currentStock`, `minimumStock`). Foreign-store rows never surface (store-scoped query).
- **`POST /api/v1/cash/shifts/open`** (`OWNER`/`CASHIER`) — `shift_already_open` if open shift exists.
- **`GET /api/v1/cash/shifts/current`** (`OWNER`/`CASHIER`) — or `open_shift_not_found`.
- **`POST /api/v1/cash/shifts/current/close`** (`OWNER`/`CASHIER`) — computed `expected_balance = opening + cashSales + cashPayments` (windowed `opened_at`→close), `difference = real − expected`. `cashSales` = Σ `amount_paid` for `CASH`/`MIXED` `COMPLETED` sales; `cashPayments` = Σ `customer_payments.amount`. Publishes `ShiftClosedEvent`.
- **`GET /api/v1/cash/shifts`** (`OWNER`) — paginated audit history, `opened_at DESC`.
- **`POST /api/v1/sales`** (`OWNER`/`CASHIER`) — `total = Σ(qty × unitPrice)` `HALF_UP` scale 2. **Tenant scoping (VULN-01):** a non-null `customerId` is validated against the store via `customerRepository.findByIdAndStoreId(...)` **before** the payment switch — for **every** method, not just CREDIT/MIXED — so a cross-store customer → 404 `customer_not_found`; the loaded `Customer` is reused by the limit checks. Payment-method rules in `SaleService`: `CASH`/`CARD`/`TRANSFER` → full pay (customer optional); `CREDIT` → customer required + credit-limit check; `MIXED` → customer required + strict `0 < amount_paid < total`, credit-limit checked against `amountCredited` only. Triggers `fn_register_credit` / `fn_apply_sale_item` own balance + stock. **Deadlock retry:** `SaleService.createSale` (and `cancelSale`) carry `@Retryable(retryFor = {DataIntegrityViolationException, CannotAcquireLockException}, backoff = @Backoff(delay = 200, multiplier = 2))` — default 3 attempts (200ms, 400ms) so concurrent cashiers hitting a PostgreSQL deadlock (`40P01` → `CannotAcquireLockException`) retry on a fresh transaction instead of 500ing. `@EnableRetry` (on `GroceryStoreApplication`) makes the retry advisor outer to `@Transactional` (higher precedence: `LOWEST_PRECEDENCE - 1`), so each attempt rolls back and re-opens the tx. After attempts exhaust, the original exception propagates → `GlobalExceptionHandler` → 409 (no recovery method).
- **`POST /api/v1/sales/{id}/cancel`** (`OWNER`/`CASHIER`) — inserts `SaleCancellation`. Trigger `fn_cancel_sale` flips status, returns stock (`inventory_movements` reason `CANCELLATION`), reverses credit. Service must NOT mutate `sale.status`. Idempotent: 409 `sale_already_canceled`. Same `@Retryable` deadlock policy as `createSale`. Publishes `SaleCancelledEvent`.

**`UnitOfMeasure` enum:** `PIECE`, `KILO` — colocated in `products.domain`, `@Enumerated(EnumType.STRING)`, column `unit_of_measure VARCHAR(10)`.

**Customer balance encapsulation:** `Customer.outstandingBalance` is a ledger column owned by triggers `fn_register_credit` (credit-sale → adds) and `fn_apply_payment` (payment → subtracts). **Never expose on write DTOs.** `CreateCustomerRequest`/`UpdateCustomerRequest` carry only `fullName`, `phone`, `creditLimit`. Service forces `outstandingBalance = ZERO` and `active = true` on create. `creditLimit` is freely editable (business policy); balance is trigger-only.

### Print-Agent Bridge (Self-Healing IP Discovery)

A store has 1..N print stations (cajas). Each station's local print agent loses its LAN IP on DHCP re-lease. `store_stations` holds one row per station; `agent_url` (`VARCHAR(100)`, nullable) keeps the last-reported URL so the PWA never scans the LAN.

`StoreStation` is an intra-`stores`-module aggregate member: `@ManyToOne(fetch = LAZY)` to `Store`, plus `name`, `agentUrl`, `status` (`StoreStationStatus`: `ACTIVE`/`SUSPENDED`/`CANCELED`). Repository in `stores.infrastructure`. **Station creation is not yet exposed via API — stations are seeded via SQL.**

- **Agent ingest:** `PUT /api/v1/agent/stations/{stationId}/url` (`StoreAgentController`) — public route whitelisted in `SecurityConfig` outside `JwtAuthenticationFilter`. Auth via `X-Agent-Key` header compared **timing-safe** with `MessageDigest.isEqual(...)` (UTF-8 bytes) against `app.security.agent.api-key` (`AgentSecurityProperties`); null/mismatch → `AccessDeniedException` (403 `forbidden`). Never use `String.equals` here (timing attack). Body `UpdateAgentUrlRequest` (`url` `@NotBlank @Size(max=100) @Pattern(regexp = "^https?://.*")` — VULN-05 anti-stored-XSS: rejects `javascript:`/`data:` schemes → 400 `validation_failed`). Returns 204. Unknown station → 404 `station_not_found`.
- **PWA read:** `GET /api/v1/stores/me/stations` (`StoreProfileController`, `OWNER`/`CASHIER`) — JWT-scoped via `SecurityUtils.currentStoreId()`. Returns `List<StoreStationResponse>` (`id`, `name`, `agentUrl`, `status`) via `StoreStationRepository.findByStoreIdAndStatusNot(storeId, CANCELED)`. `CANCELED` excluded; empty list (never 404).

### Notifications & Async Events

Domain events (`<module>.domain.events.*Event` records) are published by services via `ApplicationEventPublisher` **after the persistence call returns** — listeners treat the business outcome as final and never re-throw to the producer.

**Owner-routed alerts are per-tenant and per-channel.** Every owner-facing event carries `UUID storeId` as its first component; `AdminNotificationEventListener.sendAdminAlert(storeId, text)` loads the `Store` via `StoreRepository.findById(storeId)` and routes by `Store.preferredAdminChannel` (`AdminNotificationChannel` enum, `null`/default → `WHATSAPP`): `TELEGRAM` → `TelegramBotClient.sendMessage(store.getTelegramChatId(), text)`, `WHATSAPP` → `EvolutionWhatsAppClient.sendMessage(store.getWhatsapp(), text)`. Missing store/number → no-op. There is **no** global owner-phone setting; never reintroduce one. **Customer-facing** alerts (`onCustomerPayment`) bypass the switch and always use WhatsApp with `event.customerPhone()` — clients do not use Telegram.

|Event|Publisher|Routed to|
|-|-|-|
|`ShiftClosedEvent`|`CashShiftService.close`|store admin channel (`event.storeId()`)|
|`CustomerPaymentEvent`|`CustomerService.registerPayment`|customer phone, WhatsApp only (`event.customerPhone()`)|
|`LowStockEvent`|`SaleService.createSale` (parsed from `notifications` rows post-sale)|store admin channel (`event.storeId()`)|
|`SaleCancelledEvent`|`SaleService.cancelSale`|store admin channel (anti-fraud)|
|`InventoryLossEvent`|`InventoryService.adjustStock` when `reason == LOSS`|store admin channel (shrinkage)|
|`ProductPriceChangedEvent`|`ProductService.updatePrices` when sale price changes|`audit_logs` row via `AuditEventListener` (DB, not a notification channel)|

**Async infra:** `core.config.AsyncConfig` enables `@EnableAsync` with `ThreadPoolTaskExecutor` (core=2, max=10, queue=50, prefix `async-`). All `@EventListener` handlers in `notifications.application.*` — plus `audit.application.AuditEventListener` — are `@Async`. **Backpressure (mandatory):** the executor uses `CallerRunsPolicy` — a saturated queue makes the **calling thread** run the task inline (added latency, never a `RejectedExecutionException`/500 for the cashier), never `AbortPolicy`. **Graceful shutdown:** `setWaitForTasksToCompleteOnShutdown(true)` + `setAwaitTerminationSeconds(30)` so queued notifications drain (≤30s) on restart instead of being dropped. **Retry infra:** `@EnableRetry` sits on `GroceryStoreApplication` (enables `@Retryable` on `SaleService` — see Sales endpoint).

**HTTP client rule (mandatory):** every outbound `RestClient` **MUST** be built on a `SimpleClientHttpRequestFactory` with strict `connectTimeout` 5s + `readTimeout` 10s (`Duration` setters). A timeout-less HTTP client is **prohibited** — a frozen upstream must never pin a request or `@Async` thread indefinitely. `EvolutionWhatsAppClient` and `TelegramBotClient` already comply; any new client follows the same contract.

**WhatsApp via Evolution API:** `notifications.infrastructure.EvolutionWhatsAppClient` wraps a Spring Boot 4 `RestClient`, POSTs to `/message/sendText/{instance}` with `apikey` header + `EvolutionMessageRequest(number, text)`. **Timeouts mandatory:** the `RestClient` is built with a `SimpleClientHttpRequestFactory` (connect 5s, read 10s via `Duration` setters) so a frozen gateway can't pin `@Async` threads indefinitely. HTTP call wrapped in `try-catch(Exception)` + `log.error` — gateway downtime, 4xx/5xx, and timeouts (`ResourceAccessException`, a `RuntimeException`) are **swallowed by design** so `@Async` threads never propagate stacktraces. Notifications are best-effort; the domain has already committed. Phone normalization: strip spaces, then prefix `52` only when input is exactly 10 digits (Mexican local → E.164). `null` phone → log warn + return.

**Telegram via Bot API:** `notifications.infrastructure.TelegramBotClient` (`@Service`, official free Bot API — resilient to WhatsApp number bans) wraps a `RestClient` (`baseUrl https://api.telegram.org`, same `SimpleClientHttpRequestFactory` 5s/10s timeouts as the WhatsApp client), POSTs to `/bot{token}/sendMessage` with JSON `TelegramMessageRequest(@JsonProperty("chat_id") chatId, text)`. Token injected via **explicit constructor** `@Value("${telegram.bot.token:}")` (not `@RequiredArgsConstructor` — avoids the Lombok/`@Value` constructor clash). Same failure-isolation contract as the WhatsApp client: `try-catch(Exception)` + `log.error`; empty token or empty `chatId` → `log.warn` + return (no-op). No `parse_mode` sent (body is strictly `chat_id` + `text`).

`onInventoryLoss` uses `event.quantity().abs()` for display — `InventoryMovement.quantity` is a signed ledger delta (LOSS rows are negative); ledger keeps the sign, the WhatsApp message shows magnitude.

**When adding a new notification:** define event record under `<source-module>.domain.events`, publish from the service **after** persistence, add `@Async @EventListener` method on `AdminNotificationEventListener` (or a new listener). For owner-facing alerts, include `UUID storeId` on the event and route via `sendAdminAlert(event.storeId(), text)` (channel-aware) — never a static phone. For customer-facing alerts, call `EvolutionWhatsAppClient.sendMessage(phone, text)` directly. **Do not** add HTTP error handling at the listener layer — the client owns failure isolation.

### Audit Module

General audit trail for critical admin actions. `audit` module, one aggregate `AuditLog` (`audit.domain`). Rows are **written by Java, not triggers**: a domain event is published after persistence and an `@Async @EventListener` inserts the row.

**Schema:** `audit_logs` (`script.sql` MODULE 5) — `id`, `store_id` (FK `stores`, `ON DELETE RESTRICT`), `user_id` (FK `users`, **nullable**, `ON DELETE SET NULL`), `action VARCHAR(50)`, `entity_type VARCHAR(50)`, `entity_id UUID`, `details JSONB`, `created_at TIMESTAMPTZ DEFAULT NOW()`. Index `idx_audit_logs_store_date (store_id, created_at DESC)`. **First and only JSONB column in the schema** — `AuditLog.details` is `Map<String, Object>` mapped via `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")` (Hibernate 6; the pattern for any future JSONB). `store_id`/`user_id`/`entity_id` are cross-module FKs → plain `UUID` (no JPA association). `created_at` is DB-owned (`insertable = false, updatable = false`, no `@PrePersist`).

**Write path:** `AuditEventListener` (`audit.application`, `@Component @RequiredArgsConstructor`, `@Async @EventListener`) consumes `ProductPriceChangedEvent` and saves an `AuditLog` (`action = "PRICE_CHANGED"`, `entityType = "PRODUCT"`, `entityId = productId`, `details = {oldPrice, newPrice}`). The **producing service captures `userId` via `SecurityUtils.currentUserId()` before publishing** — the `@Async` thread has no `SecurityContext`, so never call `SecurityUtils` inside the listener. Add new audited actions by publishing a new event (with `storeId` + `userId`) and adding a handler here.

**Read path:** `GET /api/v1/audit-logs` (`AuditLogController`, `OWNER`) → `AuditLogService.getStoreAuditLogs(Pageable)` — tenant-scoped via `currentStoreId()`, `AuditLogRepository.findByStoreIdOrderByCreatedAtDesc`. Resolves `userName` by building an in-memory `userId → fullName` map from `UserRepository.findByStoreId(storeId)` (cross-module read; loads all store users — fine for the low-volume `users` module); `null`/unresolved `userId` → `"Sistema"`. Returns `PageResponse<AuditLogResponse>` (`id`, `userId`, `userName`, `action`, `entityType`, `entityId`, `details`, `createdAt`) — entity never exposed.

### Dashboard Module

Read-only analytics over existing PostgreSQL views — **no aggregate, no writes**. `dashboard` module, two view entities in `dashboard.domain`: `SalesTodayView` (maps `v_sales_today`, `@Id storeId`) and `ProductToRestockView` (maps `v_products_to_restock`, `@Id productId`). Both annotated `@Entity @Immutable @Table(name = "v_…")` + Lombok `@Getter @NoArgsConstructor` only — `@Immutable` (Hibernate) disables dirty-checking on these never-written rows (the convention for any future read-model view). Under `ddl-auto=validate`, view columns must match the entity mapping exactly (PostgreSQL exposes views as tables to JDBC metadata, so validation passes).

**Repositories** (`dashboard.infrastructure`) extend the minimal `org.springframework.data.repository.Repository` (read-only surface, not `JpaRepository`): `SalesTodayViewRepository.findById(UUID storeId)` → `Optional`, `ProductToRestockViewRepository.findByStoreId(UUID storeId, Pageable)` → `Page`.

**Service/Web:** `DashboardService` (`@Transactional(readOnly = true)`, tenant-scoped via `SecurityUtils.currentStoreId()` — never trust client `storeId`). `getSalesToday()` maps the view or falls back to `SalesTodayResponse.empty()` (`0` + `BigDecimal.ZERO`) when no row exists (start of day, no completed sales) — **never 404/exception**. `getProductsToRestock(Pageable)` → `PageResponse.from(page.map(ProductToRestockResponse::from))`. `DashboardController` (`/api/v1/dashboard`, both endpoints `@PreAuthorize("hasRole('OWNER')")`): `GET /sales-today` → `SalesTodayResponse`; `GET /products-to-restock` (`@PageableDefault`) → `PageResponse<ProductToRestockResponse>`. View entities never exposed — records only.

## SOLID Principles

- **SRP:** one reason to change per class.
- **OCP:** interfaces and composition over inheritance.
- **LSP:** subclasses replaceable by base types.
- **ISP:** small, specific interfaces per client.
- **DIP:** depend on abstractions.

## Implementation Guidelines

- **Database source of truth:** `src/main/resources/script.sql` — schema, multi-tenancy (`store_id`), PostgreSQL triggers, views, integrity constraints. **Do not duplicate trigger logic in Java.**
- **Schema validation:** `spring.jpa.hibernate.ddl-auto=validate` — entity mappings must match `script.sql` exactly; Hibernate never alters the schema.
- **Modern Java:** records, sealed classes, switch pattern matching.
- **Boilerplate (Lombok):** entities → `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of = "id")`. **Avoid `@Data` on entities** (breaks lazy proxies). Services/components → `@RequiredArgsConstructor` + `final` fields.
- **Validation:** Bean Validation on all DTOs and entities.
- **Entity conventions:**
  - PK: `UUID id` + `@GeneratedValue(strategy = GenerationType.UUID)`. DB default `gen_random_uuid()` stays as safety net.
  - Types: `TIMESTAMPTZ` → `OffsetDateTime`, `DATE` → `LocalDate`, `NUMERIC(10,2)` → `BigDecimal`, `JSONB` → `Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")` (see `AuditLog.details`).
  - DB-filled timestamps (`created_at`, `updated_at`, `paid_at`): `@Column(insertable = false, updatable = false)`. **Do not** add `@PrePersist` — triggers/defaults own this.
  - Enums: one Java `enum` per CHECK constraint, `@Enumerated(EnumType.STRING)`, colocated in the same `domain` package.
- **Best practices:** prioritize patterns in `.claude/skills/java-springboot/SKILL.md`.

## Configuration

DB connection via env vars (defaults to `localhost` dev values): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`. HikariCP tuned in `application.properties` — `maximum-pool-size=25`, `minimum-idle=5` (widened for the per-request JWT DB revalidation + `@Async` notification listeners), `connection-timeout=30000`, `idle-timeout=600000`, `max-lifetime=1800000`.

**DB safety valves (mandatory):** the pool also enforces `connection-init-sql=SET statement_timeout = '15s'` — PostgreSQL kills any query running >15s at the connection level, so a single hung query can never monopolize the pool — and `leak-detection-threshold=60000` — a connection held >60s logs a stack trace to surface leaked/un-closed connections. Keep both set; never widen the pool to mask a slow query.

**Required env vars (boot fails without):**
- `JWT_SECRET` — Base64-encoded; must decode to ≥32 bytes for HS256 (validated in `JwtProperties`).

**Optional env vars:**
- `JWT_EXPIRATION_MINUTES` (default `480`). Issuer hardcoded `grocery-store` via `app.security.jwt.issuer`.
- `AGENT_API_KEY` (default `default-agent-secret`) — bound to `app.security.agent.api-key` via `AgentSecurityProperties`; shared static key for the print-agent bridge.
- `EVOLUTION_BASE_URL`, `EVOLUTION_APIKEY`, `EVOLUTION_INSTANCE` — Evolution / WhatsApp gateway. (Owner alerts route to the per-store `Store.whatsapp` column — no global phone env var.)
- `TELEGRAM_BOT_TOKEN` (default empty) — bound to `telegram.bot.token`, used by `TelegramBotClient` for stores with `preferred_admin_channel = 'TELEGRAM'`. Empty token → Telegram sends no-op. Owner alerts route to the per-store `Store.telegramChatId` column — no global chat-id env var.
- `CORS_ALLOWED_ORIGINS`, `CORS_ALLOWED_METHODS`, `CORS_ALLOWED_HEADERS`, `CORS_EXPOSED_HEADERS`, `CORS_ALLOW_CREDENTIALS`, `CORS_MAX_AGE` — bound to `app.cors.*`. Dev defaults include `http://localhost:5173`, `https://localhost:5174`, `http://192.168.68.69:5173`.

## Deployment & CI

`.github/workflows/deploy.yml` — push to `master` triggers build + deploy to a Netcup Ubuntu VPS.
- **Build:** Temurin JDK 21 (`actions/setup-java@v4`, Maven cache) → `./mvnw clean package -DskipTests` (tests gated to a separate flow).
- **Transfer:** `appleboy/scp-action` copies `target/*.jar` → `/opt/grocery-store` (`strip_components: 1`). Spring Boot's `*.jar.original` does not match `*.jar`, so only the runnable jar ships.
- **Restart:** `appleboy/ssh-action` renames `grocery-store-*.jar` → `/opt/grocery-store/ventacore.jar` (fixed name the unit points at — glob avoids clobbering the existing `ventacore.jar`), then `sudo systemctl restart ventacore` (deploy user has NOPASSWD) + `status --no-pager`.
- **Required repo secrets:** `SERVER_IP`, `SERVER_USER`, `SSH_PRIVATE_KEY`. systemd unit: `ventacore.service`.

## Testing

- **JUnit 5** via Spring Boot test starters (JPA, Security, Web MVC). Lombok annotation processing configured for main + test compilation.
- **Service tests:** pure Mockito (`@ExtendWith(MockitoExtension.class)`), no Spring context.
- **Controller tests:** `@WebMvcTest` + `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`; mock filter/entry point with `@MockitoBean`.
- **Env-dependent:** `GroceryStoreApplicationTests.contextLoads` (`@SpringBootTest`) opens a real DataSource — requires a running PostgreSQL matching `DB_*` env vars. Expected to fail locally without DB; not a regression.
- **Seed test data:** run `SeedTestAdminUsersTest` and `SeedTestUsersTest` → SQL written to `target/seed/*.sql`.

## Expected Output Format

1. **Design reasoning:** brief justification of modular design + SOLID compliance.
2. **Implementation:** code blocks organized by package structure.
3. **Verification:** specific `./mvnw test` command to verify.
