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
Follow a **Domain-Oriented (Modular) Architecture** — organize code by functional feature (e.g., `products`, `sales`)
rather than by technical layer.
**Internal module structure:** Each module contains `web`, `application`, `domain`, and `infrastructure` sub-packages.
**Dependency management:** Use constructor-based injection for all required dependencies.
**Data flow:** Use DTOs for the API layer; do not expose JPA entities directly.

## SOLID Principles

Strictly adhere to SOLID in every implementation:

- **Single Responsibility:** Each class must have one reason to change.
- **Open/Closed:** Use interfaces and composition over inheritance.
- **Liskov Substitution:** Subclasses must be replaceable by their base types.
- **Interface Segregation:** Create small, specific interfaces per client.
- **Dependency Inversion:** Depend on abstractions, not concrete implementations.

## Implementation Guidelines

- **Database Source of Truth:** Always read and align to `src/main/resources/script.sql` for the database schema, multi-tenancy rules (`store_id`), PostgreSQL triggers, views, and integrity constraints. Do not duplicate trigger logic in Java.
- **Modern Java:** Leverage Java 21 features — `records`, `sealed classes`, and `switch` pattern matching.
- **Boilerplate:** Use Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`).
- **Validation:** Apply Bean Validation on all DTOs.
- **Best Practices:** Always prioritize patterns defined in `.claude/skills/java-springboot/SKILL.md`.

## Expected Output Format

1. **Design Reasoning:** Brief technical justification of the modular design and SOLID compliance.
2. **Implementation:** Code blocks organized by the required package structure.
3. **Verification:** The specific `./mvnw test` command to verify the implementation.