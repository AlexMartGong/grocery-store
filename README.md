# grocery-store

Multi-tenant POS (Point of Sale) backend for grocery stores. Spring Boot 4.0.5 on Java 21, PostgreSQL.

## Stack

- Java 21
- Spring Boot 4.0.5 (Web MVC, Data JPA, Security)
- PostgreSQL + HikariCP
- Lombok, Bean Validation
- JUnit 5 (Spring Boot test starters)
- Maven (wrapper: `./mvnw`)

## Architecture

Domain-Oriented Modular Architecture — code organized by feature (one aggregate per module), not by technical layer.

Base package: `com.amx.grocerystore`. Entry point: `GroceryStoreApplication`.

Each module contains `web`, `application`, `domain`, and `infrastructure` sub-packages.

### Modules

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

### FK Strategy

- **Same aggregate:** `@ManyToOne(fetch = LAZY)` with `@JoinColumn`.
- **Cross-module FKs:** plain `UUID` field — no JPA association. Resolve via repositories.

### Database

Schema source of truth: `src/main/resources/script.sql`. Includes multi-tenancy (`store_id`), triggers, views, and CHECK constraints. Hibernate runs in `validate` mode (`spring.jpa.hibernate.ddl-auto=validate`) — entity mappings must match the SQL exactly.

Triggers own ledger columns (e.g. `Customer.outstandingBalance`, stock movements). Do not duplicate trigger logic in Java.

## Auth

Two flows share the same JWT secret and filter, distinguished by claim shape:

| Actor | Endpoint | Claims | Principal |
|-|-|-|-|
| Store users | `POST /api/v1/auth/login` | `storeId`, `userId`, `role` (`OWNER`/`CASHIER`) | `UserPrincipal` → `TenantAuthenticationToken` |
| SaaS admins | `POST /api/v1/admin/login` | `adminId`, `role` (`SUPER_ADMIN`) | `AdminPrincipal` → `AdminAuthenticationToken` |

`JwtAuthenticationFilter` re-queries the DB on every authenticated request to revalidate user/store/admin state. Inactive accounts or stores → 401 via `JwtAuthenticationEntryPoint`.

## API

CRUD endpoints share the same shape: `POST` (201 + `Location`), `GET /{id}`, `GET` (paginated), `PUT /{id}`, `DELETE /{id}` (soft delete only — never physical removal).

| Resource | Base path | Role | Tenant scoped |
|-|-|-|-|
| Stores | `/api/v1/stores` | `SUPER_ADMIN` | No (global) |
| Users | `/api/v1/users` | `OWNER` | Yes |
| Products | `/api/v1/products` | `OWNER` (write), `OWNER`/`CASHIER` (read) | Yes |
| Customers | `/api/v1/customers` | `OWNER`/`CASHIER` | Yes |
| Inventory movements | `/api/v1/inventory/movements` | `OWNER`/`CASHIER` | Yes |

Extras:

- `GET /api/v1/products/search?barcode={barcode}` — barcode lookup, current store scope.
- `PATCH /api/v1/products/{id}/price` — partial price update (`OWNER`).
- `POST /api/v1/admin/stores/{storeId}/users` — bootstrap initial `OWNER` for a store (`SUPER_ADMIN`).
- Server-side `search` param on `stores`, `products`, `customers` list endpoints (case-insensitive `LIKE`).

### Error format

Single `@RestControllerAdvice`: `core.web.GlobalExceptionHandler`. Body: `ErrorResponse` record (`error`, `message`, `fields`).

| Exception | Status | Code |
|-|-|-|
| `BadCredentialsException` | 401 | `invalid_credentials` |
| `AuthenticationException` | 401 | `unauthenticated` |
| `AccessDeniedException` | 403 | `forbidden` |
| `MethodArgumentNotValidException` | 400 | `validation_failed` + `fields` |
| `NotFoundException` | 404 | factory code (e.g. `store_not_found`) |
| `DuplicateResourceException` | 409 | factory code (e.g. `username_taken`) |
| `InvalidResourceStateException` | 409 | factory code (e.g. `store_not_active`) |
| `DataIntegrityViolationException` | 409 | `conflict` |

## Configuration

DB connection via env vars (defaults to `localhost` dev values):

| Var | Default |
|-|-|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | (see `application.properties`) |
| `DB_USERNAME` | (see `application.properties`) |
| `DB_PASSWORD` | (see `application.properties`) |

## Build & Run

| Task | Command |
|-|-|
| Compile | `./mvnw compile` |
| Run all tests | `./mvnw test` |
| Single test class | `./mvnw test -Dtest=ClassName` |
| Single test method | `./mvnw test -Dtest=ClassName#methodName` |
| Package | `./mvnw package` |
| Run app | `./mvnw spring-boot:run` |

Seed admin users for tests: run `SeedTestAdminUsersTest` → SQL written to `target/seed/test-admin-users.sql`.

## Testing

- **Service tests:** Pure Mockito (`@ExtendWith(MockitoExtension.class)`), no Spring context.
- **Controller tests:** `@WebMvcTest` + `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`; mock filter/entry point with `@MockitoBean`.

## Further Reading

- `CLAUDE.md` — full implementation guidelines for AI agents.
- `AGENTS.md` — extended instructions for OpenCode/agent sessions.
- `src/main/resources/script.sql` — database schema source of truth.
