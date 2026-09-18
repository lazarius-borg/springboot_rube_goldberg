# Implementation Plan: Table Combination Management & Capacity Optimization

**Branch**: `017-table-combinations` | **Date**: 2026-09-18 | **Spec**: [specs/017-table-combinations/spec.md](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/017-table-combinations/spec.md)

**Input**: Feature specification from `specs/017-table-combinations/spec.md`

---

## Summary

Enable restaurant managers to define, inspect, and delete logical table combinations within a single floor zone to accommodate larger parties. Prevent duplicate combinations through order-insensitive set comparison, enforce strict physical proximity constraints (same-zone validation), cap combination capacity to physical table sums with optional custom reduction, guarantee that combinations never inflate total restaurant capacity, coordinate timeslot mutual exclusion across individual tables and combinations, and provide a dedicated overview and creation UX in the manager portal.

---

## Technical Context

**Language/Version**: Java 26 (Maven compiler release target 26)  
**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA, Spring Web MVC, Spring Kafka, Bootstrap 5.3.3  
**Storage**: PostgreSQL with Hibernate array types (`uuid[]`), H2 for in-memory slice tests, Redis for availability cache  
**Testing**: JUnit 5, AssertJ, Mockito, Spring `@WebMvcTest`, `@DataJpaTest`  
**Target Platform**: Linux / Containerized microservices on JVM  
**Project Type**: Multi-module Maven microservices architecture  
**Performance Goals**: Combination validation and duplicate checks $< 20\text{ms}$; availability queries $< 50\text{ms}$; real-time manager portal updates  
**Constraints**: Zero double-booking between combinations and member tables; total restaurant capacity strictly invariant to combinations; immediate unpublishing on deletion without disrupting confirmed bookings  
**Scale/Scope**: Dozens of tables and combinations per restaurant; multi-zone dining layout; synchronized across restaurant, availability, and reservation services  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I: Strict Specification Adherence**: PASS. Every requirement (FR-001 through FR-011) and clarification rule (same-zone, duplicate detection, custom capacity cap, immediate unpublish) is accounted for in the domain and design artifacts.
- **Principle II: Maven Reactor & Microservices Architecture**: PASS. Boundaries are strictly preserved. Changes are isolated to `restaurant-service` (configuration & validation), `event-contracts` (domain event), `availability-service` (read projection & capacity check), `reservation-service` (allocation engine), and `gateway` (manager portal UI).
- **Principle III: Modern Spring Boot Feature Showcase**: PASS. Leverages Spring Data JPA, `@Transactional` boundaries, Kafka event messaging, records for immutable DTOs, and Actuator health/metrics.
- **Principle IV: Agentic AI-Aided Development & Traceability**: PASS. Design artifacts (`research.md`, `data-model.md`, `contracts/table-combinations-api.yaml`, `quickstart.md`) provide complete traceability from spec to tasks.
- **Principle V: Comprehensive Testing & Quality Gates**: PASS. Unit and WebMvc tests will validate validation logic, duplicate prevention, zone constraints, and deletion lifecycle.

---

## Project Structure

### Documentation (this feature)

```text
specs/017-table-combinations/
├── spec.md              # Feature specification with clarifications
├── plan.md              # This implementation plan
├── research.md          # Phase 0 technical decisions and rationale
├── data-model.md        # Phase 1 data entities, relationships, and invariants
├── quickstart.md        # Phase 1 verification and testing guide
├── contracts/
│   └── table-combinations-api.yaml # Phase 1 OpenAPI interface contract
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
common/event-contracts/
└── src/main/java/nl/invokedynamic/demo/events/
    └── TableConfigurationChangedEvent.java       # Event contract for table and combination changes

services/restaurant-service/
└── src/main/java/nl/invokedynamic/demo/restaurant/
    ├── api/
    │   ├── RestaurantController.java            # Endpoints: list, create, delete combinations
    │   └── dto/
    │       ├── CreateCombinationRequest.java    # Request payload
    │       └── TableCombinationResponse.java    # Response payload with zone and table details
    ├── domain/
    │   └── TableCombinationEntity.java          # JPA entity for combinations
    ├── repository/
    │   └── TableCombinationRepository.java      # JPA repository
    └── service/
        └── RestaurantService.java               # Validation (zone, duplicates, capacity), CRUD, pruning

services/availability-service/
└── src/main/java/nl/invokedynamic/demo/availability/
    ├── events/
    │   └── AvailabilityEventListener.java       # Handles TableConfigurationChangedEvent
    └── service/
        └── AvailabilityService.java             # Capacity invariant & combination availability check

services/reservation-service/
└── src/main/java/nl/invokedynamic/demo/reservation/
    └── domain/
        └── TableAllocationEngine.java           # Prioritizes single table, then combination, then multi

gateway/src/main/resources/static/ui/manager/
├── index.html                                   # Dedicated Table Combinations Overview table & modal
└── app.js                                       # Combination CRUD handlers, zone filtering, list render
```

**Structure Decision**: Multi-service reactor layout where table combinations are authored and validated in `restaurant-service`, published via Kafka domain events, projected in `availability-service`, allocated in `reservation-service`, and managed through the `gateway` web portal.

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations identified. Design adheres fully to the project constitution.*
