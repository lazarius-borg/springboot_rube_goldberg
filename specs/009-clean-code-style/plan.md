# Implementation Plan: Code Style Refactoring - FQCN Elimination and Stream API Modernization

**Branch**: `009-clean-code-style` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/009-clean-code-style/spec.md`

## Summary

This plan outlines the code style refactoring to eliminate inline Fully Qualified Class Names (FQCNs) in class bodies across microservices and modernize imperative iteration with nested conditionals into declarative Java Stream APIs (`filter`, `map`, `anyMatch`, `findFirst`, `flatMap`). The refactoring improves code legibility, adheres to clean coding conventions, and ensures zero behavioral regression across the platform.

## Technical Context

**Language/Version**: Java 21 LTS / Java 26 (Eclipse Temurin)

**Primary Dependencies**:
- Spring Boot 3.3.3 / Spring Framework 6.x
- Jackson 2.17+ (`com.fasterxml.jackson.databind.JsonNode`)
- Spring Security 6.x (`KeycloakRealmRoleConverter`)
- Spring Data JPA (`saveAll`, repository queries)

**Testing**: JUnit 5, Spring MockMvc, AssertJ

**Target Platform**: Docker container / JVM on Linux/macOS

**Project Type**: Multi-module Maven reactor microservice platform

**Performance Goals**: Zero performance degradation; stream short-circuiting matches or exceeds imperative loop performance

**Constraints**: Strict preservation of transactional and error-handling semantics; zero regressions across test suites

**Scope**:
- 6 services with `KeycloakRealmRoleConverter`
- 5 services with `ValidationExceptionHandler`
- Event listeners in `availability-service`, `notification-service`, `waiting-list-service`, `analytics-service`
- Service logic in `availability-service` and `waiting-list-service`
- Test suites in all 6 services with inline `java.util.List.of` / `java.util.Map.of`

## Constitution Check

*GATE: Passed prior to research and verified post-design.*

- [x] **Strict Specification Adherence**: Scope strictly limited to FQCN elimination and stream modernizations specified in `spec.md`.
- [x] **Maven Reactor Architecture**: Modular boundaries and dependencies remain untouched.
- [x] **Clean Idiomatic Java**: Declarative Stream pipelines and clean imports replace cluttered inline packages and nested loop constructs.
- [x] **Non-Regression Invariant**: REST contracts, Kafka messaging formats, and database entities remain 100% compatible.
- [x] **Automated Testing Quality Gate**: Entire test suite must pass with zero failures and zero skipped tests.

## Project Structure

### Documentation Layout

```text
specs/009-clean-code-style/
├── plan.md              # Implementation plan (this file)
├── research.md          # Phase 0: FQCN rules and Stream refactoring patterns
├── data-model.md        # Phase 1: Component transformation schema
├── quickstart.md        # Phase 1: Verification and execution guide
├── contracts/           # Phase 1: Invariant and style contracts
│   └── refactoring-contracts.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2: Actionable tasks (/speckit-tasks command)
```

### Source Code Impact Layout

```text
services/
├── availability-service/
│   └── src/main/java/nl/invokedynamic/demo/availability/
│       ├── service/AvailabilityService.java          # Stream-based table/combo availability checks
│       ├── events/AvailabilityEventListener.java     # JsonNode import + saveAll stream mapping
│       ├── api/ValidationExceptionHandler.java       # Stream flatMap error processing
│       └── config/KeycloakRealmRoleConverter.java    # Stream role extraction
│
├── waiting-list-service/
│   └── src/main/java/nl/invokedynamic/demo/waitinglist/
│       ├── service/WaitingListService.java           # Stream findFirst FIFO candidate matching
│       ├── events/ReservationCancelledListener.java  # JsonNode import
│       ├── api/ValidationExceptionHandler.java       # Stream flatMap error processing
│       └── config/KeycloakRealmRoleConverter.java    # Stream role extraction
│
├── notification-service/
│   └── src/main/java/nl/invokedynamic/demo/notification/
│       └── events/NotificationEventListener.java     # JsonNode import
│
├── analytics-service/
│   └── src/main/java/nl/invokedynamic/demo/analytics/
│       ├── events/AnalyticsEventListener.java        # JsonNode import
│       └── config/KeycloakRealmRoleConverter.java    # Stream role extraction
│
├── restaurant-service/
│   └── src/main/java/nl/invokedynamic/demo/restaurant/
│       ├── api/ValidationExceptionHandler.java       # Stream flatMap error processing
│       └── config/KeycloakRealmRoleConverter.java    # Stream role extraction
│
├── customer-service/
│   └── src/main/java/nl/invokedynamic/demo/customer/
│       ├── api/ValidationExceptionHandler.java       # Stream flatMap error processing
│       └── config/KeycloakRealmRoleConverter.java    # Stream role extraction
│
├── reservation-service/
│   └── src/main/java/nl/invokedynamic/demo/reservation/
│       ├── api/ValidationExceptionHandler.java       # Stream flatMap error processing
│       └── config/KeycloakRealmRoleConverter.java    # Stream role extraction
│
└── */src/test/java/**                                # List.of / Map.of import replacements
```

## Phases

### Phase 0: Outline & Research *(Completed)*
- Documented FQCN import rules and collision handling in [research.md](research.md).
- Analyzed and selected idiomatic stream replacements for target methods in [research.md](research.md).

### Phase 1: Design & Contracts *(Completed)*
- Defined component transformation mappings in [data-model.md](data-model.md).
- Authored component style and behavioral contracts in [contracts/refactoring-contracts.md](contracts/refactoring-contracts.md).
- Authored runnable test and verification guide in [quickstart.md](quickstart.md).

### Phase 2: Implementation Planning (Next Phase)
- Execute `/speckit-tasks` to generate ordered, dependency-aware tasks in `tasks.md`.
