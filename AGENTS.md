# AGENTS.md

Comprehensive instruction file for OpenCode and AI Agent sessions on the `grocery-store` project.

> **Sibling doc:** `CLAUDE.md` is a near-duplicate maintained for Claude Code. Keep both in sync when editing architectural rules, env vars, or endpoint contracts.

## Role & Context

Act as a Senior Backend Engineer specialized in Java 21 developing the `grocery-store` project — a POS system built with
Spring Boot 4.0.6.
**Base package:** `com.amx.grocerystore` — the `@SpringBootApplication` entry point is `GroceryStoreApplication`.
**Stack:** Spring Web MVC, Spring Data JPA (PostgreSQL), Spring Security, Lombok, Bean Validation, JJWT 0.13.0 (pinned — Spring Boot 4.x does not manage `io.jsonwebtoken`), Spring Retry 2.0.12 (pinned — Boot 4.x does not manage `org.springframework.retry`) + `aspectjweaver` (BOM-managed; Boot 4 dropped `spring-boot-starter-aop`, `spring-aop` ships transitively via `spring-context`), bucket4j-core 8.10.1 (pinned — Boot 4.x does not manage `com.bucket4j`; powers login rate limiting).
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

Spring Boot 4.0 application (Java 21). **Testing:** JUnit 5 via Spring Boot test starters (JPA test, Security test,
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
| `subscriptions` | `SubscriptionPlan`, `Subscription`, `SaasPayment` |
| `users` | `User` |
| `products` | `Product` |
| `customers` | `Customer`, `CustomerPayment` |
| `sales` | `Sale`, `SaleItem`, `SaleCancellation` |
| `inventory` | `InventoryMovement` |
| `cash` | `CashRegisterShift` |
| `notifications` | `Notification` |
| `audit` | `AuditLog` |

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
| `NotFoundException` | 404 | from `ex.getCode()` (e.g. `store_not_found`, `user_not_found`, `product_not_found`, `customer_not_found`, `open_shift_not_found`, `sale_not_found`, `station_not_found`) |
| `DuplicateResourceException` | 409 | from `ex.getCode()` (e.g. `store_code_taken`, `username_taken`, `store_owner_exists`, `barcode_taken`) |
| `InvalidResourceStateException` | 409 | from `ex.getCode()` (e.g. `store_not_active`, `role_not_allowed`, `reason_not_allowed`, `customer_required`, `credit_limit_exceeded`, `invalid_mixed_payment`, `shift_already_open`, `shift_not_open`, `sale_already_canceled`) |
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

**Server-side search (stores, products, customers):** `GET /api/v1/stores`, `GET /api/v1/products`, and
`GET /api/v1/customers` accept optional `search` `@RequestParam`. Case-insensitive `LIKE %search%` over `name`/`code`
(stores), `name`/`barcode` (products), and `fullName`/`phone` (customers). Single `@Query` per repo combines all
filters using `(:status IS NULL OR ...)` / `(:active IS NULL OR ...)` for typed enums/booleans, and
`(:search = '' OR ...)` for the text term — the empty-string sentinel avoids PostgreSQL's
`function lower(bytea) does not exist` error caused by JDBC binding `null` without type metadata.
**Service contract:** convert `search == null` → `""` before calling the repo; never pass raw `null`.
Repos: `StoreRepository.findStores(status, search, pageable)` (status as `String`, service passes `statusFilter.name()`),
`ProductRepository.findProducts(storeId, active, search, pageable)`,
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
`DuplicateResourceException` (uniqueness conflicts) and `NotFoundException` (missing).

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
`CANCELED`). `StoreStationRepository` lives in `stores.infrastructure`. Station creation is not yet exposed via
API — stations are seeded via SQL.

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
**after the persistence call returns** — listeners must treat the business outcome as final and never re-throw to the
producer. Current publishers:
- `CashShiftService.close` → `ShiftClosedEvent(CashShiftResponse)`.
- `CustomerService.registerPayment` → `CustomerPaymentEvent(CustomerPaymentResponse, customerName, customerPhone)`.
- `SaleService.createSale` → `LowStockEvent(productName, remainingStock, minStock)` per pending low-stock notification, parsed from `notifications` rows after the sale persists.
- `SaleService.cancelSale` → `SaleCancelledEvent(saleId, totalAmount, reason)`.
- `InventoryService.adjustStock` → `InventoryLossEvent(productName, quantity, notes)` only when `reason == LOSS`.
- `ProductService.updatePrices` → `ProductPriceChangedEvent(storeId, userId, productId, oldPrice, newPrice)` only when the sale price changes. **Not** a notification — consumed by `audit.application.AuditEventListener`, which writes an `audit_logs` row (see Audit Module).

