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

| Module          | Entities                                          |
|-----------------|---------------------------------------------------|
| `admin`         | `AdminUser`                                       |
| `stores`        | `Store`                                           |
| `subscriptions` | `SubscriptionPlan`, `Subscription`, `SaasPayment` |
| `users`         | `User`                                            |
| `products`      | `Product`                                         |
| `customers`     | `Customer`, `CustomerPayment`                     |
| `sales`         | `Sale`, `SaleItem`, `SaleCancellation`            |
| `inventory`     | `InventoryMovement`                               |
| `notifications` | `Notification`                                    |

### Auth Architecture

Two separate auth flows share the same JWT secret and filter:

| Actor       | Endpoint                   | Token claims                                    | Spring principal                              |
|-------------|----------------------------|-------------------------------------------------|-----------------------------------------------|
| Store users | `POST /api/v1/auth/login`  | `storeId`, `userId`, `role` (`OWNER`/`CASHIER`) | `UserPrincipal` → `TenantAuthenticationToken` |
| SaaS admins | `POST /api/v1/admin/login` | `adminId`, `role` (`SUPER_ADMIN`)               | `AdminPrincipal` → `AdminAuthenticationToken` |

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

| Exception                         | Status | `error` code                                                                          |
|-----------------------------------|--------|---------------------------------------------------------------------------------------|
| `BadCredentialsException`         | 401    | `invalid_credentials`                                                                 |
| `DisabledException`               | 403    | message (e.g. `store_inactive`)                                                       |
| `AccessDeniedException`           | 403    | `forbidden`                                                                           |
| `AuthenticationException` (rest)  | 401    | `unauthenticated`                                                                     |
| `MethodArgumentNotValidException` | 400    | `validation_failed` + `fields`                                                        |
| `NotFoundException`               | 404    | from `ex.getCode()` (e.g. `store_not_found`, `user_not_found`, `product_not_found`)                        |
| `DuplicateResourceException`      | 409    | from `ex.getCode()` (e.g. `store_code_taken`, `username_taken`, `store_owner_exists`, `barcode_taken`)     |
| `InvalidResourceStateException`   | 409    | from `ex.getCode()` (e.g. `store_not_active`, `role_not_allowed`)                     |
| `DataIntegrityViolationException` | 409    | `conflict`                                                                            |
| Filter-layer (via entry point)    | 401    | `invalid_token` / `user_inactive` / `store_inactive` / `admin_inactive`               |

Seed test data: run `SeedTestAdminUsersTest` → SQL in `target/seed/test-admin-users.sql`.

### Management Endpoints

CRUD endpoints follow the same shape: `POST` (201 + `Location`), `GET /{id}`, `GET` (paginated, optional filters),
`PUT /{id}`, `DELETE /{id}` (soft delete via status flag — never physical removal). Authorization is scoped per actor:

| Resource | Base path           | Role required        | Tenant scoping                                  | Soft-delete behavior                                |
|----------|---------------------|----------------------|-------------------------------------------------|-----------------------------------------------------|
| Stores   | `/api/v1/stores`    | `SUPER_ADMIN`        | None — global                                   | `status = CANCELED`; idempotent if already canceled |
| Users    | `/api/v1/users`     | `OWNER`              | `SecurityUtils.currentStoreId()` on every query | `active = false`; idempotent if already inactive    |
| Products | `/api/v1/products`  | `OWNER` (write), `OWNER`/`CASHIER` (read) | `SecurityUtils.currentStoreId()` on every query | `active = false`; idempotent if already inactive    |

**Products extra endpoint:** `GET /api/v1/products/search?barcode={barcode}` — lookup active product by barcode within the current store's tenant scope. Returns `product_not_found` if missing or inactive.

**Products list filter:** `active` param — omitted or `true` → only active products (`findByStoreIdAndActiveTrue`); `false` → all products (`findByStoreId`).

**`UnitOfMeasure` enum:** `PIECE`, `KILO` — colocated in `products.domain`, mapped `@Enumerated(EnumType.STRING)`, column `unit_of_measure VARCHAR(10)`.

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
`NotFoundException.product(id)`, `NotFoundException.productByBarcode(barcode)`,
`DuplicateResourceException.username(name)`, `DuplicateResourceException.barcode(barcode)`,
`InvalidResourceStateException.storeNotActive(id)`). Add new factories
here rather than instantiating with raw strings. Use `InvalidResourceStateException` for precondition failures on
existing resources (wrong status, disallowed role, etc.) — distinct from `DuplicateResourceException` (uniqueness
conflicts) and `NotFoundException` (missing).

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