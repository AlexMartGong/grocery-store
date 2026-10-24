# AGENTS.md

Comprehensive instruction file for OpenCode and AI Agent sessions on the `grocery-store` project.

> **Sibling doc:** `CLAUDE.md` is a near-duplicate maintained for Claude Code. Keep both in sync when editing architectural rules, env vars, or endpoint contracts.

## Role & Context

Act as a Senior Backend Engineer specialized in Java 21 developing the `grocery-store` project — a POS system built with
Spring Boot 4.1.2.
**Base package:** `com.amx.grocerystore` — the `@SpringBootApplication` entry point is `GroceryStoreApplication`.
**Stack:** Spring Web MVC, Spring Data JPA (PostgreSQL), Spring Security, Lombok, Bean Validation, JJWT 0.13.0 (pinned — Spring Boot 4.x does not manage `io.jsonwebtoken`), Spring Retry 2.0.13 (pinned — Boot 4.x does not manage `org.springframework.retry`) + `aspectjweaver` (BOM-managed; Boot 4 dropped `spring-boot-starter-aop`, `spring-aop` ships transitively via `spring-context`), bucket4j `bucket4j_jdk17-core` 8.21.0 (pinned — Boot 4.x does not manage `com.bucket4j`; the plain `bucket4j-core` artifact is frozen at 8.10.1; powers login rate limiting), AWS SDK v2 `s3` 2.57.3 (pinned — Boot 4.x does not manage `software.amazon.awssdk`; Cloudflare R2 product-image storage).
**Tooling:** Use the Maven wrapper (`./mvnw`) for all build and test commands.

## Build & Development Commands

| Task | Command |
|-|-|
| Build | `./mvnw compile` |
| All tests | `./mvnw test` |
| Single test class | `./mvnw test -Dtest=ClassName` |
| Single test method | `./mvnw test -Dtest=ClassName#methodName` |
| Package | `./mvnw package` |
| Run app | `./mvnw spring-boot:run` |
| Static analysis | Qodana (`qodana scan` via Docker) — config in `qodana.yaml` |

Qodana: `profile: qodana.starter`, linter `jetbrains/qodana-jvm-community:2026.1`, JDK 21. Runs in CI/CD; no failure thresholds set yet (`failureConditions` commented out).

## Architecture

Spring Boot 4.1 application (Java 21). **Testing:** JUnit 5 via Spring Boot test starters (JPA test, Security test,
Web MVC test). Lombok annotation processing is configured for both main and test compilation.
Follow a **Domain-Oriented (Modular) Architecture** — organize code by functional feature (one aggregate per module)
rather than by technical layer.
**Internal module structure:** Each module contains `web`, `application`, `domain`, and `infrastructure` sub-packages.
**Dependency management:** Use constructor-based injection for all required dependencies.
**Data flow:** Use DTOs for the API layer; do not expose JPA entities directly.

### Module Map

One aggregate per module, all under `com.amx.grocerystore.<module>.domain`:

| Module | Entities |
|-|-|
| `admin` | `AdminUser` |
| `stores` | `Store`, `StoreStation` |
| `subscriptions` | `SubscriptionPlan`, `Subscription`, `SaasPayment` (3 tiers + billing cycles — see Subscriptions & Billing Module) |
| `users` | `User` |
| `products` | `Product` |
| `customers` | `Customer`, `CustomerPayment` |
| `sales` | `Sale`, `SaleItem`, `SaleCancellation` |
| `inventory` | `InventoryMovement` |
| `cash` | `CashRegisterShift` |
| `notifications` | `Notification` |
| `audit` | `AuditLog` |
| `dashboard` | `SalesTodayView`, `ProductToRestockView` (read-only `@Immutable` views — no aggregate) |

### Auth Architecture

Two separate auth flows share the same JWT secret and filter:

| Actor | Endpoint | Token claims | Spring principal |
|-|-|-|-|
| Store users | `POST /api/v1/auth/login` | `storeId`, `userId`, `role` (`OWNER`/`CASHIER`) | `UserPrincipal` → `TenantAuthenticationToken` |
| SaaS admins | `POST /api/v1/admin/login` | `adminId`, `role` (`SUPER_ADMIN`) | `AdminPrincipal` → `AdminAuthenticationToken` |

`JwtAuthenticationFilter` distinguishes by presence of `adminId` claim. Never add `storeId` to admin tokens.

**Per-request state revalidation:** the filter re-queries the DB on every authenticated request.
Store-user tokens: `User.findById` + `Store.findById(user.storeId)` — reject if `!user.active`, user missing,
`store.status != ACTIVE`, or store missing.
Admin tokens: `AdminUser.findById` — reject if `!admin.active` or admin missing.
Rejection delegates to `JwtAuthenticationEntryPoint`, which writes a JSON `ErrorResponse` with 401.
The filter throws `AccountInactiveException(code)` for revoked state; claim/signature parse failures throw
`InsufficientAuthenticationException("invalid_token")`.

**Login rate limiting (mandatory):** `AuthRateLimitFilter` (`core.security`, `OncePerRequestFilter`) sits ahead of
`JwtAuthenticationFilter` (`addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter.class)`) and acts **only** on
`POST /api/v1/auth/login` + `POST /api/v1/admin/login` (`shouldNotFilter` skips everything else). Per-IP token bucket
(bucket4j, keyed by `request.getRemoteAddr()`): 10 attempts/minute, greedy refill. Overflow short-circuits with 429
`too_many_requests` + `Retry-After: 60` (writes `ErrorResponse` via the Jackson `ObjectMapper`, never calls the chain).
Buckets live in an in-memory `ConcurrentHashMap` — `getRemoteAddr()` is the proxy IP behind a reverse proxy;
`X-Forwarded-For` parsing + distributed/Redis-backed buckets are a future hardening, not yet wired.

### Exception Handling

`core.web.GlobalExceptionHandler` is the single `@RestControllerAdvice` (no per-controller advices).
Error DTO: `core.web.dto.ErrorResponse` record (`error`, `message`, `fields`). Factories: `ErrorResponse.of(code)`,
`ErrorResponse.validation(fields)`.

| Exception | Status | `error` code |
|-|-|-|
| `BadCredentialsException` | 401 | `invalid_credentials` |
| `DisabledException` | 403 | message (e.g. `store_inactive`) |
| `AccessDeniedException` | 403 | `forbidden` |
| `AuthenticationException` (rest) | 401 | `unauthenticated` |
| `MethodArgumentNotValidException` | 400 | `validation_failed` + `fields` |
| `NotFoundException` | 404 | from `ex.getCode()` (e.g. `store_not_found`, `user_not_found`, `product_not_found`, `customer_not_found`, `open_shift_not_found`, `sale_not_found`, `station_not_found`, `subscription_not_found`, `plan_not_found`) |
| `DuplicateResourceException` | 409 | from `ex.getCode()` (e.g. `store_code_taken`, `username_taken`, `store_owner_exists`, `barcode_taken`) |
| `InvalidResourceStateException` | 409 | from `ex.getCode()` (e.g. `store_not_active`, `role_not_allowed`, `reason_not_allowed`, `customer_required`, `credit_limit_exceeded`, `invalid_mixed_payment`, `shift_already_open`, `shift_not_open`, `sale_already_canceled`, `station_limit_exceeded`) |
| `InvalidFileException` | 400 | from `ex.getCode()` (`file_required`, `image_type_not_allowed`, `image_too_large`) |
| `MaxUploadSizeExceededException` | 400 | `image_too_large` (container multipart-limit backstop) |
| `DataIntegrityViolationException` | 409 | `conflict` |
| Rate-limit filter (`AuthRateLimitFilter`) | 429 | `too_many_requests` (+ `Retry-After: 60`) |
| Filter-layer (via entry point) | 401 | `invalid_token` / `user_inactive` / `store_inactive` / `admin_inactive` |

