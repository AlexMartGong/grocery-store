# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Role & Context

Act as a Senior Backend Engineer specialized in Java 21 developing the `grocery-store` project — a POS system built with
Spring Boot 4.0.5
**Base package:** `com.amx.grocerystore` — the `@SpringBootApplication` entry point is `GroceryStoreApplication`.
**Stack:** Spring Web MVC, Spring Data JPA (PostgreSQL), Spring Security, Lombok, Bean Validation.
**Tooling:** Use the Maven wrapper (`./mvnw`) for all build and test commands.

## Build & Development Commands

- **Build:** `./mvnw compile`
- **Run tests:** `./mvnw test`
- **Run a single test class:** `./mvnw test -Dtest=ClassName`
- **Run a single test method:** `./mvnw test -Dtest=ClassName#methodName`
- **Package:** `./mvnw package`
- **Run application:** `./mvnw spring-boot:run`

## Architecture

Spring Boot 4.0 application (Java 21) for a grocery store. Uses Maven wrapper (`./mvnw`).
**Testing:** JUnit 5 via Spring Boot test starters (JPA test, Security test, Web MVC test). Lombok annotation processing
is configured for both main and test compilation.
Follow a **Domain-Oriented (Modular) Architecture** — organize code by functional feature (one aggregate per module)
rather than by technical layer.
**Internal module structure:** Each module contains `web`, `application`, `domain`, and `infrastructure` sub-packages.
**Dependency management:** Use constructor-based injection for all required dependencies.
**Data flow:** Use DTOs for the API layer; do not expose JPA entities directly.

### Module Map

One aggregate per module, all under `com.amx.grocerystore.<module>.domain`:

|Module|Entities|
|-|-|
|`admin`|`AdminUser`|
|`stores`|`Store`|
|`subscriptions`|`SubscriptionPlan`,`Subscription`,`SaasPayment`|
|`users`|`User`|
|`products`|`Product`|
|`customers`|`Customer`,`CustomerPayment`|
|`sales`|`Sale`, `SaleItem`, `SaleCancellation`|
|`inventory`|`InventoryMovement`|
|`cash`|`CashRegisterShift`|
|`notifications`|`Notification`|

### Auth Architecture

Two separate auth flows share the same JWT secret and filter:

|Actor|Endpoint|Token claims|Spring principal|
|-|-|-|-|
|Store users|`POST /api/v1/auth/login`|`storeId`,`userId`,`role`(`OWNER`/`CASHIER`)|`UserPrincipal`→`TenantAuthenticationToken`|
|SaaS admins|`POST /api/v1/admin/login`|`adminId`,`role`(`SUPER_ADMIN`)|`AdminPrincipal`→`AdminAuthenticationToken`|

`JwtAuthenticationFilter` distinguishes by presence of `adminId` claim. Never add `storeId` to admin tokens.

**Per-request state revalidation:** the filter re-queries the DB on every authenticated request.
Store-user tokens: `User.findById` + `Store.findById(user.storeId)` — reject if `!user.active`, user missing,
`store.status != ACTIVE`, or store missing.
Admin tokens: `AdminUser.findById` — reject if `!admin.active` or admin missing.
Rejection delegates to `JwtAuthenticationEntryPoint`, which writes a JSON `ErrorResponse` with 401.
The filter throws `AccountInactiveException(code)` for revoked state; claim/signature parse failures throw
`InsufficientAuthenticationException("invalid_token")`.

**Exception handling:** `core.web.GlobalExceptionHandler` is the single `@RestControllerAdvice` (no per-controller
advices).
Error DTO: `core.web.dto.ErrorResponse` record (`error`, `message`, `fields`). Factories: `ErrorResponse.of(code)`,
`ErrorResponse.validation(fields)`.

