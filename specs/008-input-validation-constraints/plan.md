# Implementation Plan: Input Values Constraints and Validation

**Branch**: `008-input-validation-constraints` | **Date**: 2026-09-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-input-validation-constraints/spec.md`

## Summary

This feature enforces robust input validation, boundary constraints, and type-safe modeling across the Spring Boot Rube Goldberg microservice suite. It replaces raw String modeling of reservation statuses with a formal `ReservationStatus` enum, introduces declarative Jakarta Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Email`) on all API request DTOs/records, establishes authoritative cancellation window snapshotting on `ReservationEntity` with a Flyway migration, and standardizes error responses using RFC 7807 `ProblemDetail` with an `invalidParams` field violation structure.

## Technical Context

**Language/Version**: Java 21 LTS (Oracle OpenJDK / Eclipse Temurin)

**Primary Dependencies**:
- Spring Boot 3.3.3 (`spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`)
- Hibernate Validator 8.0.x (Jakarta Bean Validation 3.0)
- Jackson 2.17.x (Enum serialization & deserialization)
- Flyway 10.x (PostgreSQL database migrations)

**Storage**: PostgreSQL 16 (`reservation-service`, `restaurant-service`, `waiting-list-service`, `customer-service`)

**Testing**: JUnit 5, Mockito, Spring MVC Test (`@WebMvcTest`, `MockMvc`), AssertJ

**Target Platform**: Docker container / JVM on Linux/macOS

**Project Type**: Multi-module Maven reactor microservice backend

**Performance Goals**: Sub-10ms validation execution overhead; instant fail-fast before database transactions

**Constraints**: Zero regression across existing reactor test suites; full backwards compatibility for valid API payloads; standard RFC 7807 `ProblemDetail` structure

**Scale/Scope**: 5 core microservices (`restaurant-service`, `reservation-service`, `waiting-list-service`, `customer-service`, `availability-service`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Strict Specification Adherence (Principle I)**: All constraints and bounds directly align with `spec.md` and user clarification resolutions.
- [x] **Maven Reactor & Microservices Architecture (Principle II)**: Services remain strictly decoupled; no synchronous cross-service calls are introduced during cancellations (policy is snapshotted onto `ReservationEntity`).
- [x] **Modern Spring Boot Feature Showcase (Principle III)**: Idiomatic Jakarta Bean Validation, `@RestControllerAdvice`, and Spring Boot 3 RFC 7807 `ProblemDetail` are utilized.
- [x] **Agentic AI-Aided Development & Traceability (Principle IV)**: Detailed spec, research, data model, contracts, and quickstart documentation created.
- [x] **Comprehensive Testing & Quality Gates (Principle V)**: Automated controller slice tests and validation assertions ensure full quality gate compliance.

## Project Structure

### Documentation (this feature)

```text
specs/008-input-validation-constraints/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── validation-contracts.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code Impact Layout

```text
services/
├── reservation-service/
│   ├── src/main/java/nl/invokedynamic/demo/reservation/
│   │   ├── api/
│   │   │   ├── ReservationController.java            # Request DTO validation, @Valid, enum binding
│   │   │   └── ValidationExceptionHandler.java       # RFC 7807 ProblemDetail handler with invalidParams
│   │   ├── domain/
│   │   │   ├── ReservationEntity.java                # Added cancellationWindowHours, status enum
│   │   │   └── ReservationStatus.java                # New enum: CONFIRMED, ARRIVED, COMPLETED, NO_SHOW, CANCELLED
│   │   └── service/
│   │       └── ReservationService.java               # Authoritative deadline calculation, transition rules
│   ├── src/main/resources/db/migration/
│   │   └── V2__add_cancellation_window_hours.sql     # Flyway migration
│   └── src/test/java/nl/invokedynamic/demo/reservation/
│       └── api/ReservationControllerWebMvcTest.java  # Validation failure & success slice tests
│
├── restaurant-service/
│   ├── src/main/java/nl/invokedynamic/demo/restaurant/
│   │   ├── api/
│   │   │   ├── RestaurantController.java             # Bounds on CreateRestaurantRequest, Table, Combos, Hours
│   │   │   └── ValidationExceptionHandler.java       # ProblemDetail advice
│   │   └── service/RestaurantService.java            # Boundary guards
│   └── src/test/java/nl/invokedynamic/demo/restaurant/
│       └── api/RestaurantControllerWebMvcTest.java   # Validation slice tests
│
├── waiting-list-service/
│   ├── src/main/java/nl/invokedynamic/demo/waitinglist/
│   │   ├── api/
│   │   │   ├── WaitingListController.java            # Bounded JoinWaitingListRequest
│   │   │   └── ValidationExceptionHandler.java       # ProblemDetail advice
│   │   └── service/WaitingListService.java           # Date and time interval validation
│   └── src/test/java/nl/invokedynamic/demo/waitinglist/
│       └── api/WaitingListControllerWebMvcTest.java  # Validation slice tests
│
├── customer-service/
│   ├── src/main/java/nl/invokedynamic/demo/customer/
│   │   ├── api/
│   │   │   ├── CustomerController.java               # Bounded UpdateCustomerRequest
│   │   │   └── ValidationExceptionHandler.java       # ProblemDetail advice
│   │   └── service/CustomerService.java
│   └── src/test/java/nl/invokedynamic/demo/customer/
│       └── api/CustomerControllerWebMvcTest.java     # Validation slice tests
│
└── availability-service/
    ├── src/main/java/nl/invokedynamic/demo/availability/
    │   ├── api/
    │   │   ├── AvailabilityController.java           # Bounded @RequestParam validation
    │   │   └── ValidationExceptionHandler.java       # ProblemDetail advice
    │   └── service/AvailabilityService.java
    └── src/test/java/nl/invokedynamic/demo/availability/
        └── api/AvailabilityControllerWebMvcTest.java # Validation slice tests
```

**Structure Decision**: Multi-module Maven reactor structure adhering to Principle II and existing service conventions.

## Complexity Tracking

*No violations of project constitution. Architecture preserves service boundaries and enhances robustness without adding unnecessary infrastructure.*