**Concurrency resilience (mandatory):** any service operation prone to deadlocks or concurrency-driven integrity
violations — multi-row writes contending on the same stock/balance rows under concurrent cashiers (e.g.
`SaleService.createSale`, `cancelSale`) — **MUST** be wrapped in
`@Retryable(retryFor = {DataIntegrityViolationException, CannotAcquireLockException}, backoff = @Backoff(delay = 200, multiplier = 2))`.
`@EnableRetry` keeps the retry advisor outer to `@Transactional` so each attempt rolls back and re-opens the tx; once
attempts exhaust the original exception propagates → `GlobalExceptionHandler` → 409. Do **not** catch-and-swallow these
inside the service.

Seed test data: run `SeedTestAdminUsersTest` → SQL in `target/seed/test-admin-users.sql`.

### Management Endpoints

CRUD endpoints follow the same shape: `POST` (201 + `Location`), `GET /{id}`, `GET` (paginated, optional filters),
`PUT /{id}`, `DELETE /{id}` (soft delete via status flag — never physical removal). Authorization scoped per actor:

| Resource | Base path | Role required | Tenant scoping | Soft-delete behavior |
|-|-|-|-|-|
| Stores | `/api/v1/stores` | `SUPER_ADMIN` | None — global | `status = CANCELED`; idempotent if already canceled |
| Users | `/api/v1/users` | `OWNER` | `SecurityUtils.currentStoreId()` on every query | `active = false`; idempotent if already inactive |
| Products | `/api/v1/products` | `OWNER` (write), `OWNER`/`CASHIER` (read) | `SecurityUtils.currentStoreId()` on every query | `active = false`; idempotent if already inactive |
| Customers | `/api/v1/customers` | `OWNER`/`CASHIER` (read+write) | `SecurityUtils.currentStoreId()` on every query | `active = false`; idempotent if already inactive |

**Products extra endpoint:** `GET /api/v1/products/search?barcode={barcode}` — lookup active product by barcode within
the current store's tenant scope. Returns `product_not_found` if missing or inactive.

**Products quick-update endpoint:** `PATCH /api/v1/products/{id}/price` (`OWNER`) — partial price update via
`UpdateProductPricesRequest`. Both `sale_price` and `purchase_cost` are optional (`@PositiveOrZero`); only non-null
fields are written. No-op if both null. Product must be active; returns `product_not_found` if not. When the **sale
price actually changes** (`salePrice` non-null and `compareTo` differs from the stored value), `ProductService.updatePrices`
publishes `ProductPriceChangedEvent(storeId, userId, productId, oldPrice, newPrice)` after the save — feeding the audit
trail (see Audit Module). Purchase-cost-only or no-op edits do not emit.

**Products list filter:** `active` param — omitted or `true` → only active products; `false` → all products. Backed by
`ProductRepository.findProducts(storeId, active, search, pageable)` (`@Query`); service maps `active=false` → `null`
(predicate dropped), otherwise `TRUE`.

**Product image endpoints:** `POST /api/v1/products/{id}/image` (`OWNER`) — multipart upload (`@RequestParam("file")`).
`ProductService.uploadProductImage` validates non-empty, MIME ∈ {`image/jpeg`, `image/png`, `image/webp`}, ≤2MB
(violations → `InvalidFileException` → 400); tenant-scoped via `findByIdAndStoreIdAndActiveTrue` (cross-store → 404
`product_not_found`). Object key `products/{storeId}/{productId}_{timestamp}.{ext}` (extension from MIME, never client
filename; timestamp busts browser cache); the previous image object is deleted first (best-effort). Returns 200 +
`ProductResponse` with the new `image_url`. `DELETE /api/v1/products/{id}/image` (`OWNER`) — deletes the R2 object
(best-effort), nulls `image_url`; idempotent when no image; 200 + `ProductResponse`. Multipart container limits
(`spring.servlet.multipart.max-file-size=3MB` / `max-request-size=4MB`) sit above the 2MB business limit so the
specific `InvalidFileException` code fires before the `MaxUploadSizeExceededException` backstop.

**Storage abstraction (`core.storage`):** `StorageService` interface (`uploadFile(MultipartFile, objectKey) → public
URL`, `deleteFile(objectKey)`), implemented by `R2StorageService` over AWS SDK v2 `S3Client` (bean in `StorageConfig`:
endpoint `https://{accountId}.r2.cloudflarestorage.com`, region `auto`, path-style access, static credentials from
`R2StorageProperties` / `app.storage.r2.*`). `deleteFile` swallows `S3Exception` (`log.warn`) — object deletion is
best-effort and must never fail the business operation. Reuse `StorageService` for any future file uploads.
`products.image_url VARCHAR(500)` stores the full public URL (`R2_PUBLIC_URL + "/" + objectKey`); live DBs need a
manual `ALTER TABLE products ADD COLUMN image_url VARCHAR(500);` (ddl-auto=validate never alters).

**Server-side search (stores, products, customers):** `GET /api/v1/stores`, `GET /api/v1/products`, and
`GET /api/v1/customers` accept optional `search` `@RequestParam`. Case-insensitive `LIKE %search%` over `name`/`code`
(stores), `name`/`barcode` (products), and `fullName`/`phone` (customers). Single `@Query` per repo combines all
filters using `(:status IS NULL OR ...)` / `(:active IS NULL OR ...)` for typed enums/booleans, and
`(:search = '' OR ...)` for the text term — the empty-string sentinel avoids PostgreSQL's
`function lower(bytea) does not exist` error caused by JDBC binding `null` without type metadata.
**Service contract:** convert `search == null` → `""` before calling the repo; never pass raw `null`.
Repos: `StoreRepository.findStores(status, search, pageable)` (status bound as the `StoreStatus` **enum**, never
`String` — a nullable filter must use the entity attribute's own Java type or Hibernate rejects the bind with
`QueryArgumentException`), `ProductRepository.findProducts(storeId, active, search, pageable)`,
`CustomerRepository.findCustomers(storeId, active, search, pageable)`. `users` intentionally excluded (low-volume).

**`UnitOfMeasure` enum:** `PIECE`, `KILO` — colocated in `products.domain`, mapped `@Enumerated(EnumType.STRING)`,
column `unit_of_measure VARCHAR(10)`.

**Admin provisioning:** `POST /api/v1/admin/stores/{storeId}/users` (`AdminUserProvisioningController`) — `SUPER_ADMIN`
only. Bootstraps the initial `OWNER` for a store. Calls `UserService.provisionUserForStore(storeId, request)`, which
takes `storeId` from the path (not the principal) and enforces: store exists, `store.status == ACTIVE`
(`store_not_active`), `request.role == OWNER` (`role_not_allowed`), no existing active owner
(`store_owner_exists` via `UserRepository.existsByStoreIdAndRoleAndActiveTrue`), username free (`username_taken`).

