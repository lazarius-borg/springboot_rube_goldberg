# Implementation Tasks: Input Values Constraints and Validation

**Feature**: `008-input-validation-constraints` | **Date**: 2026-09-14 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Phase 1: Setup (Shared Infrastructure & Exception Advice)

**Purpose**: Establish uniform RFC 7807 `ProblemDetail` validation exception handlers and ensure Jakarta Bean Validation dependencies across all microservices.

- [x] T001 Verify and update Maven reactor POMs for `spring-boot-starter-validation` across all services in `services/analytics-service/pom.xml` and `pom.xml`
- [x] T002 [P] Implement RFC 7807 `ValidationExceptionHandler` with structured `invalidParams` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ValidationExceptionHandler.java`
- [x] T003 [P] Implement RFC 7807 `ValidationExceptionHandler` in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/ValidationExceptionHandler.java`
- [x] T004 [P] Implement RFC 7807 `ValidationExceptionHandler` in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/api/ValidationExceptionHandler.java`
- [x] T005 [P] Implement RFC 7807 `ValidationExceptionHandler` in `services/customer-service/src/main/java/nl/invokedynamic/demo/customer/api/ValidationExceptionHandler.java`
- [x] T006 [P] Implement RFC 7807 `ValidationExceptionHandler` in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/api/ValidationExceptionHandler.java`

---

## Phase 2: Foundational (Domain Enums & Database Migration)

**Purpose**: Core model infrastructure and database schema updates required before implementing user stories.

**⚠️ CRITICAL**: Must complete before proceeding to User Story phases.

- [x] T007 [P] Create `ReservationStatus` enum (`CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`) in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/domain/ReservationStatus.java`
- [x] T008 Create Flyway migration script `V2__add_cancellation_window_hours.sql` adding `cancellation_window_hours` column in `services/reservation-service/src/main/resources/db/migration/V2__add_cancellation_window_hours.sql`
- [x] T009 Update `ReservationEntity` with `cancellationWindowHours` column and `ReservationStatus` enum mapping in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/domain/ReservationEntity.java`

**Checkpoint**: Foundation ready - User Story 1 implementation can now begin.

---

## Phase 3: User Story 1 - Type-Safe Lifecycle Status Transitions and Reservation Constraints (Priority: P1) 🎯 MVP

**Goal**: Enforce type-safe lifecycle status transitions via `ReservationStatus` enum and bound reservation party size (1–50), duration (15–480 min), and role-based `startTime` temporal constraints.

**Independent Test**: Send `PATCH /api/v1/reservations/{id}/status` with valid enum (succeeds) vs invalid string (rejected with allowed statuses), and submit `POST /api/v1/reservations` with party size 0 or duration 10,000 (rejected with 400 Bad Request `invalidParams`).

### Tests for User Story 1

- [x] T010 [P] [US1] Create unit and slice tests for reservation status update enum validation and creation boundaries in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/api/ReservationValidationTest.java`

### Implementation for User Story 1

- [x] T011 [US1] Update `UpdateStatusRequest` record to bind `ReservationStatus status` with `@NotNull` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java`
- [x] T012 [US1] Refactor state machine transition logic in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java` to validate against `ReservationStatus`
- [x] T013 [US1] Annotate `CreateReservationRequest` record with validation constraints (`@NotNull`, `@Size`, `@Min(1)`, `@Max(50)`, `@Min(15)`, `@Max(480)`, `@Email`) in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java`
- [x] T014 [US1] Implement role-based `startTime` validation (current/future with 5-min clock-skew grace period for customers; past allowed for managers/admins) in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java`
- [x] T015 [US1] Add `@Valid` annotation to `@RequestBody` on `createReservation` and `updateStatus` methods in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java`

**Checkpoint**: User Story 1 is fully functional, type-safe, and testable independently as the MVP.

---

## Phase 4: User Story 2 - Authoritative Operational Boundaries for Restaurant Configuration (Priority: P2)

**Goal**: Constrain restaurant configuration parameters (duration 15–480 min, lead time 0–10080 min, horizon 1–365 days, cancellation window 0–168 hours), table seating capacity (1–50), combinations (≥2 tables), and opening hours ordering.

**Independent Test**: Attempt to register a restaurant or add a table with negative, zero, or excessive values (e.g., horizon of 1,000,000 days or capacity of 0), verifying rejection with 400 Bad Request and descriptive `invalidParams`.

### Tests for User Story 2

- [x] T016 [P] [US2] Create slice tests for restaurant registration, table, combination, and opening hours validation in `services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/api/RestaurantValidationTest.java`

### Implementation for User Story 2

- [x] T017 [US2] Annotate `CreateRestaurantRequest` record with `@NotBlank`, `@Size`, bounds (`@Min`, `@Max`), and IANA timezone verification in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java`
- [x] T018 [US2] Annotate `CreateTableRequest` with `@NotBlank`, `@Size(max = 50)`, and capacity bounds (`@Min(1)`, `@Max(50)`) in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java`
- [x] T019 [US2] Annotate `CreateCombinationRequest` with `@NotBlank` and table IDs list bounds (`@Size(min = 2, max = 10)`) in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java`
- [x] T020 [US2] Annotate `OpeningHoursConfigDto` and `ScheduleItemDto` with `@NotEmpty`, dayOfWeek `@Min(1)`, `@Max(7)`, and add schedule time ordering validation (`closeTime` > `openTime`) in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java` and `RestaurantService.java`
- [x] T021 [US2] Add `@Valid` to all `@RequestBody` controller endpoints in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java`

**Checkpoint**: User Stories 1 and 2 work independently.

---

## Phase 5: User Story 3 - Authoritative Restaurant Cancellation Policy Enforcement (Priority: P2)

**Goal**: Enforce cancellation deadlines strictly against the restaurant's snapshotted cancellation window stored on the reservation, deprecating and ignoring client-supplied cancellation window parameters.

**Independent Test**: Cancel a reservation past the restaurant's snapshotted window and verify HTTP 409 Conflict rejection, even if the client passes `?cancellationWindowHours=0`.

### Tests for User Story 3

- [x] T022 [P] [US3] Create tests for authoritative cancellation deadline evaluation and parameter deprecation in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/service/ReservationCancellationPolicyTest.java`

