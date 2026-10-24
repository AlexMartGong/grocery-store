# grocery-store

Multi-tenant POS (Point of Sale) backend for grocery stores. Spring Boot 4.1.2 on Java 21, PostgreSQL.

## Stack

- Java 21
- Spring Boot 4.1.2 (Web MVC, Data JPA, Security)
- PostgreSQL + HikariCP
- JJWT 0.13.0 (pinned — Spring Boot 4.x does not manage `io.jsonwebtoken`)
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
| `stores` | `Store`, `StoreStation` |
| `subscriptions` | `SubscriptionPlan`, `Subscription`, `SaasPayment` |
| `users` | `User` |
| `products` | `Product` |
| `customers` | `Customer`, `CustomerPayment` |
| `sales` | `Sale`, `SaleItem`, `SaleCancellation` |
| `inventory` | `InventoryMovement` |
| `cash` | `CashRegisterShift` |
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
| Sales | `/api/v1/sales` | `OWNER`/`CASHIER` | Yes |
| Inventory movements | `/api/v1/inventory/movements` | `OWNER`/`CASHIER` | Yes |
| Cash register shifts | `/api/v1/cash/shifts` | `OWNER`/`CASHIER` (open/close/current), `OWNER` (history) | Yes |

Extras:

- `GET /api/v1/products/search?barcode={barcode}` — barcode lookup, current store scope.
- `PATCH /api/v1/products/{id}/price` — partial price update (`OWNER`).
- `POST /api/v1/sales/{id}/cancel` — register a sale cancellation (`OWNER`/`CASHIER`); body `{reason, notes}`. DB trigger `fn_cancel_sale` flips status, returns stock, reverses credit. Idempotent (re-cancel → 409 `sale_already_canceled`).
- `POST /api/v1/customers/{id}/payments` — credit repayment; DB trigger decrements `customers.outstanding_balance`.
- `POST /api/v1/admin/stores/{storeId}/users` — bootstrap initial `OWNER` for a store (`SUPER_ADMIN`).
- `PUT /api/v1/agent/stations/{stationId}/url` — print-agent self-reports its LAN URL. Public route, auth via `X-Agent-Key` header (`AgentSecurityProperties` → `app.security.agent.api-key`). Unknown station → 404 `station_not_found`.
- `GET /api/v1/stores/me/stations` — PWA lists active stations for the current store (`OWNER`/`CASHIER`).
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

## Notifications & Async Events

Domain events (`<module>.domain.events.*Event` records) are published by services via `ApplicationEventPublisher` **after** persistence. `AdminNotificationEventListener` (in `notifications.application`) handles them with `@Async @EventListener`, executing on a `ThreadPoolTaskExecutor` (core=2, max=10, queue=50, prefix `async-`) configured in `core.config.AsyncConfig`. Listeners must never re-throw — both `EvolutionWhatsAppClient.sendMessage` and `TelegramBotClient.sendMessage` swallow gateway errors by design (best-effort delivery).

**Per-tenant, per-channel owner routing.** Every owner-facing event carries `UUID storeId` as its first component. `sendAdminAlert(storeId, text)` loads the `Store` and routes by `Store.preferredAdminChannel` (`AdminNotificationChannel`, default `WHATSAPP`): `WHATSAPP` → `Store.whatsapp`, `TELEGRAM` → `Store.telegramChatId`. There is **no** global owner-phone setting.

| Event | Published by | Routed to |
|-|-|-|
| `ShiftClosedEvent` | `CashShiftService.close` | store admin channel |
| `CustomerPaymentEvent` | `CustomerService.registerPayment` | customer phone (WhatsApp only) |
| `LowStockEvent` | `SaleService` (parsed from pending notifications) | store admin channel |
| `SaleCancelledEvent` | `SaleService.cancelSale` | store admin channel (anti-fraud) |
| `InventoryLossEvent` | `InventoryService.adjustStock` (when `reason=LOSS`) | store admin channel (shrinkage) |

WhatsApp delivery via Evolution API: `EvolutionWhatsAppClient` POSTs to `/message/sendText/{instance}`. Phone normalization: 10-digit input gets `52` prefix (Mexican local → E.164). Telegram delivery via Bot API: `TelegramBotClient` POSTs to `/bot{token}/sendMessage`; empty token or empty `chat_id` → no-op.

## Configuration

DB connection via env vars (defaults to `localhost` dev values):

| Var | Default |
|-|-|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | (see `application.properties`) |
| `DB_USERNAME` | (see `application.properties`) |
| `DB_PASSWORD` | (see `application.properties`) |

JWT (boot fails if missing/invalid):

| Var | Notes |
|-|-|
| `JWT_SECRET` | Base64-encoded; must decode to ≥32 bytes (HS256) |
| `JWT_EXPIRATION_MINUTES` | Optional; default `480` |

WhatsApp / Evolution API (owner alerts routed per-tenant via `Store.whatsapp` — no global phone env var):

| Var | Maps to |
|-|-|
| `EVOLUTION_BASE_URL` | `evolution.api.base-url` |
| `EVOLUTION_APIKEY` | `evolution.api.apikey` |
| `EVOLUTION_INSTANCE` | `evolution.api.instance` |

Telegram Bot API (owner alerts routed per-tenant via `Store.telegramChatId` when `Store.preferredAdminChannel = 'TELEGRAM'`):

| Var | Maps to | Notes |
|-|-|-|
| `TELEGRAM_BOT_TOKEN` | `telegram.bot.token` | Default empty → Telegram sends no-op |

Print-agent bridge:

| Var | Maps to | Notes |
|-|-|-|
| `AGENT_API_KEY` | `app.security.agent.api-key` | Default `default-agent-secret`; shared static key for `X-Agent-Key` header |

CORS (bound to `app.cors.*`): `CORS_ALLOWED_ORIGINS`, `CORS_ALLOWED_METHODS`, `CORS_ALLOWED_HEADERS`, `CORS_EXPOSED_HEADERS`, `CORS_ALLOW_CREDENTIALS`, `CORS_MAX_AGE`.

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
- **Env-dependent:** `GroceryStoreApplicationTests.contextLoads` (`@SpringBootTest`) opens a real DataSource — requires a running PostgreSQL matching `DB_*` env vars. Expected to fail locally without DB; not a regression.

## Further Reading

- `CLAUDE.md` — full implementation guidelines for AI agents.
- `AGENTS.md` — extended instructions for OpenCode/agent sessions.
- `src/main/resources/script.sql` — database schema source of truth.