`UserService` resolves `storeId` from the authenticated principal (never trust client input) and uses
`findByIdAndStoreId` / `findByStoreIdAnd*` for all reads/writes. Cross-store access returns `user_not_found`. List
endpoints accept optional filters as `@RequestParam` (`status` for stores; `active`, `role` for users) and a Spring
`Pageable`.

**Pagination DTO:** `core.web.dto.PageResponse<T>` (record) — wraps `Page<T>` to expose only `content`, `page`, `size`,
`totalElements`, `totalPages`. Use `PageResponse.from(page.map(Response::from))`.

**Domain exceptions:** `core.web.exception.NotFoundException` (404), `DuplicateResourceException` (409), and
`InvalidResourceStateException` (409) carry an `error` code via static factories (`NotFoundException.store(id)`,
`NotFoundException.product(id)`, `NotFoundException.productByBarcode(barcode)`, `NotFoundException.customer(id)`,
`NotFoundException.openShift()`, `NotFoundException.sale(id)`,
`DuplicateResourceException.username(name)`, `DuplicateResourceException.barcode(barcode)`,
`InvalidResourceStateException.storeNotActive(id)`, `InvalidResourceStateException.reasonNotAllowed(reason)`,
`InvalidResourceStateException.customerRequiredForCredit()`, `InvalidResourceStateException.creditLimitExceeded()`,
`InvalidResourceStateException.invalidMixedPayment()`, `InvalidResourceStateException.shiftAlreadyOpen()`,
`InvalidResourceStateException.shiftNotOpen()`, `InvalidResourceStateException.saleAlreadyCanceled()`). Add new
factories here rather than instantiating with raw
strings. Use `InvalidResourceStateException` for precondition failures on existing resources (wrong status,
disallowed role, disallowed inventory reason, invalid payment split, etc.) — distinct from
`DuplicateResourceException` (uniqueness conflicts) and `NotFoundException` (missing). `InvalidFileException` (400)
covers rejected multipart uploads (empty file, disallowed MIME type, oversize) — factories `emptyFile()`,
`imageTypeNotAllowed()`, `imageTooLarge()`.

**Customer payment endpoint:** `POST /api/v1/customers/{id}/payments` (`OWNER`/`CASHIER`) — register a credit
repayment via `CreatePaymentRequest(amount @Positive)`. Returns 201 + `CustomerPaymentResponse`. DB trigger
`fn_apply_payment` decrements `customers.outstanding_balance` AFTER INSERT; service never mutates the balance directly.
After the JPA save returns, `CustomerService.registerPayment` publishes `CustomerPaymentEvent(payment, customerName,
customerPhone)` via `ApplicationEventPublisher` so `AdminNotificationEventListener.onCustomerPayment` can notify the
customer asynchronously.

**Customer balance encapsulation:** `Customer.outstandingBalance` is an accounting ledger column owned by DB triggers
`fn_register_credit` (credit-sale → adds) and `fn_apply_payment` (payment → subtracts). Never expose it on write DTOs.
`CreateCustomerRequest` / `UpdateCustomerRequest` carry only `fullName`, `phone`, `creditLimit`. `CustomerService.create`
forces `outstandingBalance = ZERO` and `active = true` server-side; `update` maps only the three editable fields.
`CustomerResponse` exposes the balance read-only for UI. `creditLimit` is freely editable (business policy), distinct
from the balance which is trigger-only. Same `findCustomers` `@Query` shape as `findProducts` (`active` + `search`
sentinels). `Customer ↔ CustomerPayment` colocated in `customers.domain`; `CustomerPayment.customer` is the only
intra-aggregate `@ManyToOne` (cross-module FKs stay as plain UUIDs per FK Strategy).

**Inventory movement endpoint:** `POST /api/v1/inventory/movements` (`OWNER`/`CASHIER`) — manual stock adjustment via
`AdjustStockRequest` (`productId`, `quantity` signed BigDecimal, `reason`, optional `notes`). Returns 201 with no body.
`quantity` is a signed ledger delta: positive = stock in, negative = stock out. Rejects `SALE` and `CANCELLATION`
reasons (`reason_not_allowed` 409) — those are owned by DB triggers `fn_apply_sale_item` / `fn_cancel_sale`. Allowed
reasons for this endpoint: `RESTOCK`, `LOSS`, `ADJUSTMENT`. Stock going negative is rejected by DB CHECK → 409
`conflict`. `InventoryReason` enum colocated in `inventory.domain`. After save, when `reason == LOSS`, publishes
`InventoryLossEvent(productName, quantity, notes)` via `ApplicationEventPublisher` for the shrinkage alert.

**Inventory movement history (Kárdex):** `GET /api/v1/inventory/movements?productId={id}` (`OWNER`/`CASHIER`) —
paginated read of one product's movement ledger, tenant-scoped via `SecurityUtils.currentStoreId()`, ordered
`created_at DESC` (`InventoryMovementRepository.findByStoreIdAndProductIdOrderByCreatedAtDesc`). Returns
`PageResponse<InventoryMovementResponse>` (`id`, `productId`, `quantity`, `reason`, `notes`, `createdAt`). **No search**
(deliberate — simple list, unlike stores/products/customers). `productId` is a required `@RequestParam` (missing → 400).
Store scoping makes a foreign-store `productId` return an empty page (never 404 — never probes existence).

**Dashboard analytics (`dashboard` module):** read-only metrics over existing PostgreSQL views — no aggregate, no writes.
View entities in `dashboard.domain` map the views one-to-one: `SalesTodayView` (`v_sales_today`, `@Id storeId`) and
`ProductToRestockView` (`v_products_to_restock`, `@Id productId`). Both are `@Entity @Immutable @Table(name = "v_…")` +
Lombok `@Getter @NoArgsConstructor` only — Hibernate `@Immutable` disables dirty-checking on never-written rows (the
convention for any future read-model view). Under `ddl-auto=validate`, columns must match the mapping exactly (PostgreSQL
exposes views as tables to JDBC metadata, so validation passes). Repositories (`dashboard.infrastructure`) extend the
minimal `org.springframework.data.repository.Repository` (read-only surface, not `JpaRepository`):
`SalesTodayViewRepository.findById(UUID storeId)` → `Optional`, `ProductToRestockViewRepository.findByStoreId(UUID
storeId, Pageable)` → `Page`. `DashboardService` is `@Transactional(readOnly = true)`, tenant-scoped via
`SecurityUtils.currentStoreId()` (never trust client `storeId`).
`GET /api/v1/dashboard/sales-today` (`DashboardController`, `OWNER`) → `SalesTodayResponse` (`tickets`, `dayTotal`,
`dayCollected`, `dayOnCredit`); when no view row exists (start of day, no completed sales) it returns
`SalesTodayResponse.empty()` (`0` + `BigDecimal.ZERO`) — **never 404 or an exception**.
`GET /api/v1/dashboard/products-to-restock` (`OWNER`, `@PageableDefault`) → `PageResponse<ProductToRestockResponse>`
(`productId`, `name`, `currentStock`, `minimumStock`; view orders by `current_stock`). View entities never exposed —
records only.

