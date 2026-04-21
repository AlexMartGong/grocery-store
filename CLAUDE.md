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

| Exception                         | Status | `error` code                                                            |
|-----------------------------------|--------|-------------------------------------------------------------------------|
| `BadCredentialsException`         | 401    | `invalid_credentials`                                                   |
| `DisabledException`               | 403    | message (e.g. `store_inactive`)                                         |
| `AccessDeniedException`           | 403    | `forbidden`                                                             |
| `AuthenticationException` (rest)  | 401    | `unauthenticated`                                                       |
| `MethodArgumentNotValidException` | 400    | `validation_failed` + `fields`                                          |
| Filter-layer (via entry point)    | 401    | `invalid_token` / `user_inactive` / `store_inactive` / `admin_inactive` |

Seed test data: run `SeedTestAdminUsersTest` → SQL in `target/seed/test-admin-users.sql`.

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