|Exception|Status|`error`code|
|-|-|-|
| `BadCredentialsException`|401|`invalid_credentials`|
| `DisabledException`|403|message (e.g. `store_inactive`)|
| `AccessDeniedException`|403|`forbidden`|
| `AuthenticationException` (rest)|401|`unauthenticated`|
| `MethodArgumentNotValidException`|400|`validation_failed` + `fields`|
| `NotFoundException`|404|from `ex.getCode()` (e.g. `store_not_found`, `user_not_found`, `product_not_found`, `open_shift_not_found`)|
| `DuplicateResourceException`|409|from `ex.getCode()` (e.g. `store_code_taken`, `username_taken`, `store_owner_exists`, `barcode_taken`)|
| `InvalidResourceStateException`|409|from `ex.getCode()` (e.g. `store_not_active`, `role_not_allowed`, `reason_not_allowed`, `customer_required`, `credit_limit_exceeded`, `invalid_mixed_payment`, `shift_already_open`, `shift_not_open`)|
| `DataIntegrityViolationException`|409|`conflict`|
| Filter-layer (via entry point)|401|`invalid_token` / `user_inactive` / `store_inactive` / `admin_inactive`|

Seed test data: run `SeedTestAdminUsersTest` → SQL in `target/seed/test-admin-users.sql`.

### Management Endpoints

CRUD endpoints follow the same shape: `POST` (201 + `Location`), `GET /{id}`, `GET` (paginated, optional filters),
`PUT /{id}`, `DELETE /{id}` (soft delete via status flag — never physical removal). Authorization is scoped per actor:

|Resource|Base path|Role required|Tenant scoping|Soft-delete behavior|
|-|-|-|-|-|
|Stores|`/api/v1/stores`|`SUPER_ADMIN`|None — global| `status = CANCELED`; idempotent if already canceled|
|Users| `/api/v1/users`| `OWNER`|`SecurityUtils.currentStoreId()`on every query|`active = false`; idempotent if already inactive|
|Products|`/api/v1/products`|`OWNER`(write),`OWNER`/`CASHIER` (read)|`SecurityUtils.currentStoreId()` on every query|`active = false`;idempotent if already inactive|
|Customers|`/api/v1/customers`|`OWNER`/`CASHIER` (read+write)|`SecurityUtils.currentStoreId()` on every query|`active = false`; idempotent if already inactive|

**Products extra endpoint:** `GET /api/v1/products/search?barcode={barcode}` — lookup active product by barcode within
the current store's tenant scope. Returns `product_not_found` if missing or inactive.

**Products quick-update endpoint:** `PATCH /api/v1/products/{id}/price` (`OWNER`) — partial price update via
`UpdateProductPricesRequest`. Both `sale_price` and `purchase_cost` are optional (`@PositiveOrZero`); only non-null
fields are written. No-op if both null. Product must be active; returns `product_not_found` if not.

**Products list filter:** `active` param — omitted or `true` → only active products; `false` → all products. Backed by
`ProductRepository.findProducts(storeId, active, search, pageable)` (`@Query`); service maps `active=false` →
`null` (predicate dropped), otherwise `TRUE`.