**Cash register shift endpoints:** `POST /api/v1/cash/shifts/open` (`OWNER`/`CASHIER`) — opens a shift via
`OpenShiftRequest` (`opening_balance` `@PositiveOrZero`). Returns 201 + `CashShiftResponse`. Fails with 409
`shift_already_open` if a shift is already `OPEN` for the store.
`GET /api/v1/cash/shifts/current` (`OWNER`/`CASHIER`) — returns the active `OPEN` shift or 404 `open_shift_not_found`.
`POST /api/v1/cash/shifts/current/close` (`OWNER`/`CASHIER`) — closes the active shift via `CloseShiftRequest`
(`real_balance` `@PositiveOrZero`, optional `notes`). Returns 200 + `CashShiftResponse` with computed fields:
`expected_balance = opening_balance + cashSales + cashPayments` (time-windowed from `opened_at` to close time),
`difference = real_balance − expected_balance`. `cashSales` = Σ `amount_paid` for `CASH`/`MIXED` `COMPLETED` sales;
`cashPayments` = Σ `customer_payments.amount`. DB never mutated directly — calculated in service, persisted on shift.
`GET /api/v1/cash/shifts` (`OWNER` only) — paginated audit history of all shifts for the store, ordered
`opened_at DESC`. Wrapped in `PageResponse<CashShiftResponse>`.
Cross-module: `SaleRepository.sumCashInflow(storeId, openedAt)` and new `CustomerPaymentRepository.sumPaymentsSince(storeId, openedAt)` provide the aggregation queries.

**Sale cancellation endpoint:** `POST /api/v1/sales/{id}/cancel` (`OWNER`/`CASHIER`) — `CancelSaleRequest`
(`@NotNull reason: CancellationReason`, optional `notes`). Returns 204. Service inserts `SaleCancellation`; DB
trigger `fn_cancel_sale` (script.sql) flips `sales.status` to `CANCELED`, returns each item's quantity to
`products.current_stock`, logs `inventory_movements` rows with `reason=CANCELLATION`, and reverses
`customers.outstanding_balance` if the original sale had `amount_credited > 0`. Service must NOT mutate
`sale.status` directly. Idempotent: re-cancel returns 409 `sale_already_canceled`. Cross-store access returns
`sale_not_found`. Repo additions: `SaleRepository.findByIdAndStoreId`, new `SaleCancellationRepository`.
After save, publishes `SaleCancelledEvent(saleId, totalAmount, reason.name())` via `ApplicationEventPublisher`
for the anti-fraud WhatsApp alert. Carries the same `@Retryable` deadlock policy as `createSale` (see Sales endpoint).

**Sales endpoint:** `POST /api/v1/sales` (`OWNER`/`CASHIER`) — `CreateSaleRequest`
(`payment_method`, optional `customer_id`, optional `amount_paid` `@PositiveOrZero`, `items[]`). Returns 201 +
`SaleResponse`. `total = Σ(quantity × unitPrice)` rounded `HALF_UP` to scale 2.
**Tenant scoping (VULN-01):** a non-null `customer_id` is validated against the store via
`customerRepository.findByIdAndStoreId(...)` **before** the payment switch — for **every** method, not just
CREDIT/MIXED — so a cross-store customer returns 404 `customer_not_found`; the loaded `Customer` is reused by the
credit-limit checks (no duplicate query). Payment-method rules:
- `CASH` / `CARD` / `TRANSFER`: `amountPaid = total`, `amountCredited = 0`. Customer optional, but validated if supplied.
- `CREDIT`: `customer_id` required (`customer_required`); enforces `outstandingBalance + total ≤ creditLimit`
  (`credit_limit_exceeded`). `amountPaid = 0`, `amountCredited = total`.
- `MIXED`: `customer_id` required; `amount_paid` must satisfy `0 < amount_paid < total` strict
  (`invalid_mixed_payment`); `amountCredited = total − amountPaid`; credit limit validated against
  **`amountCredited` only**, not `total`.
DB owns the ledger: trigger `fn_register_credit` adds `amount_credited` to `customers.outstanding_balance` AFTER
INSERT when `amount_credited > 0 AND customer_id IS NOT NULL`; `fn_apply_sale_item` decrements stock per line. Service
never mutates balance directly. `GET /api/v1/sales` paginated, scoped to `currentStoreId()`.
**Deadlock retry:** `SaleService.createSale` and `cancelSale` carry
`@Retryable(retryFor = {DataIntegrityViolationException, CannotAcquireLockException}, backoff = @Backoff(delay = 200, multiplier = 2))`
— default 3 attempts (200ms, 400ms). Concurrent cashiers tripping a PostgreSQL deadlock (`40P01` →
`CannotAcquireLockException`) retry on a fresh transaction instead of returning 500. `@EnableRetry` (on
`GroceryStoreApplication`) keeps the retry advisor outer to `@Transactional` (precedence `LOWEST_PRECEDENCE - 1`), so
each attempt rolls back and re-opens the tx; once attempts exhaust, the original exception propagates →
`GlobalExceptionHandler` → 409 (no recovery method).

### Print-Agent Bridge (Self-Healing IP Discovery)

A store has 1..N print stations (cajas). Each station's local print agent loses its LAN IP on DHCP re-lease.
The `store_stations` table holds one row per station; `store_stations.agent_url` (`VARCHAR(100)`, nullable,
mapped as `StoreStation.agentUrl`) keeps the agent's last-reported URL so the PWA never scans the LAN. Each
`StoreStation` is an intra-`stores`-module aggregate member: `@ManyToOne(fetch = LAZY)` to `Store` (same module
→ JPA association per FK Strategy), plus `name`, `agentUrl`, `status` (`StoreStationStatus`: `ACTIVE`/`SUSPENDED`/
`CANCELED`). `StoreStationRepository` lives in `stores.infrastructure`. Station creation is
`POST /api/v1/admin/stores/{storeId}/stations` (`AdminStoreStationController`, `SUPER_ADMIN`) and is
**quota-gated by the store's subscription tier** — see Subscriptions & Billing Module.

- **Agent ingest:** `PUT /api/v1/agent/stations/{stationId}/url` (`StoreAgentController`) — public route,
  whitelisted in `SecurityConfig` outside `JwtAuthenticationFilter`. Auth is the `X-Agent-Key` header compared
  **timing-safe** via `MessageDigest.isEqual(...)` (UTF-8 bytes) against `app.security.agent.api-key`
  (`AgentSecurityProperties`); null/mismatch throws `AccessDeniedException` (403 `forbidden`). Never `String.equals`
  here (timing attack). Body `UpdateAgentUrlRequest` (`url` `@NotBlank @Size(max=100) @Pattern(regexp = "^https?://.*")`
  — VULN-05 anti-stored-XSS: rejects `javascript:`/`data:` schemes → 400 `validation_failed`). Returns 204. `StoreService
  .updateAgentUrl(stationId, url)` loads the station by id and writes `agentUrl`. Unknown station → 404
  `station_not_found` (`NotFoundException.station(id)`).
