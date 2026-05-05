# AGENTS.md

Comprehensive instruction file for OpenCode and AI Agent sessions on the `grocery-store` project.

## Role & Context
Act as a Senior Backend Engineer specialized in Java 21 developing the `grocery-store` project — a POS system.

## Project Identity & Build Commands
- **Stack:** Spring Boot 4.0.5, Java 21, PostgreSQL, Maven wrapper (`./mvnw`).
- **Base package:** `com.amx.grocerystore` — entry point `GroceryStoreApplication`.
- **Spring starters:** Web MVC, Data JPA, Security, Validation. Lombok annotation processing is configured for both main and test compilation.

| Task | Command |
|-|-|
| Build | `./mvnw compile` |
| All tests | `./mvnw test` |
| Single test class | `./mvnw test -Dtest=ClassName` |
| Single test method | `./mvnw test -Dtest=ClassName#methodName` |
| Package | `./mvnw package` |
| Run app | `./mvnw spring-boot:run` |

## Architecture & Module Map
Follow a **Domain-Oriented (Modular) Architecture** — organize code by functional feature (one aggregate per module) rather than by technical layer.
Each module contains `web`, `application`, `domain`, and `infrastructure` sub-packages.
Use constructor-based injection for all dependencies. Use DTOs for the API layer; do not expose JPA entities directly.

| Module | Entities |
|-|-|
| `admin` | `AdminUser` |
| `stores` | `Store` |
| `subscriptions` | `SubscriptionPlan`, `Subscription`, `SaasPayment` |
| `users` | `User` |
| `products` | `Product` |
| `customers` | `Customer`, `CustomerPayment` |
| `sales` | `Sale`, `SaleItem`, `SaleCancellation` |
| `inventory` | `InventoryMovement` |
| `notifications` | `Notification` |

## Database, Schema & FK Strategy
- **Source of truth:** Always read and align to `src/main/resources/script.sql` for the database schema, multi-tenancy rules (`store_id`), PostgreSQL triggers, views, and integrity constraints.
- `spring.jpa.hibernate.ddl-auto=validate` — entity mappings must match `script.sql` exactly; Hibernate will never alter the schema.
- **Do not duplicate trigger logic in Java.** Triggers handle stock decrement, credit registration, payment application, and sale cancellation.
- **Same module FKs:** `@ManyToOne(fetch = LAZY)` with `@JoinColumn`.
- **Cross-module FKs:** plain `UUID` field (no JPA association). Examples: `storeId`, `userId`, `productId`, `customerId`, `saleId`, `canceledBy`, `referenceId`.

## Entity Conventions
- Boilerplate: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of = "id")`.
- **Avoid `@Data` on entities** (breaks with lazy proxies).
- PK: `UUID id` + `@GeneratedValue(strategy = GenerationType.UUID)`.
- Types: `TIMESTAMPTZ` → `OffsetDateTime`, `DATE` → `LocalDate`, `NUMERIC(10,2)` → `BigDecimal`.
- DB-filled timestamps (`created_at`, `updated_at`, `paid_at`): `@Column(insertable = false, updatable = false)`. **Do not add `@PrePersist`.**
- Enums: one Java `enum` per CHECK constraint, mapped with `@Enumerated(EnumType.STRING)`, colocated in the same `domain` package.

## Auth & Security Architecture
Two separate auth flows share the same JWT secret and filter:
- **Store users:** `POST /api/v1/auth/login`. Claims: `storeId`, `userId`, `role` (`OWNER`/`CASHIER`). Principal: `UserPrincipal` → `TenantAuthenticationToken`.
- **SaaS admins:** `POST /api/v1/admin/login`. Claims: `adminId`, `role` (`SUPER_ADMIN`). Principal: `AdminPrincipal` → `AdminAuthenticationToken`.

`JwtAuthenticationFilter` distinguishes by presence of `adminId` claim. Never add `storeId` to admin tokens.
**Per-request state revalidation:** the filter re-queries the DB on every authenticated request to reject inactive users or stores.

## Management Endpoints
CRUD endpoints follow the same shape: `POST` (201 + `Location`), `GET /{id}`, `GET` (paginated, optional filters), `PUT /{id}`, `DELETE /{id}` (soft delete via status flag — never physical removal).

| Resource | Base path | Role required | Tenant scoping | Soft-delete behavior |
|-|-|-|-|-|
| Stores | `/api/v1/stores` | `SUPER_ADMIN` | None — global | `status = CANCELED` |
| Users | `/api/v1/users` | `OWNER` | `SecurityUtils.currentStoreId()` | `active = false` |
| Products | `/api/v1/products` | `OWNER`(write), `OWNER`/`CASHIER` (read) | `SecurityUtils.currentStoreId()` | `active = false` |
| Customers | `/api/v1/customers` | `OWNER`/`CASHIER` (read+write) | `SecurityUtils.currentStoreId()` | `active = false` |

- **Pagination DTO:** `core.web.dto.PageResponse<T>` (record) — wraps `Page<T>` to expose only `content`, `page`, `size`, `totalElements`, `totalPages`.

## Exception Handling
`core.web.GlobalExceptionHandler` is the single `@RestControllerAdvice` (no per-controller advices).
Error DTO: `core.web.dto.ErrorResponse` record (`error`, `message`, `fields`).
Domain exceptions use static factories: `NotFoundException.store(id)`, `DuplicateResourceException.username(name)`, `InvalidResourceStateException.storeNotActive(id)`. Never instantiate exceptions with raw strings.[

## Business Logic Encapsulation
- **Customer balance:** `Customer.outstandingBalance` is an accounting ledger column owned by DB triggers `fn_register_credit` and `fn_apply_payment`. Never expose it on write DTOs.
- **Inventory movement:** `POST /api/v1/inventory/movements` handles manual stock adjustment (positive = stock in, negative = stock out).Rejects `SALE` and `CANCELLATION` reasons as those are owned by DB triggers. Allowed reasons: `RESTOCK`, `LOSS`, `ADJUSTMENT`.
- **Server-side search:** Single `@Query` per repo uses an empty-string sentinel (`:search = '' OR ...`) to avoid PostgreSQL `function lower(bytea) does not exist`. Service contract: convert `search == null` → `""` before calling the repo; never pass raw `null`. `users` intentionally does not have a search endpoint.

## Testing Patterns
- **Service tests:** Pure Mockito (`@ExtendWith(MockitoExtension.class)`), no Spring context.
- **Controller tests:** `@WebMvcTest` + `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`. Mock filter/entry point as `@MockitoBean`.
- **Seed tests:** `SeedTestAdminUsersTest` generates SQL to `target/seed/test-admin-users.sql`.

## SOLID Principles
- **Single Responsibility:** Each class must have one reason to change.
- **Open/Closed:** Use interfaces and composition over inheritance.
- **Liskov Substitution:** Subclasses must be replaceable by their base types.
- **Interface Segregation:** Create small, specific interfaces per client.
- **Dependency Inversion:** Depend on abstractions, not concrete implementations.