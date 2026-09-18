# Implementation Plan: Platform Functionality & Usability Fixes

**Branch**: `016-platform-functionality-fixes` | **Date**: 2026-09-17 | **Spec**: [`specs/016-platform-functionality-fixes/spec.md`](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/016-platform-functionality-fixes/spec.md)

**Input**: Feature specification from `specs/016-platform-functionality-fixes/spec.md`

## Summary

Remediate operational inconsistencies, contract mismatches, and scheduling defects across the restaurant reservation platform:
1. Support floor zones (`zone`) on restaurant tables in creation, update, and listing.
2. Resolve table combination creation contract mismatches (`name`, `tableIds`, `combinedCapacity`) with automatic naming and sum-based capacity defaults.
3. Support closed days in manager operating hours schedule and enforce closure in availability checks and booking validation.
4. Provide safe table editing and deletion in `restaurant-service` with active reservation collision protection.
5. Populate assigned table labels and cancellation reasons in manager reservation overview.
6. Enforce total restaurant physical capacity limits in overlapping timeslots and allocate real physical table inventory rather than fabricated single tables.
7. Support multi-table reservation selection and clear guidance for party sizes exceeding single table capacities.
8. Enable configurable default and maximum dining durations (`maxReservationDurationMinutes`) and provide customer duration selection via 15-minute dropdown.
9. Refactor waiting list cancellation matching to evaluate live table availability in FIFO order rather than strictly matching `< cancelledPartySize`.
10. Prominently display party size badges on customer waiting list cards matching upcoming reservations.

---

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.3.3  
**Primary Dependencies**: Spring Data JPA, Spring Security OAuth2 Resource Server, Spring Cloud Gateway, Apache Kafka, Spring Validation, Flyway  
**Storage**: PostgreSQL 16 (per-service databases), Redis 7 (availability cache)  
**Testing**: JUnit 5, Mockito, Spring `@WebMvcTest`, `@DataJpaTest`, Reactor test suite  
**Target Platform**: Docker Compose / Kubernetes containerized microservices  
**Project Type**: Multi-module Maven reactor microservices application with static web frontends  
**Performance Goals**: Table allocation and availability checks < 50ms p95; zero overbooking  
**Constraints**: Zero regression across reactor test suites; immutability of existing confirmed reservations when establishment settings change  
**Scale/Scope**: 4 microservices (`restaurant-service`, `reservation-service`, `availability-service`, `waiting-list-service`) and 2 frontend SPAs (`ui/src/manager/`, `ui/src/customer/`)  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I: Strict Specification Adherence**: All changes map 1:1 to spec requirements and the 12 user-reported problem areas. **PASS**
- **Principle II: Maven Reactor Architecture**: Changes contained cleanly in respective Maven submodules without cross-module dependency leaks. **PASS**
- **Principle III: Modern Spring Boot Showcase**: Modern record DTOs, Bean Validation, JPA migrations, and problem details. **PASS**
- **Principle IV: Agentic AI Traceability**: Full traceability with research, data-model, contracts, and quickstart artifacts. **PASS**
- **Principle V: Comprehensive Testing & Quality Gates**: Unit, slice, and integration tests covering each fix with zero reactor test regressions. **PASS**

---

## Project Structure

### Documentation (this feature)

```text
specs/016-platform-functionality-fixes/
├── plan.md              # Implementation plan (this file)
├── research.md          # Technical research, root cause analysis & decisions
├── data-model.md        # Entity attributes, database migrations & DTOs
├── quickstart.md        # Step-by-step verification guide
├── checklists/
│   └── requirements.md  # Quality verification checklist
├── contracts/
│   └── platform-fixes-api-contract.md # REST and Kafka event schemas
└── tasks.md             # Task breakdown (created by /speckit-tasks)
```

### Source Code Impacted

```text
services/restaurant-service/
├── src/main/java/nl/invokedynamic/demo/restaurant/
│   ├── api/
│   │   ├── RestaurantController.java          # Add zone, edit/delete table, combination defaults, settings update
│   │   └── dto/                               # DTO records for table update and combination creation
│   ├── domain/
│   │   ├── RestaurantEntity.java              # Add maxReservationDurationMinutes
│   │   └── RestaurantTableEntity.java         # Add zone column and status
│   └── service/
│       └── RestaurantService.java             # Table CRUD, combination validation, settings update
└── src/main/resources/db/migration/
    └── V4__add_table_zone_and_max_duration.sql

services/reservation-service/
├── src/main/java/nl/invokedynamic/demo/reservation/
│   ├── api/
│   │   ├── ReservationController.java          # Enriched reservation listing with table labels and reason
│   │   └── dto/ReservationResponseDto.java    # Include tableLabels and cancellationReason
│   ├── domain/
│   │   └── TableAllocationEngine.java         # Multi-table allocation and real capacity checking
│   └── service/
│       └── ReservationService.java            # Total capacity verification against real inventory

services/availability-service/
└── src/main/java/nl/invokedynamic/demo/availability/
    └── service/
        └── AvailabilityService.java           # Enforce closed days and total restaurant capacity limits

services/waiting-list-service/
└── src/main/java/nl/invokedynamic/demo/waitinglist/
    ├── events/
    │   └── ReservationCancelledListener.java  # Evaluate cancellation opening
    └── service/
        └── WaitingListService.java            # Match candidates against live table availability in FIFO order

ui/
├── src/manager/
│   ├── app.js                                 # Table zone, combination defaults, closed toggle, table labels
│   └── index.html                             # UI controls for closed days, edit/delete table
└── src/customer/
    ├── app.js                                 # Duration dropdown (15-min steps), waitlist party badge, multi-table flow
    └── index.html                             # Duration selector UI, waitlist card template

gateway/
└── src/main/resources/static/ui/              # Synchronized UI assets (manager & customer)
```

---

## Complexity Tracking

No violations. Standard Spring Boot JPA, Bean Validation, and DOM script updates within existing module boundaries.