- **PWA read:** `GET /api/v1/stores/me/stations` (`StoreProfileController`, `OWNER`/`CASHIER`) — JWT-scoped via
  `SecurityUtils.currentStoreId()`. Returns `List<StoreStationResponse>` (`id`, `name`, `agentUrl`, `status`) for
  the current store, via `StoreStationRepository.findByStoreIdAndStatusNot(storeId, CANCELED)` — `CANCELED`
  stations are excluded; empty list (never 404) when none.

### Notifications & Async Events

Domain events (`<module>.domain.events.*Event` records) are published by services via `ApplicationEventPublisher`
**after the persistence call returns**, but listeners fire **only after the transaction commits**
(`@TransactionalEventListener(phase = AFTER_COMMIT)`) so a rolled-back/retried tx — e.g. `SaleService.createSale`'s
`@Retryable` deadlock retry — never emits a phantom or duplicate alert. Listeners must treat the business outcome as
final and never re-throw to the producer. **Caveat:** `@TransactionalEventListener` silently drops events published with
**no active transaction** (`fallbackExecution` left default `false`); every current publisher is `@Transactional`, so
keep new publishers transactional too. Current publishers:
- `CashShiftService.close` → `ShiftClosedEvent(CashShiftResponse)`.
- `CustomerService.registerPayment` → `CustomerPaymentEvent(CustomerPaymentResponse, customerName, customerPhone)`.
- `SaleService.createSale` → `LowStockEvent(productName, remainingStock, minStock)` per pending low-stock notification, parsed from `notifications` rows after the sale persists.
- `SaleService.cancelSale` → `SaleCancelledEvent(saleId, totalAmount, reason)`.
- `InventoryService.adjustStock` → `InventoryLossEvent(productName, quantity, notes)` only when `reason == LOSS`.
- `ProductService.updatePrices` → `ProductPriceChangedEvent(storeId, userId, productId, oldPrice, newPrice)` only when the sale price changes. **Not** a notification — consumed by `audit.application.AuditEventListener`, which writes an `audit_logs` row (see Audit Module).

**Async infra:** `core.config.AsyncConfig` enables `@EnableAsync` and exposes **two** **virtual-thread**
`SimpleAsyncTaskExecutor` beans (`setVirtualThreads(true)`) — Java 21 virtual threads make the blocking ≤15s HTTP sends
cheap. (1) `taskExecutor` (prefix `async-`) is the default `@Async` qualifier — keep this name (renaming breaks default
resolution); used by `audit.application.AuditEventListener`. (2) `notificationExecutor` (prefix `notif-`) is **dedicated**
to the notification handlers: every method on `AdminNotificationEventListener` (`notifications.application.*`) is
`@Async("notificationExecutor") @TransactionalEventListener(AFTER_COMMIT)` — dispatch deferred to commit, run on a
virtual thread, so publication never blocks the request thread and never fires for an uncommitted tx. **Isolation
rationale (mandatory):** each send blocks up to ~15s on a slow WhatsApp/Telegram gateway; a separate pool keeps a
notification burst (e.g. low-stock alerts per sale) from starving other `@Async` work (audit writes) or pinning the
committing request thread behind the shared throttle. Route new notification listeners through `notificationExecutor`.
**Backpressure (mandatory):** each bean sets `setConcurrencyLimit(200)` — a semaphore throttle that, at the cap,
**blocks** the submitting thread until a slot frees (never a `RejectedExecutionException`/500 for the cashier); because
dispatch is post-commit the block never holds a DB tx/connection open. Widen the notification limit only when the gateway
is healthy — never to mask a slow upstream. **Graceful shutdown:** `setTaskTerminationTimeout(30_000)` (both beans) lets
active sends drain (≤30s) on restart instead of being dropped. **Retry infra:** `@EnableRetry` sits on
`GroceryStoreApplication`, enabling the `@Retryable` deadlock policy on `SaleService` (see Sales endpoint).

**HTTP client rule (mandatory):** every outbound `RestClient` **MUST** be built on a `SimpleClientHttpRequestFactory`
with strict `connectTimeout` 5s + `readTimeout` 10s (`Duration` setters). A timeout-less HTTP client is **prohibited** —
a frozen upstream must never pin a request or `@Async` thread indefinitely. `EvolutionWhatsAppClient` and
`TelegramBotClient` already comply; any new client follows the same contract.

**WhatsApp via Evolution API:**
- `notifications.infrastructure.EvolutionWhatsAppClient` (`@Service`) wraps a Spring Boot 4 `RestClient` and POSTs to
  `/message/sendText/{instance}` with `apikey` header and body `EvolutionMessageRequest(number, text)` (DTO under
  `notifications.infrastructure.dto`).
- **Timeouts mandatory:** the `RestClient` is built with a `SimpleClientHttpRequestFactory` (connect 5s, read 10s via
  `Duration` setters) so a frozen gateway can't pin `@Async` threads indefinitely.
- The HTTP call is wrapped in a **two-arm catch**: `catch(RestClientResponseException)` logs gateway status + response
  body (surfaces bad number/instance/apikey), `catch(Exception)` logs the full stacktrace via `log.error(..., e)`
  (surfaces `ResourceAccessException` timeouts). Both are swallowed by design so `@Async` threads never propagate. Pass
  the exception object, never `e.getMessage()`, so the cause is preserved. Notifications are best-effort; the domain has
  already committed.
- Phone normalization: strip spaces, then prefix `52` only when the input is exactly 10 digits (Mexican local → E.164).
  `null` phone → log warn + return.
- Configuration (`application.properties`):
  `evolution.api.base-url`, `evolution.api.apikey`, `evolution.api.instance` — all overridable via
  env vars (`EVOLUTION_BASE_URL`, `EVOLUTION_APIKEY`, `EVOLUTION_INSTANCE`). There is **no** global owner-phone
  setting — owner alerts route per-tenant (see below).

**Telegram via Bot API:**
- `notifications.infrastructure.TelegramBotClient` (`@Service`) — official free Bot API, hardens admin alerts against
  WhatsApp number bans. Wraps a `RestClient` (`baseUrl https://api.telegram.org`, same `SimpleClientHttpRequestFactory`
  5s/10s timeouts as the WhatsApp client), POSTs to `/bot{token}/sendMessage`
  with JSON `TelegramMessageRequest(@JsonProperty("chat_id") chatId, text)` (DTO under `notifications.infrastructure.dto`).
- Token injected via an **explicit constructor** with `@Value("${telegram.bot.token:}")` — **not**
  `@RequiredArgsConstructor` — to avoid the Lombok/`@Value` generated-constructor clash.
- Same failure-isolation contract: two-arm catch (`RestClientResponseException` → status + body, `Exception` → full
  stacktrace via `log.error(..., e)`), all swallowed; empty token or empty `chatId` → `log.warn` + return (no-op). Body is
  strictly `chat_id` + `text` (no `parse_mode`).
- Configuration: `telegram.bot.token` ← env `TELEGRAM_BOT_TOKEN` (default empty).

