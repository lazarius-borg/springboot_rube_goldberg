# Implementation Plan: Prevent Past-Time Temporal Requests (with Manager Back-Filling)

**Branch**: `013-prevent-past-time-requests` | **Date**: 2026-09-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-prevent-past-time-requests/spec.md` with revised manager back-filling requirements.

## Summary

Enforce strict temporal validation across `availability-service`, `reservation-service`, and `waiting-list-service` to prevent past date/time inquiries and bookings, while providing an authorized back-filling exception for restaurant managers:
- In `availability-service`: Reject past availability queries from customers and unauthenticated callers against the restaurant's operational timezone (with a 5-minute clock-skew grace window), while permitting `ROLE_RESTAURANT_MANAGER` and `ROLE_ADMIN` to query past availability for back-filling.
- In `reservation-service`: Reject past reservation creation requests from customers and unauthenticated callers with 400 ProblemDetail, while permitting `ROLE_RESTAURANT_MANAGER` and `ROLE_ADMIN` to create past reservations for historical back-filling.
- In `waiting-list-service`: Universally reject past target dates and expired same-day seating windows across all caller roles, clamping `earliestTime` to current time when within the 5-minute grace window.
- Inject injectable `java.time.Clock` beans in each service for deterministic, repeatable boundary testing across all roles.

## Technical Context

**Language/Version**: Java 21 / 26 (Temurin 26-jre-alpine container baseline)  
**Primary Dependencies**: Spring Boot 3.4.3, Spring WebMVC, Spring Security, Jakarta Validation, Spring Data JPA  
**Storage**: PostgreSQL 17 (relational store), Redis 7 (availability cache)  
**Testing**: JUnit 5, MockMvc, AssertJ, Spring Boot Test (`@WebMvcTest`, `@SpringBootTest`, `@WithMockUser`)  
**Target Platform**: Docker Compose on Linux/macOS  
**Project Type**: Multi-module Maven reactor microservices  
**Performance Goals**: Sub-5ms validation response; rejection occurs immediately prior to cache lookups or DB queries  
**Constraints**: RFC 7807 ProblemDetail error responses with `invalidParams` property; 5-minute clock-skew grace period for non-managers; IANA timezone lookup with `Europe/Amsterdam` fallback; zero false positives for future queries; manager back-filling allowed on availability and reservations  
**Scale/Scope**: 3 microservices (`services/availability-service`, `services/reservation-service`, `services/waiting-list-service`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I: Strict Specification Adherence**: PASS. Conforms directly to `spec.md` including the revised manager back-filling capabilities and all clarified requirements.
- **Principle II: Maven Reactor & Microservices Architecture**: PASS. Implemented cleanly within existing submodule boundaries (`availability-service`, `reservation-service`, `waiting-list-service`) without cross-service leakage.
- **Principle III: Modern Spring Boot Feature Showcase**: PASS. Leverages Spring Security role evaluation (`SecurityContextHolder`), Spring Boot 3 RFC 7807 `ProblemDetail`, and injectable `Clock` beans.
- **Principle IV: Agentic AI-Aided Development & Traceability**: PASS. Complete traceability established across `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, and `tasks.md`.
- **Principle V: Comprehensive Testing & Quality Gates**: PASS. Full automated test coverage via `@WebMvcTest` slice tests with `@WithMockUser(roles = "CUSTOMER")` (rejection) and `@WithMockUser(roles = "RESTAURANT_MANAGER")` (back-filling acceptance).

## Project Structure

### Documentation (this feature)

```text
specs/013-prevent-past-time-requests/
├── plan.md              # Implementation plan (/speckit-plan command output)
├── research.md          # Phase 0 output: Research findings & technical decisions
├── data-model.md        # Phase 1 output: Request schemas, validation rules, ProblemDetail model
├── quickstart.md        # Phase 1 output: Automated test commands and curl verification guide
├── contracts/           # Phase 1 output: API contracts for temporal endpoints
│   └── temporal-validation-contract.md
├── checklists/
│   ├── requirements.md  # Spec quality checklist (16/16 passing)
│   └── temporal-validation.md # Custom review checklist (35 items)
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
services/availability-service/
├── src/main/java/nl/invokedynamic/demo/availability/
│   ├── api/
│   │   ├── AvailabilityController.java           # Role-aware past availability query handling
│   │   └── ValidationExceptionHandler.java       # RFC 7807 400 ProblemDetail mapping
│   ├── config/
│   │   └── TimeConfig.java                       # Clock bean provider
│   └── service/
│       └── AvailabilityService.java              # Restaurant timezone resolution & past check
└── src/test/java/nl/invokedynamic/demo/availability/
    └── api/
        └── AvailabilityValidationTest.java       # MockMvc slice tests for customer rejection & manager back-fill

services/reservation-service/
├── src/main/java/nl/invokedynamic/demo/reservation/
│   ├── api/
│   │   └── ReservationController.java            # Past-time validation for non-managers / back-fill for managers
│   └── config/
│       └── TimeConfig.java                       # Clock bean provider
└── src/test/java/nl/invokedynamic/demo/reservation/
    └── api/
        └── ReservationValidationTest.java        # Tests verifying customer rejection & manager back-fill acceptance

services/waiting-list-service/
├── src/main/java/nl/invokedynamic/demo/waitinglist/
│   ├── api/
│   │   └── WaitingListController.java            # Updated JoinWaitingListRequest temporal validation
│   ├── config/
│   │   └── TimeConfig.java                       # Clock bean provider
│   └── service/
│       └── WaitingListService.java               # Universal same-day clamping and timezone resolution
└── src/test/java/nl/invokedynamic/demo/waitinglist/
    └── api/
        └── WaitingListValidationTest.java        # Tests for past targetDate and same-day window checks
```

**Structure Decision**: Multi-module Maven layout applying targeted temporal validation and role checks directly in each respective service domain.

## Complexity Tracking

*No violations identified. Design adheres to standard Spring Boot 3 validation and architecture guidelines.*
