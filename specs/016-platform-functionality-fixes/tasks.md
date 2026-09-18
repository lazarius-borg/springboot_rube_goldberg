# Implementation Tasks: Platform Functionality & Usability Fixes

**Feature**: `016-platform-functionality-fixes`  
**Date**: 2026-09-17  
**Spec**: [`specs/016-platform-functionality-fixes/spec.md`](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/016-platform-functionality-fixes/spec.md)  
**Plan**: [`specs/016-platform-functionality-fixes/plan.md`](file:///Users/lazolazarev/projects/springboot_rube_goldberg/specs/016-platform-functionality-fixes/plan.md)  

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Database schema migrations and foundational configuration across services.

- [X] T001 [P] Create Flyway migration `V2__add_table_zone_and_max_duration.sql` adding `zone` column (`VARCHAR(50) DEFAULT 'Main Dining'`) to `restaurant_table` and `max_reservation_duration_minutes` (`INT DEFAULT 180`) to `restaurant` in `services/restaurant-service/src/main/resources/db/migration/V2__add_table_zone_and_max_duration.sql`
- [X] T002 [P] Create Flyway migration `V2__add_table_zone_and_max_duration_views.sql` adding `zone` to `table_inventory_view` and `max_reservation_duration_minutes` to `restaurant_view` in `services/availability-service/src/main/resources/db/migration/V2__add_table_zone_and_max_duration_views.sql`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain models, DTOs, and repository contracts required before user story implementations.

**⚠️ CRITICAL**: Must be completed before user story execution begins.

- [X] T003 [P] Update `RestaurantTableEntity` with `zone` (nullable, max 50, default 'Main Dining') and `status` ('ACTIVE'/'INACTIVE') in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/domain/RestaurantTableEntity.java`
- [X] T004 [P] Update `RestaurantEntity` with `maxReservationDurationMinutes` (`@Min(15) @Max(480)`, default 180) in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/domain/RestaurantEntity.java`
- [X] T005 [P] Update `CreateTableRequest` to include optional `@Size(max = 50) String zone` in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java`
- [X] T006 [P] Update `CreateCombinationRequest` to accept optional `name` and optional `combinedCapacity` in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java`
- [X] T007 [P] Create `UpdateRestaurantSettingsRequest` record with full settings fields in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/dto/UpdateRestaurantSettingsRequest.java`
- [X] T008 [P] Update `TableInventoryViewEntity` and `RestaurantViewEntity` in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/domain/` to include `zone` and `maxReservationDurationMinutes`

**Checkpoint**: Foundation ready — user story implementations can proceed.

---

## Phase 3: User Story 1 - Table Management & Flexible Combinations (Priority: P1) 🎯 MVP

**Goal**: Enable managers to create tables with floor zones, edit and delete tables safely, and create combinations with auto-defaulted names and calculated capacities without 400 Bad Request errors.

**Independent Test**: Manager adds table with "Rooftop" zone, edits its capacity, creates a combination using 2 tables without typing a name (auto-deriving "Combo: T1 + T2"), and deletes an unbooked table; table deletion fails with 409 if active reservations are assigned.

### Tests for User Story 1

- [X] T009 [P] [US1] Unit test table zone persistence, combination auto-defaults, and safe deletion in `services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/service/RestaurantServiceUnitTest.java`
- [X] T010 [P] [US1] WebMvcTest `POST /tables` with zone, `PUT /tables/{id}`, `DELETE /tables/{id}`, and `POST /table-combinations` in `services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/api/RestaurantControllerWebMvcTest.java`

### Implementation for User Story 1

- [X] T011 [US1] Implement `updateTable` and `deleteTable` methods with active reservation safety checks in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/service/RestaurantService.java`
- [X] T012 [US1] Update `addTableCombination` in `RestaurantService.java` to auto-generate name from constituent table numbers and calculate capacity sum if omitted in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/service/RestaurantService.java`
- [X] T013 [US1] Expose `PUT /api/v1/restaurants/{id}/tables/{tableId}` and `DELETE /api/v1/restaurants/{id}/tables/{tableId}` in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/api/RestaurantController.java`
- [X] T014 [US1] Update manager UI table creation modal to send `zone` and display zone in table inventory in `ui/src/manager/app.js` and `ui/src/manager/index.html`
- [X] T015 [US1] Update manager UI table combination modal with auto-defaulted name and calculated editable capacity in `ui/src/manager/app.js`
- [X] T016 [US1] Add Edit Table and Delete Table modal dialogs and actions in `ui/src/manager/app.js` and `ui/src/manager/index.html`

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: User Story 2 - Capacity Constraints & Seating Guidance (Priority: P1)

**Goal**: Prevent timeslot overbooking beyond total restaurant physical seating capacity, allocate real physical tables/combinations, and provide multi-table booking guidance for large parties rather than futile waitlist invitations.

**Independent Test**: Booking reservations that collectively exceed total restaurant seating capacity in an overlapping timeslot is rejected with 409 Conflict. Searching for a party size exceeding single table capacity allows multi-table selection if total capacity allows, or explains capacity limits without prompting for waitlist.

### Tests for User Story 2

- [X] T017 [P] [US2] Unit test multi-table allocation and total restaurant capacity enforcement in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/domain/TableAllocationEngineTest.java`
- [X] T018 [P] [US2] WebMvcTest capacity limit validation and multi-table reservation creation in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/api/ReservationControllerWebMvcTest.java`
- [X] T019 [P] [US2] WebMvcTest availability response with capacity metadata in `services/availability-service/src/test/java/nl/invokedynamic/demo/availability/api/AvailabilityControllerWebMvcTest.java`

### Implementation for User Story 2

- [X] T020 [US2] Refactor `TableAllocationEngine.java` to support multi-table allocation for parties exceeding single table capacity in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/domain/TableAllocationEngine.java`
- [X] T021 [US2] Refactor `ReservationService.java` to enforce total restaurant physical capacity across overlapping reservations and allocate actual table inventory in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java`
- [X] T022 [US2] Update `AvailabilityService.java` to verify total capacity and return `totalRestaurantCapacity` and `reason` metadata in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/service/AvailabilityService.java`
- [X] T023 [US2] Update customer UI to offer multi-table booking under a single reservation checkout and explain unseatable capacity constraints without offering waitlist in `ui/src/customer/app.js`

**Checkpoint**: User Stories 1 and 2 fully functional. Overbooking prevented.

---

## Phase 5: User Story 3 - Configurable Dining Duration & Customer Selection (Priority: P2)

**Goal**: Allow managers to configure default and maximum dining durations, and enable customers to select their intended duration in 15-minute increments up to the restaurant's maximum limit.

**Independent Test**: Manager sets max duration to 150 minutes; customer sees default (90 min) and max (150 min) in the booking UI and can select any duration in 15-minute steps (45 to 150 min); the booked table is blocked for the full duration.

### Tests for User Story 3

- [X] T024 [P] [US3] Unit test duration validation and slot blocking in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/service/ReservationDurationTest.java`
- [X] T025 [P] [US3] WebMvcTest duration bounds checking on reservation creation in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/api/ReservationDurationWebMvcTest.java`

### Implementation for User Story 3

- [X] T026 [US3] Update `ReservationService.createReservation` to validate requested duration against restaurant's `maxReservationDurationMinutes` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java`
- [X] T027 [US3] Update establishment settings management in `restaurant-service` to configure `defaultReservationDurationMinutes` and `maxReservationDurationMinutes` in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/service/RestaurantService.java`
- [X] T028 [US3] Add 15-minute duration dropdown selector to customer booking interface with default and max duration labels in `ui/src/customer/app.js` and `ui/src/customer/index.html`
- [X] T029 [US3] Update manager UI establishment settings editor to configure default and max dining durations in `ui/src/manager/app.js` and `ui/src/manager/index.html`

**Checkpoint**: User Stories 1, 2, and 3 fully functional and testable.

---

## Phase 6: User Story 4 - Restaurant Operating Schedule & Closed Days (Priority: P2)

**Goal**: Allow managers to designate weekdays or specific dates as closed via a toggle in the schedule editor, blocking new reservations while preserving existing confirmed bookings.

**Independent Test**: Manager toggles Monday as "Closed"; availability queries for Monday return `isClosed: true` and booking is blocked; existing reservations on that Monday retain their status.

### Tests for User Story 4

- [X] T030 [P] [US4] WebMvcTest `PUT /opening-hours` with `isClosed: true` in `services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/api/OpeningHoursClosedWebMvcTest.java`
- [X] T031 [P] [US4] Integration test verifying availability rejection on closed days in `services/availability-service/src/test/java/nl/invokedynamic/demo/availability/service/ClosedDayAvailabilityTest.java`

### Implementation for User Story 4

- [X] T032 [US4] Enforce `isClosed` check in `AvailabilityService.checkAvailability` returning `reason: "RESTAURANT_CLOSED"` in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/service/AvailabilityService.java`
- [X] T033 [US4] Add validation guard in `ReservationService.createReservation` rejecting bookings on closed schedule days in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java`
- [X] T034 [US4] Add "Closed" toggle switch per weekday in manager operating schedule editor, disabling time inputs when closed in `ui/src/manager/app.js` and `ui/src/manager/index.html`

**Checkpoint**: Operating schedule closed days enforced across services and UI.

---

## Phase 7: User Story 5 - Reservation Overview Visibility & Table Allocations (Priority: P3)

**Goal**: Display assigned table numbers/labels in the manager reservation overview TABLES column, and display the recorded cancellation reason for cancelled reservations.

**Independent Test**: Manager opens reservation overview; table numbers (e.g. "T1", "T2") appear in the TABLES column; cancelled reservations show the cancellation reason badge.

### Tests for User Story 5

- [X] T035 [P] [US5] WebMvcTest `GET /reservations` returning enriched DTO with table labels and cancellation reason in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/api/ReservationListingEnrichedTest.java`

### Implementation for User Story 5

- [X] T036 [US5] Update `ReservationResponseDto` to include `List<String> allocatedTableLabels` and `String cancellationReason` in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java`
- [X] T037 [US5] Update `ReservationController.listReservations` to enrich page items with allocated table labels (using a batch map lookup of allocated table IDs to table numbers to prevent N+1 queries) and cancellation reason in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/api/ReservationController.java`
- [X] T038 [US5] Update manager UI `filterReservationsTable` to render table labels in TABLES column and display cancellation reason badge in `ui/src/manager/app.js`

**Checkpoint**: Manager reservation dashboard displays full table and cancellation details.

---

## Phase 8: User Story 6 - Customer Waiting List Experience & Intelligent Matching (Priority: P3)

**Goal**: Prominently display party size badges on customer waiting list cards, and evaluate waiting list candidates upon reservation cancellations based on live available table inventory in FIFO order.

**Independent Test**: Customer sees party size badge on their active waitlist card; when a reservation is cancelled, a waiting candidate whose party size can physically be seated receives an offer even if the cancelled reservation was smaller.

### Tests for User Story 6

- [X] T039 [P] [US6] Unit test FIFO candidate matching against available table inventory on cancellation in `services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/service/WaitingListFifoLiveMatchingTest.java`
- [X] T040 [P] [US6] WebMvcTest `GET /waiting-list` ensuring party size is serialized in `services/waiting-list-service/src/test/java/nl/invokedynamic/demo/waitinglist/api/WaitingListPartySizeWebMvcTest.java`

### Implementation for User Story 6

- [X] T041 [US6] Refactor `WaitingListService.processCancellationOpening` to evaluate queued entries against available table inventory rather than strictly `< cancelledPartySize` in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/service/WaitingListService.java`
- [X] T042 [US6] Update `createWaitlistCard` in customer UI to render a prominent guest count badge matching upcoming reservations in `ui/src/customer/app.js`

**Checkpoint**: Waiting list experience and matching logic fully upgraded.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Frontend asset synchronization, full build verification, and end-to-end quickstart validation.

- [X] T043 [P] Synchronize manager and customer static UI assets to `gateway/src/main/resources/static/ui/` in `ui/`
- [X] T044 Run full reactor build `mvn clean test` across all 10 modules verifying zero test failures in root directory
- [X] T045 Execute quickstart end-to-end validation scenarios per `specs/016-platform-functionality-fixes/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
- **User Stories (Phases 3–8)**: Depend on Foundational phase completion.
  - US1 (Table Management) and US2 (Capacity Enforcement) are P1 priorities.
  - US3 (Dining Duration) and US4 (Closed Days) are P2 priorities.
  - US5 (Reservation Visibility) and US6 (Waitlist Upgrades) are P3 priorities.
- **Polish (Phase 9)**: Depends on all user stories being implemented.

### Parallel Opportunities

- **Phase 1**: T001 and T002 can run in parallel.
- **Phase 2**: T003, T004, T005, T006, T007, T008 can all run in parallel across different entity/DTO files.
- **Phase 3 (US1)**: Tests T009 and T010 can run in parallel. UI tasks T014, T015, T016 can proceed once service contracts are in place.
- **Phase 4 (US2)**: Tests T017, T018, T019 can run in parallel.
- **Phase 5 (US3)**: Tests T024 and T025 can run in parallel.
- **Phase 6 (US4)**: Tests T030 and T031 can run in parallel.
- **Phase 8 (US6)**: Tests T039 and T040 can run in parallel.

---

## Implementation Strategy

### MVP Scope (Phases 1, 2, and 3: User Story 1)

1. Execute Phase 1 (Flyway migrations) and Phase 2 (Foundational entities & DTOs).
2. Implement User Story 1 (Table management, floor zones, combination auto-defaults, safe deletion).
3. Validate User Story 1 independently via unit tests and manager portal.

### Incremental Delivery Sequence

1. **Increment 1 (MVP)**: Table zones, combination auto-defaults, safe table editing/deletion (US1).
2. **Increment 2**: Timeslot capacity enforcement, multi-table booking, unseatable party guidance (US2).
3. **Increment 3**: Configurable dining duration (15-min dropdown) and operating schedule closed days (US3 & US4).
4. **Increment 4**: Manager reservation table labels / cancellation reasons, and live FIFO waitlist matching (US5 & US6).
5. **Increment 5**: Static asset synchronization, reactor test suite validation, and quickstart run (Phase 9).