**Per-tenant, per-channel owner routing.** Every owner-facing event carries `UUID storeId` as its first component;
`AdminNotificationEventListener.sendAdminAlert(storeId, text)` loads the `Store` via `StoreRepository.findById(storeId)`
and routes by `Store.preferredAdminChannel` (`AdminNotificationChannel`, `null`/default → `WHATSAPP`): `TELEGRAM` →
`TelegramBotClient.sendMessage(store.getTelegramChatId(), text)`, `WHATSAPP` →
`EvolutionWhatsAppClient.sendMessage(store.getWhatsapp(), text)`. Missing store/number → no-op. Never reintroduce a
static owner-phone property.

**Current listeners (`AdminNotificationEventListener`, `@RequiredArgsConstructor` over `CustomerRepository`,
`StoreRepository`, `EvolutionWhatsAppClient`, `TelegramBotClient`):**
- `onShiftClosed(ShiftClosedEvent)` → `sendAdminAlert(event.storeId(), …)` with expected/real/difference balances.
- `onCustomerPayment(CustomerPaymentEvent)` → **WhatsApp only** (clients do not use Telegram):
  `whatsappClient.sendMessage(event.customerPhone(), …)` confirming the payment + new outstanding balance (re-read from
  `CustomerRepository`, since the trigger `fn_apply_payment` updates it post-commit).
- `onLowStock(LowStockEvent)` → `sendAdminAlert(event.storeId(), …)` warning about a product that's low or out of stock.
- `onSaleCancelled(SaleCancelledEvent)` → `sendAdminAlert(event.storeId(), …)` with anti-fraud alert (total amount,
  reason, ticket id).
- `onInventoryLoss(InventoryLossEvent)` → `sendAdminAlert(event.storeId(), …)` with shrinkage alert. Uses
  `event.quantity().abs()` for display because `InventoryMovement.quantity` is a signed ledger delta (LOSS rows are
  negative); the ledger keeps the sign, the message shows magnitude.

When adding a new notification: define the event record under `<source-module>.domain.events` (include `UUID storeId`
for owner-facing alerts), publish from the service after the persistence call, add an
`@Async @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` method on
`AdminNotificationEventListener` (or a new listener). Route owner alerts through `sendAdminAlert(event.storeId(), text)`
(channel-aware); route customer alerts through `EvolutionWhatsAppClient.sendMessage(phone, text)` directly. Do **not**
add new HTTP error handling at the listener layer — the client owns failure isolation.

### Subscriptions & Billing Module

Three SaaS tiers, seeded in `script.sql` SEED DATA and keyed on `subscription_plans.code` (the stable business
key — `name` is marketing copy and may change):

| `code` | Monthly | Yearly (`10 ×`) | `maxStations` | `includesNotifications` | `includesInvoicing` |
|--------|---------|-----------------|---------------|-------------------------|---------------------|
| `STANDARD` | $299.00 | $2,990.00 | 1 | ✗ | ✗ |
| `PRO` | $499.00 | $4,990.00 | 2 | ✓ | ✗ |
| `BUSINESS` | $899.00 | $8,990.00 | 5 | ✓ | ✓ |

The `BillingCycle` enum (`subscriptions.domain`: `MONTHLY`, `YEARLY`, column `billing_cycle VARCHAR(20)`) **owns the
cycle-dependent rules** so they cannot drift across the service and the DTOs: `feeFor(monthlyFee)` (yearly =
`monthlyFee × 10`, two months free — **computed, never stored**, so a price change reprices both cycles at once),
`advance(from)` (`plusMonths(1)` / `plusYears(1)`), and `paymentConcept()` (`MONTHLY_FEE` / `YEARLY_FEE`). This is a
deliberate exception to the bare-enum convention.

**Subscription history is append-only (mandatory).** A store has at most one `ACTIVE` subscription, enforced by the
partial unique index `idx_one_active_subscription ON subscriptions (store_id) WHERE status = 'ACTIVE'` (declared with
the `subscriptions` DDL in MODULE 1). Switching or renewing a plan **must not** UPDATE the existing row in place:
`SubscriptionService.changePlan` marks the current `ACTIVE` row `CANCELED` and INSERTs a new `ACTIVE` one, so past
pricing and cycle stay auditable next to the payments that reference them. **`saveAndFlush` is load-bearing:**
Hibernate's action queue executes INSERTs **before** UPDATEs, so the CANCEL must be flushed before the new row is
saved — otherwise the insert trips `idx_one_active_subscription` → `DataIntegrityViolationException` → 409. Verified
against PostgreSQL: two `ACTIVE` rows are rejected; cancel-then-insert in one transaction commits to 1 `ACTIVE` +
1 `CANCELED`.

**Station quota (mandatory).** `SubscriptionService.validateStationQuota(storeId, currentActiveStationsCount)` loads
the store's `ACTIVE` subscription and throws `InvalidResourceStateException.stationLimitExceeded(max)` when
`currentActiveStationsCount >= plan.maxStations`. The param is the count that **already** exists — the method reserves
a slot for the one being added. `StoreStationService.createStation` counts via
`StoreStationRepository.countByStoreIdAndStatus(storeId, ACTIVE)` and calls it **before** saving. Consequence: a store
with no `ACTIVE` subscription can no longer create stations at all (404 `subscription_not_found`) — the paid tier is
the gate. `changePlan` applies the mirror-image downgrade guard: a tier whose cap the store's existing stations
already overflow is rejected with `station_limit_exceeded`.