**Server-side search (stores, products, customers):** `GET /api/v1/stores`, `GET /api/v1/products`, and
`GET /api/v1/customers` accept optional `search` `@RequestParam`. Case-insensitive `LIKE %search%` over `name`/`code`
(stores), `name`/`barcode` (products), and `fullName`/`phone` (customers). Single `@Query` per repo combines all
filters using `(:status IS NULL OR ...)` / `(:active IS NULL OR ...)` for typed enums/booleans, and
`(:search = '' OR ...)` for the text term — the empty-string sentinel avoids PostgreSQL's
`function lower(bytea) does not exist` error caused by JDBC binding `null` without type metadata. **Service contract:**
convert `search == null` → `""` before calling the repo; never pass raw `null`. Repos:
`StoreRepository.findStores(status, search, pageable)` (status as `String`, service passes `statusFilter.name()`),
`ProductRepository.findProducts(storeId, active, search, pageable)`,
`CustomerRepository.findCustomers(storeId, active, search, pageable)`. `users` module is intentionally excluded
(low-volume).

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
`NotFoundException.openShift()`,
`DuplicateResourceException.username(name)`, `DuplicateResourceException.barcode(barcode)`,
`InvalidResourceStateException.storeNotActive(id)`, `InvalidResourceStateException.reasonNotAllowed(reason)`,
`InvalidResourceStateException.customerRequiredForCredit()`, `InvalidResourceStateException.creditLimitExceeded()`,
`InvalidResourceStateException.invalidMixedPayment()`, `InvalidResourceStateException.shiftAlreadyOpen()`,
`InvalidResourceStateException.shiftNotOpen()`). Add new factories here rather than instantiating with raw
strings. Use `InvalidResourceStateException` for precondition failures on existing resources (wrong status,
disallowed role, disallowed inventory reason, invalid payment split, etc.) — distinct from
`DuplicateResourceException` (uniqueness conflicts) and `NotFoundException` (missing).

**Customer payment endpoint:** `POST /api/v1/customers/{id}/payments` (`OWNER`/`CASHIER`) — register a credit
repayment via `CreatePaymentRequest(amount @Positive)`. Returns 201 + `CustomerPaymentResponse`. DB trigger
`fn_apply_payment` decrements `customers.outstanding_balance` AFTER INSERT; service never mutates the balance directly.
After the JPA save returns, `CustomerService.registerPayment` publishes `CustomerPaymentEvent(payment, customerName,
customerPhone)` via `ApplicationEventPublisher` so `WhatsAppEventListener.onCustomerPayment` can notify the customer
asynchronously.

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
`conflict`. `InventoryReason` enum colocated in `inventory.domain`.

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

**Sales endpoint:** `POST /api/v1/sales` (`OWNER`/`CASHIER`) — `CreateSaleRequest`
(`payment_method`, optional `customer_id`, optional `amount_paid` `@PositiveOrZero`, `items[]`). Returns 201 +
`SaleResponse`. `total = Σ(quantity × unitPrice)` rounded `HALF_UP` to scale 2. Payment-method rules:
- `CASH` / `CARD` / `TRANSFER`: `amountPaid = total`, `amountCredited = 0`. No customer required.
- `CREDIT`: `customer_id` required (`customer_required`); enforces `outstandingBalance + total ≤ creditLimit`
  (`credit_limit_exceeded`). `amountPaid = 0`, `amountCredited = total`.
- `MIXED`: `customer_id` required; `amount_paid` must satisfy `0 < amount_paid < total` strict
  (`invalid_mixed_payment`); `amountCredited = total − amountPaid`; credit limit validated against
  **`amountCredited` only**, not `total`.
DB owns the ledger: trigger `fn_register_credit` adds `amount_credited` to `customers.outstanding_balance` AFTER
INSERT when `amount_credited > 0 AND customer_id IS NOT NULL`; `fn_apply_sale_item` decrements stock per line. Service
never mutates balance directly. `GET /api/v1/sales` paginated, scoped to `currentStoreId()`.

### Notifications & Async Events

Domain events (`<module>.domain.events.*Event` records) are published by services via `ApplicationEventPublisher`
**after the persistence call returns** — listeners must treat the business outcome as final and never re-throw to the
producer. Current publishers: `CashShiftService.close` → `ShiftClosedEvent(CashShiftResponse)`;
`CustomerService.registerPayment` → `CustomerPaymentEvent(CustomerPaymentResponse, customerName, customerPhone)`.

**Async infra:** `core.config.AsyncConfig` enables `@EnableAsync` with a `ThreadPoolTaskExecutor`
(core=2, max=10, queue=50, prefix `async-`). All `@EventListener` handlers in `notifications.application.*` are
annotated `@Async` so event publication never blocks the request thread.