**Async infra:** `core.config.AsyncConfig` enables `@EnableAsync` with a `ThreadPoolTaskExecutor`
(core=2, max=10, queue=50, prefix `async-`). All `@EventListener` handlers in `notifications.application.*` — plus
`audit.application.AuditEventListener` — are annotated `@Async` so event publication never blocks the request thread. **Backpressure (mandatory):** the executor
uses `CallerRunsPolicy` — a saturated queue makes the **calling thread** run the task inline (added latency, never a
`RejectedExecutionException`/500 for the cashier), never `AbortPolicy`. **Graceful shutdown:**
`setWaitForTasksToCompleteOnShutdown(true)` + `setAwaitTerminationSeconds(30)` so queued notifications drain (≤30s) on
restart instead of being dropped. **Retry infra:** `@EnableRetry` sits on `GroceryStoreApplication`, enabling the
`@Retryable` deadlock policy on `SaleService` (see Sales endpoint).

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
- The HTTP call is wrapped in `try-catch(Exception)` + `log.error` — gateway downtime, 4xx/5xx, and timeouts
  (`ResourceAccessException`, a `RuntimeException`) are swallowed by design so `@Async` threads never propagate
  stacktraces. Notifications are best-effort; the domain has already committed.
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
- Same failure-isolation contract: `try-catch(Exception)` + `log.error`; empty token or empty `chatId` → `log.warn` +
  return (no-op). Body is strictly `chat_id` + `text` (no `parse_mode`).
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
for owner-facing alerts), publish from the service after the persistence call, add an `@Async @EventListener` method on
`AdminNotificationEventListener` (or a new listener). Route owner alerts through `sendAdminAlert(event.storeId(), text)`
(channel-aware); route customer alerts through `EvolutionWhatsAppClient.sendMessage(phone, text)` directly. Do **not**
add new HTTP error handling at the listener layer — the client owns failure isolation.

### Audit Module

General audit trail for critical admin actions. The `audit` module holds one aggregate, `AuditLog` (`audit.domain`).
Rows are **written by Java, not DB triggers**: a domain event is published after persistence and an `@Async @EventListener`
inserts the row.

**Schema** (`src/main/resources/script.sql` — `MODULE 5: AUDIT LOGS`): table `audit_logs` with `id`, `store_id`
(FK `stores`, `ON DELETE RESTRICT`), `user_id` (FK `users`, **nullable**, `ON DELETE SET NULL`), `action VARCHAR(50)`,
`entity_type VARCHAR(50)`, `entity_id UUID`, `details JSONB`, `created_at TIMESTAMPTZ DEFAULT NOW()`, plus index
`idx_audit_logs_store_date (store_id, created_at DESC)`. This is the **first and only JSONB column in the schema** —
`AuditLog.details` is `Map<String, Object>` mapped with Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)` +
`@Column(columnDefinition = "jsonb")`; reuse that pattern for any future JSONB column. `store_id`/`user_id`/`entity_id`
are cross-module FKs → plain `UUID` (no JPA association, per FK Strategy). `created_at` is DB-owned
(`@Column(insertable = false, updatable = false)`, no `@PrePersist`).

**Write path:** `AuditEventListener` (`audit.application`, `@Component @RequiredArgsConstructor`, `@Async @EventListener`)
consumes `ProductPriceChangedEvent` and saves an `AuditLog` (`action = "PRICE_CHANGED"`, `entityType = "PRODUCT"`,
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
  — shared static key for the print-agent bridge.
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

`.github/workflows/deploy.yml` — push to `master` triggers build + deploy to a Netcup Ubuntu VPS.
- **Build:** Temurin JDK 21 (`actions/setup-java@v4`, Maven cache) → `./mvnw clean package -DskipTests` (tests gated to a separate flow).
- **Transfer:** `appleboy/scp-action` copies `target/*.jar` → `/opt/grocery-store` (`strip_components: 1`). Spring Boot's `*.jar.original` does not match `*.jar`, so only the runnable jar ships.
- **Restart:** `appleboy/ssh-action` renames `grocery-store-*.jar` → `/opt/grocery-store/ventacore.jar` (fixed name the unit points at — glob avoids clobbering the existing `ventacore.jar`), then `sudo systemctl restart ventacore` (deploy user has NOPASSWD) + `status --no-pager`.
- **Required repo secrets:** `SERVER_IP`, `SERVER_USER`, `SSH_PRIVATE_KEY`. systemd unit: `ventacore.service`.

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