`SaasPayment.subscriptionId` is a **plain UUID with no association** (nullable FK, `ON DELETE SET NULL`, never
traversed — the payment record must outlive the subscription row it points at). `registerMonthlyPayment` remains an
**in-place** roll-forward of `nextBillingDate` (collecting a period's fee is not a subscription change) and is
cycle-aware: both the period length and the `concept` come from `BillingCycle`.

- **`GET /api/v1/admin/plans`** (`SubscriptionPlanController`, `SUPER_ADMIN`) — active tier catalog via
  `SubscriptionPlanRepository.findByActiveTrueOrderByMonthlyFeeAsc()`. Returns `List<SubscriptionPlanResponse>`
  (adds a computed `yearly_fee`).
- **`POST /api/v1/admin/stores/{storeId}/subscription`** (`StoreSubscriptionController`, `SUPER_ADMIN`) —
  `ChangePlanRequest` (`plan_code`, `billing_cycle`). Always **201** (every call creates a row). Unknown or inactive
  code → 404 `plan_not_found`; over-cap downgrade → 409 `station_limit_exceeded`.
- **`GET /api/v1/admin/stores/{storeId}/subscription`** (`StoreSubscriptionController`, `SUPER_ADMIN`) — the store's
  current plan, so the admin UI can show what it is changing *from*. Delegates to the same `getActiveSubscription` the
  tenant route uses. No `ACTIVE` subscription → 404 `subscription_not_found`, which clients treat as "no plan", not as
  an error.
- **`GET /api/v1/stores/me/subscription`** (`MySubscriptionController`, `OWNER`) — tenant-scoped via
  `SecurityUtils.currentStoreId()`; returns `SubscriptionResponse` carrying the plan entitlements so the PWA can gate
  features. No `ACTIVE` subscription → 404 `subscription_not_found`.
- **`GET /api/v1/admin/subscriptions/upcoming-billings`** (`AdminSubscriptionController`, `SUPER_ADMIN`) —
  `List<UpcomingBillingResponse>`, **not paginated** (the window is only days wide). ACTIVE subscriptions billing
  within `SubscriptionService.UPCOMING_WINDOW_DAYS` (7), soonest first. **Deliberately unbounded below** so rows that
  are already past due — still ACTIVE inside the scheduler's `GRACE_DAYS` — stay visible for collection. `amount_due`
  is `billingCycle.feeFor(plan.monthlyFee)`, **never the raw `monthlyFee`**; the store name and `whatsapp` are
  batch-joined via `StoreRepository.findAllById` because `Subscription.storeId` is a bare UUID, not an association.
- **`GET /api/v1/admin/subscriptions/summary`** (`AdminSubscriptionController`, `SUPER_ADMIN`) —
  `SubscriptionSummaryResponse` KPIs. `tier_distribution` is a `LinkedHashMap` **seeded from the plan catalog at
  `0L`** in ascending price order, so empty tiers still render and the client needs no second call; tiers that were
  since deactivated but still carry subscribers are appended. The 7-day count and amount are derived from the **same**
  query that backs `upcoming-billings`, so the header and the table cannot disagree.

**Live-DB migration:** `script.sql` is an initdb script (runs only on an empty volume), so already-provisioned
databases need `src/main/resources/migrations/2026-08-10-subscription-tiers.sql` applied by hand — idempotent,
transactional, and it backfills the legacy `Standard Plan` row onto `PRO` (matching price and features) and renames
it. Verified end-to-end: legacy schema → migration → `ddl-auto=validate` boots clean.
`2026-08-11-upcoming-billings-view.sql` follows it: the `v_upcoming_billings` view exposed `monthly_fee` and ignored
`billing_cycle` (understating a YEARLY subscriber tenfold), and is replaced with `billing_cycle` plus a cycle-aware
`amount_due`. `DROP`-then-`CREATE`, because `CREATE OR REPLACE VIEW` cannot rename a column.

### Audit Module

General audit trail for critical admin actions. The `audit` module holds one aggregate, `AuditLog` (`audit.domain`).
Rows are **written by Java, not DB triggers**: a domain event is published after persistence and an
`@Async @TransactionalEventListener(AFTER_COMMIT)` inserts the row once the producing tx commits.

**Schema** (`src/main/resources/script.sql` — `MODULE 5: AUDIT LOGS`): table `audit_logs` with `id`, `store_id`
(FK `stores`, `ON DELETE RESTRICT`), `user_id` (FK `users`, **nullable**, `ON DELETE SET NULL`), `action VARCHAR(50)`,
`entity_type VARCHAR(50)`, `entity_id UUID`, `details JSONB`, `created_at TIMESTAMPTZ DEFAULT NOW()`, plus index
`idx_audit_logs_store_date (store_id, created_at DESC)`. This is the **first and only JSONB column in the schema** —
`AuditLog.details` is `Map<String, Object>` mapped with Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)` +
`@Column(columnDefinition = "jsonb")`; reuse that pattern for any future JSONB column. `store_id`/`user_id`/`entity_id`
are cross-module FKs → plain `UUID` (no JPA association, per FK Strategy). `created_at` is DB-owned
(`@Column(insertable = false, updatable = false)`, no `@PrePersist`).

**Write path:** `AuditEventListener` (`audit.application`, `@Component @RequiredArgsConstructor`,
`@Async @TransactionalEventListener(AFTER_COMMIT)`) consumes `ProductPriceChangedEvent` and saves an `AuditLog` (`action = "PRICE_CHANGED"`, `entityType = "PRODUCT"`,
`entityId = productId`, `details = {oldPrice, newPrice}`). The **producing service captures `userId` via
`SecurityUtils.currentUserId()` before publishing** — the `@Async` thread carries no `SecurityContext`, so never call
`SecurityUtils` from inside the listener. To audit a new action, publish a new event (carrying `storeId` + `userId`) and
add a handler method here.

**Read path:** `GET /api/v1/audit-logs` (`AuditLogController`, `@PreAuthorize("hasRole('OWNER')")`,
`@PageableDefault(size = 20)`) → `AuditLogService.getStoreAuditLogs(Pageable)`. Tenant-scoped via
`SecurityUtils.currentStoreId()`; queries `AuditLogRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable)`. To
fill `userName`, the service builds an in-memory `userId → fullName` map from `UserRepository.findByStoreId(storeId)`
(cross-module read; loads all store users — acceptable for the low-volume `users` module). When `userId` is `null` or
absent from the map, `userName` falls back to `"Sistema"`. Returns `PageResponse<AuditLogResponse>` (`id`, `userId`,
`userName`, `action`, `entityType`, `entityId`, `details`, `createdAt`) — the entity is never exposed.

### FK Strategy (bounded-context friendly)

- **Same module (same aggregate):** `@ManyToOne(fetch = LAZY)` with `@JoinColumn`. Example: `SaleItem.sale`,
  `CustomerPayment.customer`, `Subscription.plan`, `SaleCancellation.sale` (`@OneToOne` via UNIQUE).
- **Cross-module FKs:** plain `UUID` field (no JPA association). Examples: `storeId`, `userId`, `productId`,
  `customerId`, `saleId`, `canceledBy`, `referenceId`, `AuditLog.storeId`/`userId`/`entityId`. Resolve via repositories
  when needed.

## SOLID Principles

Strictly adhere to SOLID in every implementation:

- **Single Responsibility:** Each class must have one reason to change.
- **Open/Closed:** Use interfaces and composition over inheritance.
- **Liskov Substitution:** Subclasses must be replaceable by their base types.
- **Interface Segregation:** Create small, specific interfaces per client.
- **Dependency Inversion:** Depend on abstractions, not concrete implementations.

## Implementation Guidelines

- **Database Source of Truth:** Always read and align to `src/main/resources/script.sql` for the database schema,
  multi-tenancy rules (`store_id`), PostgreSQL triggers, views, and integrity constraints. Do not duplicate trigger
  logic in Java.
- **Schema validation:** `spring.jpa.hibernate.ddl-auto=validate` — entity mappings must match `script.sql` exactly;
  Hibernate will never alter the schema.
- **DB connection:** Configured in `src/main/resources/application.properties` via env vars `DB_HOST`, `DB_PORT`,
  `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (all with `localhost`/dev defaults). HikariCP pool tuned in the same file:
  `maximum-pool-size=25`, `minimum-idle=5` (widened for per-request JWT DB revalidation + `@Async` listeners),
  `connection-timeout=30000`, `idle-timeout=600000`, `max-lifetime=1800000`.
- **DB safety valves (mandatory):** the pool also enforces `connection-init-sql=SET statement_timeout = '15s'` —
  PostgreSQL kills any query running >15s at the connection level, so a single hung query can never monopolize the
  pool — and `leak-detection-threshold=60000` — a connection held >60s logs a stack trace to surface leaked/un-closed
  connections. Keep both set; never widen the pool to mask a slow query.