**WhatsApp via Evolution API:**
- `notifications.infrastructure.EvolutionWhatsAppClient` (`@Service`) wraps a Spring Boot 4 `RestClient` and POSTs to
  `/message/sendText/{instance}` with `apikey` header and body `EvolutionMessageRequest(number, text)` (DTO under
  `notifications.infrastructure.dto`).
- The HTTP call is wrapped in `try-catch(Exception)` + `log.error` — gateway downtime, 4xx/5xx, and timeouts are
  swallowed by design so `@Async` threads never propagate stacktraces. Notifications are best-effort; the domain has
  already committed.
- Phone normalization: strip spaces, then prefix `52` only when the input is exactly 10 digits (Mexican local → E.164).
  `null` phone → log warn + return.
- Configuration (`application.properties`):
  `evolution.api.base-url`, `evolution.api.apikey`, `evolution.api.instance`, `store.owner.phone` — all overridable via
  env vars (`EVOLUTION_BASE_URL`, `EVOLUTION_APIKEY`, `EVOLUTION_INSTANCE`, `STORE_OWNER_PHONE`).

**Current listeners (`WhatsAppEventListener`):**
- `onShiftClosed(ShiftClosedEvent)` → message to `${store.owner.phone}` with expected/real/difference balances.
- `onCustomerPayment(CustomerPaymentEvent)` → message to `event.customerPhone()` confirming the payment + new
  outstanding balance (re-read from `CustomerRepository`, since the trigger `fn_apply_payment` updates it post-commit).

When adding a new notification: define the event record under `<source-module>.domain.events`, publish from the
service after the persistence call, add an `@Async @EventListener` method on `WhatsAppEventListener` (or a new
listener), and route through `EvolutionWhatsAppClient.sendMessage(phone, text)`. Do **not** add new HTTP error
handling at the listener layer — the client owns failure isolation.

### FK Strategy (bounded-context friendly)

- **Same module (same aggregate):** `@ManyToOne(fetch = LAZY)` with `@JoinColumn`. Example: `SaleItem.sale`,
  `CustomerPayment.customer`, `Subscription.plan`, `SaleCancellation.sale` (`@OneToOne` via UNIQUE).
- **Cross-module FKs:** plain `UUID` field (no JPA association). Examples: `storeId`, `userId`, `productId`,
  `customerId`, `saleId`, `canceledBy`, `referenceId`. Resolve via repositories when needed.

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
  `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (all with `localhost`/dev defaults). HikariCP pool tuned in the same file.
- **Modern Java:** Leverage Java 21 features — `records`, `sealed classes`, and `switch` pattern matching.
- **Boilerplate:** Use Lombok. For entities:
  `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of = "id")` — **avoid `@Data` on
  entities** (breaks with lazy proxies). For services/components prefer `@RequiredArgsConstructor` with `final` fields.
- **Validation:** Apply Bean Validation on all DTOs and entities.
- **Entity conventions:**
    - PK: `UUID id` + `@GeneratedValue(strategy = GenerationType.UUID)`. DB default `gen_random_uuid()` stays as safety
      net.
    - Types: `TIMESTAMPTZ` → `OffsetDateTime`, `DATE` → `LocalDate`, `NUMERIC(10,2)` → `BigDecimal`.
    - DB-filled timestamps (`created_at`, `updated_at`, `paid_at`): `@Column(insertable = false, updatable = false)`. *
      *Do not** add `@PrePersist` — triggers/defaults own this.
    - Enums: one Java `enum` per CHECK constraint, mapped with `@Enumerated(EnumType.STRING)`, colocated in the same
      `domain` package.
- **Best Practices:** Always prioritize patterns defined in `.claude/skills/java-springboot/SKILL.md`.

## Expected Output Format

1. **Design Reasoning:** Brief technical justification of the modular design and SOLID compliance.
2. **Implementation:** Code blocks organized by the required package structure.
3. **Verification:** The specific `./mvnw test` command to verify the implementation.