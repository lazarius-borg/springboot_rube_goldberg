# Tasks: Prevent Past-Time Temporal Requests (with Manager Back-Filling)

**Feature**: `013-prevent-past-time-requests`  
**Specification**: [spec.md](./spec.md)  
**Implementation Plan**: [plan.md](./plan.md)  
**Date**: 2026-09-16  

---

## Phase 1: Setup (Clock Infrastructure)

**Purpose**: Provide deterministic, injectable time abstraction across all three microservices.

- [X] T001 [P] Create `TimeConfig` with an injectable `java.time.Clock` bean (defaulting to `Clock.systemUTC()`) in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/TimeConfig.java`
- [X] T002 [P] Create `TimeConfig` with an injectable `java.time.Clock` bean (defaulting to `Clock.systemUTC()`) in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/TimeConfig.java`
- [X] T003 [P] Create `TimeConfig` with an injectable `java.time.Clock` bean (defaulting to `Clock.systemUTC()`) in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/TimeConfig.java`

---

## Phase 2: Foundational (Error Mapping & Timezone Resolution)

**Purpose**: Core exception handling and timezone projection utilities required before story implementation.

- [X] T004 [P] Implement `RestaurantTimezoneResolver` utility component in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/service/RestaurantTimezoneResolver.java` to resolve IANA `ZoneId` from `RestaurantViewEntity` (falling back to `Europe/Amsterdam` if missing, blank, or invalid)
- [X] T005 [P] Update `ValidationExceptionHandler.java` in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/api/ValidationExceptionHandler.java` to ensure temporal validation errors thrown as `IllegalArgumentException` return HTTP 400 Bad Request with RFC 7807 `ProblemDetail` and `invalidParams` property instead of being converted to HTTP 404

**Checkpoint**: Foundational time abstraction, error handling, and timezone resolution ready.

---

## Phase 3: User Story 1 - Prevent Customers from Checking Availability for Elapsed Times (Priority: P1) 🎯 MVP

**Goal**: Reject availability queries from customers/unauthenticated callers for past dates or past same-day time slots (with 5-minute clock-skew grace window), while allowing `ROLE_RESTAURANT_MANAGER` and `ROLE_ADMIN` to query past availability for back-filling.

**Independent Test**: Execute `AvailabilityValidationTest` slice tests verifying that customer/unauthenticated queries for past dates or past times today return HTTP 400 Bad Request, future queries return HTTP 200 OK, and manager queries for past dates return HTTP 200 OK.

### Tests for User Story 1
- [X] T006 [P] [US1] Create automated MockMvc web slice tests in `services/availability-service/src/test/java/nl/invokedynamic/demo/availability/api/AvailabilityValidationTest.java` asserting HTTP 400 Bad Request with `invalidParams` for past date/time queries under customer role and unauthenticated access, HTTP 200 OK for valid future queries, and HTTP 200 OK for past queries under `@WithMockUser(roles = "RESTAURANT_MANAGER")` and `ROLE_ADMIN`

### Implementation for User Story 1
- [X] T007 [US1] Implement role-aware temporal validation in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/service/AvailabilityService.java`: inject `Clock` and `RestaurantTimezoneResolver`; check caller authorities in `SecurityContextHolder`; if caller holds `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`, bypass past-time check; otherwise, evaluate `(date, time)` against `clock.instant().atZone(zoneId).minusMinutes(5)` and throw `IllegalArgumentException` when in the past or exceeding 365 days
- [X] T008 [US1] Update `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/api/AvailabilityController.java` to handle temporal validation exceptions and return HTTP 400 ProblemDetail with `invalidParams: [{"name": "time", "reason": "Dining time cannot be in the past"}]`

**Checkpoint**: User Story 1 is fully functional and independently testable. Customers cannot query past availability, while managers can back-fill.

---

## Phase 4: User Story 2 - Prevent Customers from Booking Reservations in the Past (Priority: P1)

**Goal**: Reject reservation creation requests from customers/unauthenticated callers for start timestamps in the past (beyond 300s grace window), while allowing `ROLE_RESTAURANT_MANAGER` and `ROLE_ADMIN` to book past reservations for back-filling.

**Independent Test**: Execute `ReservationValidationTest` asserting that customer past bookings return HTTP 400 Bad Request with `startTime` in `invalidParams`, bookings beyond 365 days return HTTP 400, future bookings return HTTP 201 Created, and manager past bookings return HTTP 201 Created.

### Tests for User Story 2
- [X] T009 [P] [US2] Update and expand automated tests in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/api/ReservationValidationTest.java` with a fixed test `Clock`, asserting HTTP 400 for customer past bookings (`startTime < now - 300s`), HTTP 400 for unauthenticated past bookings, HTTP 201 for future bookings, and HTTP 201 Created for past bookings under `@WithMockUser(roles = "RESTAURANT_MANAGER")`

### Implementation for User Story 2
- [X] T010 [US2] Refactor `createReservation` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java`: inject `Clock` bean; evaluate `boolean isManagerOrAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RESTAURANT_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"))`; if `!isManagerOrAdmin`, enforce `startTime >= clock.instant().minusSeconds(300)` and throw `IllegalArgumentException("Reservation start time cannot be in the past")`; enforce `startTime <= clock.instant().plus(Duration.ofDays(365))` across all roles