- **Required env vars (boot fails without):** `JWT_SECRET` — Base64-encoded, must decode to ≥32 bytes for HS256
  (validated in `JwtProperties`). Optional: `JWT_EXPIRATION_MINUTES` (default `480`); issuer hardcoded `grocery-store`
  via `app.security.jwt.issuer`. Evolution/WhatsApp and Telegram (`TELEGRAM_BOT_TOKEN`) env vars listed in the Notifications section above.
  `AGENT_API_KEY` (default `default-agent-secret`, bound to `app.security.agent.api-key` via `AgentSecurityProperties`)
  — shared static key for the print-agent bridge. `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`
  (default empty), `R2_BUCKET_NAME` (default `ventacore-products`), `R2_PUBLIC_URL` (default empty) — bound to
  `app.storage.r2.*` via `R2StorageProperties`; Cloudflare R2 product-image storage. Empty creds must not fail boot —
  uploads then fail at request time (`StorageConfig.orPlaceholder` substitutes `"unconfigured"` for a blank account id and
  blank keys; `AwsBasicCredentials` rejects blank keys and newer AWS SDK releases (verified 2.57.3; 2.46.7
  did not) reject the host-less `https://.r2.cloudflarestorage.com` endpoint at `S3Client` build time).
- **Modern Java:** Leverage Java 21 features — `records`, `sealed classes`, and `switch` pattern matching.
- **Boilerplate:** Use Lombok. For entities:
  `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of = "id")` — **avoid `@Data` on
  entities** (breaks with lazy proxies). For services/components prefer `@RequiredArgsConstructor` with `final` fields.
- **Validation:** Apply Bean Validation on all DTOs and entities.
- **Entity conventions:**
    - PK: `UUID id` + `@GeneratedValue(strategy = GenerationType.UUID)`. DB default `gen_random_uuid()` stays as safety
      net.
    - Types: `TIMESTAMPTZ` → `OffsetDateTime`, `DATE` → `LocalDate`, `NUMERIC(10,2)` → `BigDecimal`, `JSONB` →
      `Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")` (Hibernate 6;
      see `AuditLog.details`).
    - DB-filled timestamps (`created_at`, `updated_at`, `paid_at`): `@Column(insertable = false, updatable = false)`.
      **Do not** add `@PrePersist` — triggers/defaults own this.
    - Enums: one Java `enum` per CHECK constraint, mapped with `@Enumerated(EnumType.STRING)`, colocated in the same
      `domain` package.
- **Best Practices:** Always prioritize patterns defined in `.claude/skills/java-springboot/SKILL.md`.

## Deployment & CI

`.github/workflows/deploy.yml` — push to `master` triggers an **SCP artifact-based deploy** on the Netcup Ubuntu VPS. **GitHub Actions compiles the runnable JAR** — the VPS never builds Java, only assembles the runtime image.
- **Build steps (runner):** `actions/checkout@v4` → `actions/setup-java@v4` (JDK 21 Temurin, `cache: maven`) → `chmod +x ./mvnw` → `./mvnw clean package -DskipTests`.
- **Transfer:** `appleboy/scp-action@v0.1.7` copies `target/*.jar,Dockerfile,docker-compose.yml,.dockerignore,src/main/resources/script.sql` to `/opt/grocery-store` (`strip_components: 0` keeps relative paths — jar lands at `target/*.jar` matching the Dockerfile `COPY`, `script.sql` keeps the path the compose volume mount expects; `overwrite: true`).
- **Deploy:** `appleboy/ssh-action@v1.2.0` (secrets `SERVER_IP`, `SERVER_USER`, `SSH_PRIVATE_KEY`) runs on the VPS: `cd /opt/grocery-store` → `docker compose up -d --build` → `docker image prune -f`. **No `git pull` on the VPS** — deploys no longer need the server clone to be current.
- **Server prerequisites (not in workflow):** `/opt/grocery-store` exists with `.env` present (gitignored — compose reads it); deploy user has docker permissions. Host Caddy keeps proxying `localhost:8080` (compose maps `8080:8080`).

### Docker / Compose (local + VPS deploy)

Root `Dockerfile` + `docker-compose.yml`; env template `.env.example` (copy to `.env` — gitignored, never commit).
- **Dockerfile:** **single-stage runtime-only** `eclipse-temurin:21-jre-alpine` — expects a pre-built jar in `target/` (CI builds it on the runner; locally run `./mvnw package -DskipTests` first). `WORKDIR /app`, non-root user `ventacore`, `COPY target/*.jar app.jar` (glob skips `*.jar.original`), `chown -R ventacore:ventacore /app` + `USER ventacore`, `EXPOSE 8080`, entrypoint `java -XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -jar app.jar`. `.dockerignore` uses `target/*` + `!target/*.jar` — only the runnable jar enters the context; VCS/IDE dirs and `.env*` stay out.
- **Compose services** (network `ventacore-net` bridge): `db` = `postgres:16-alpine` (container `ventacore-db`, volume `postgres_data` → fixed name `ventacore_postgres_data`, `pg_isready` healthcheck 5s×5); `app` builds from root Dockerfile (container `ventacore-app`, `8080:8080` — host Caddy keeps proxying `localhost:8080`), `depends_on: db: condition: service_healthy`, passes all `DB_*`/`JWT_*`/`AGENT_API_KEY`/`EVOLUTION_*`/`TELEGRAM_BOT_TOKEN`/`R2_*`/`CORS_ALLOWED_ORIGINS` env vars from `.env` with `DB_HOST=db`. `db` publishes `127.0.0.1:5432:5432` — loopback-only on the host, for SSH-tunnel DB access (`ssh -L 5432:127.0.0.1:5432 user@vps`); never exposed publicly. Note: still occupies host port 5432, so `docker compose up` clashes on machines running a host Postgres.
- **Schema init (critical):** `ddl-auto=validate` never creates schema, so compose mounts `./src/main/resources/script.sql` → `/docker-entrypoint-initdb.d/01-schema.sql:ro` — Postgres runs it **only on an empty volume**; a fresh stack boots fully initialized. Schema changes after volume creation still need manual `ALTER`/`psql` (initdb scripts don't re-run).
- **Compose default DB name is `grocerystore`** (`${DB_NAME:-grocerystore}`) — differs from the app's bare fallback `grocery_store`; harmless because compose passes `DB_NAME` to both containers. Keep `.env` values authoritative.
- **Usage:** `./mvnw package -DskipTests` then `docker compose up -d --build` (runtime-only Dockerfile needs the jar pre-built); validate config `docker compose config -q`. CI builds the jar on the runner and SCPs artifacts to the VPS (see Deployment & CI above) — the bare-jar/systemd flow is retired.

## Testing Patterns

- **Service tests:** Pure Mockito (`@ExtendWith(MockitoExtension.class)`), no Spring context.
- **Controller tests:** `@WebMvcTest` + `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`. Mock
  filter/entry point as `@MockitoBean`.
- **Env-dependent:** `GroceryStoreApplicationTests.contextLoads` (`@SpringBootTest`) opens a real DataSource —
  requires a running PostgreSQL matching `DB_*` env vars. Expected to fail locally without DB; not a regression.
- **Seed tests:** `SeedTestAdminUsersTest` generates SQL to `target/seed/test-admin-users.sql`.

## Expected Output Format

1. **Design Reasoning:** Brief technical justification of the modular design and SOLID compliance.
2. **Implementation:** Code blocks organized by the required package structure.
3. **Verification:** The specific `./mvnw test` command to verify the implementation.