### Implementation for User Story 3

- [x] T023 [US3] Update `ReservationService.createReservation` to accept and persist `cancellationWindowHours` (defaulting to 2 if omitted or <= 0) onto `ReservationEntity` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java`
- [x] T024 [US3] Refactor `ReservationService.cancelReservation` to calculate deadline strictly as `reservation.getStartTime().minus(Duration.ofHours(reservation.getCancellationWindowHours()))`, ignoring client-supplied parameter in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java`
- [x] T025 [US3] Update `cancelReservation` endpoint in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java` to deprecate `cancellationWindowHours` query parameter in OpenAPI annotations and log a deprecation warning

**Checkpoint**: User Stories 1, 2, and 3 work independently.

---

## Phase 6: User Story 4 - Waiting List, Availability Query, and Profile Constraints (Priority: P3)

**Goal**: Constrain waiting list entries (target date >= today, `earliestTime` <= `latestTime`, party size 1–50), customer profile updates (names 1–50 chars, valid phone), and table availability query parameters.

**Independent Test**: Query availability with negative party size, join waiting list with inverted time interval, or update profile with blank name, verifying 400 Bad Request responses.

### Tests for User Story 4

- [x] T026 [P] [US4] Create validation slice tests for waiting list, customer profile, and availability in `services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/api/WaitingListValidationTest.java`, `services/customer-service/src/test/java/nl/invokedynamic/demo/customer/api/CustomerValidationTest.java`, and `services/availability-service/src/test/java/nl/invokedynamic/demo/availability/api/AvailabilityValidationTest.java`

### Implementation for User Story 4

- [x] T027 [US4] Annotate `JoinWaitingListRequest` with `@NotNull`, `@NotBlank`, `@Email`, and party size `@Min(1)`, `@Max(50)` in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/api/WaitingListController.java`
- [x] T028 [US4] Implement temporal validation requiring `targetDate >= today` and `earliestTime <= latestTime` in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/service/WaitingListService.java`
- [x] T029 [US4] Annotate `UpdateCustomerRequest` with `@NotBlank`, `@Size(max = 50)`, and phone format pattern in `services/customer-service/src/main/java/nl/invokedynamic/demo/customer/api/CustomerController.java`
- [x] T030 [US4] Add `@Validated` and constrain query parameters (`@RequestParam @Min(1) @Max(50) int partySize`) in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/api/AvailabilityController.java`

**Checkpoint**: All user stories functional and bounded.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Update documentation, OpenAPI schemas, and verify reactor quality gates.

- [x] T031 [P] Update OpenAPI annotations and schema descriptions for all bounded DTOs across all 5 services
- [x] T032 [P] Update `README.md` API specification and curl examples to reflect bounded inputs and `invalidParams` error formats
- [x] T033 Execute full Maven reactor build and test verification (`./mvnw clean test`) across all 10 modules
- [x] T034 Execute end-to-end quickstart validation scenarios from `specs/008-input-validation-constraints/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User Story 1 (P1): Depends on Phase 2; can proceed independently
  - User Story 2 (P2): Depends on Phase 2; can proceed independently
  - User Story 3 (P2): Depends on Phase 2 & User Story 1 (shares `ReservationEntity` / `ReservationService`)
  - User Story 4 (P3): Depends on Phase 2; can proceed independently
- **Polish (Phase 7)**: Depends on completion of all desired user stories

### Parallel Opportunities

- Phase 1: `T002`, `T003`, `T004`, `T005`, `T006` (Validation exception handlers in distinct services) can run in parallel
- Phase 2: `T007` (`ReservationStatus` enum) can be created in parallel with migration preparation
- User Stories: User Story 2 (`restaurant-service`) and User Story 4 (`waiting-list`, `customer`, `availability`) can run in parallel with User Story 1 & 3 (`reservation-service`)
- Phase 7: `T031` and `T032` can run in parallel

---

## Implementation Strategy

### MVP Scope (User Story 1 Only)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Implement Phase 3 (User Story 1: Type-Safe Lifecycle Status Transitions and Reservation Constraints).
3. Validate User Story 1 with `ReservationValidationTest`.
4. Deploy/demonstrate MVP.

### Incremental Delivery
1. Foundation (Phase 1 & 2) → Baseline established.
2. User Story 1 (Phase 3) → Type-safe reservation status & creation constraints (MVP).
3. User Story 2 (Phase 4) → Restaurant operational boundary constraints.
4. User Story 3 (Phase 5) → Authoritative cancellation policy snapshotting.
5. User Story 4 (Phase 6) → Waiting list, customer, and availability constraints.
6. Polish (Phase 7) → Documentation, OpenAPI schemas, and full reactor test verification.