**Checkpoint**: User Story 2 is fully functional. Customer past bookings are blocked; restaurant manager back-filling is enabled.

---

## Phase 5: User Story 3 - Prevent Joining Waiting Lists for Elapsed Time Windows (Priority: P1)

**Goal**: Universally reject waiting list requests for past calendar dates or expired same-day seating windows across all caller roles, clamping `earliestTime` to current time when within the 5-minute clock-skew grace window.

**Independent Test**: Execute `WaitingListValidationTest` asserting that past target dates and same-day requests where `latestTime < now` or `earliestTime < now - 5m` return HTTP 400 Bad Request, while same-day requests with `earliestTime` within the grace window clamp to `now` and succeed (HTTP 201).

### Tests for User Story 3
- [X] T011 [P] [US3] Create automated MockMvc web slice and service tests in `services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/api/WaitingListValidationTest.java` using a fixed test `Clock`, asserting HTTP 400 for past `targetDate`, HTTP 400 for elapsed same-day windows, HTTP 201 for valid future dates, and HTTP 201 with clamped `earliestTime` when within the 5-minute grace window

### Implementation for User Story 3
- [X] T012 [US3] Update `JoinWaitingListRequest` in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/api/WaitingListController.java`: replace the obsolete hardcoded `2026-09-01` baseline in `isTargetDate()` with dynamic validation against the current date in the restaurant's operational timezone (default `Europe/Amsterdam`), ensuring `!targetDate.isBefore(today)` and `!targetDate.isAfter(today.plusDays(365))`
- [X] T013 [US3] Implement same-day window validation and grace clamping in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/service/WaitingListService.java`: inject `Clock`; resolve operational timezone; if `targetDate.isEqual(today)`, verify `!latestTime.isBefore(now.toLocalTime())` (throw `IllegalArgumentException("Seating time window has already passed")` if expired), verify `!earliestTime.isBefore(now.toLocalTime().minusMinutes(5))` (throw `IllegalArgumentException("Earliest seating time cannot be in the past")`), and if `earliestTime.isBefore(now.toLocalTime())`, clamp effective `earliestTime = now.toLocalTime()` before queue insertion

**Checkpoint**: User Story 3 is fully functional. Waiting list past entries are universally prohibited with graceful same-day clamping.

---

## Phase 6: Polish & Cross-Cutting Verification

**Purpose**: End-to-end reactor build validation, regression testing, and verification against quickstart scenarios.

- [X] T014 [P] Run full Maven reactor test verification via `./mvnw clean test` across all microservices to guarantee zero regressions
- [X] T015 Run quickstart validation scenarios from `specs/013-prevent-past-time-requests/quickstart.md` verifying customer 400 rejections and manager 200/201 back-filling successes

---

## Dependencies & Execution Order

### Phase Dependencies
```
Phase 1: Setup (T001-T003)
      │
      ▼
Phase 2: Foundational (T004-T005)
      │
      ├───────────────────────────────┬───────────────────────────────┐
      ▼                               ▼                               ▼
Phase 3: User Story 1 (T006-T008)  Phase 4: User Story 2 (T009-T010)  Phase 5: User Story 3 (T011-T013)
      │                               │                               │
      └───────────────────────────────┴───────────────────────────────┘
                                      │
                                      ▼
                        Phase 6: Polish (T014-T015)
```

### User Story Dependencies
- **User Story 1 (P1)**: Depends on Phase 1 & 2 (`TimeConfig`, `RestaurantTimezoneResolver`, `ValidationExceptionHandler`). Independent of US2 and US3.
- **User Story 2 (P1)**: Depends on Phase 1 (`TimeConfig`). Independent of US1 and US3.
- **User Story 3 (P1)**: Depends on Phase 1 (`TimeConfig`). Independent of US1 and US2.
- Stories 1, 2, and 3 can be executed in parallel once Phase 1 & 2 are complete.

### Parallel Opportunities
- **Phase 1**: `T001`, `T002`, and `T003` are in distinct modules and can run in parallel.
- **Phase 2**: `T004` and `T005` touch separate files and can run in parallel.
- **User Stories**: Once Phase 1 & 2 complete, `Phase 3 (US1)`, `Phase 4 (US2)`, and `Phase 5 (US3)` touch distinct service submodules (`availability-service`, `reservation-service`, `waiting-list-service`) and can be implemented concurrently.
- **Test Tasks**: `T006`, `T009`, and `T011` can be authored in parallel before implementation tasks.

---

## Implementation Strategy (MVP & Incremental Delivery)

1. **MVP First (User Story 1)**:
   - Complete `T001`–`T005` (Setup + Foundation).
   - Implement `T006`–`T008` (User Story 1).
   - Validate availability checks independently: customer past query returns 400; manager back-fill query returns 200.
2. **Incremental Delivery**:
   - Deliver User Story 2 (`T009`–`T010`): Reservation past-booking protection with manager back-filling.
   - Deliver User Story 3 (`T011`–`T013`): Waiting list queue protection with same-day clamping.
   - Run Polish & Reactor verification (`T014`–`T015`).
