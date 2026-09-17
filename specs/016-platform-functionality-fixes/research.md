# Research & Architecture Decisions: Platform Functionality & Usability Fixes

**Feature**: `016-platform-functionality-fixes`  
**Date**: 2026-09-17  
**Status**: Approved  

---

## 1. Table Zone Storage & Schema Extension

### Context
Managers configure tables with 'Table Number / Label', 'Seating Capacity', and 'Floor Zone / Location' (e.g. "Main Dining", "Patio", "Rooftop"). The manager UI sends `{"tableNumber":"T6","capacity":4,"zone":"Rooftop"}` to `POST /api/v1/restaurants/{id}/tables`, but `CreateTableRequest` in `restaurant-service` currently rejects or ignores `zone`, and `RestaurantTableEntity` does not persist it.

### Decision
- Add `zone` field (`VARCHAR(50)`, nullable, default `'Main Dining'`) to `restaurant_table` table via Flyway migration in `restaurant-service`.
- Update `RestaurantTableEntity` to include `private String zone = "Main Dining"`.
- Update `CreateTableRequest` and table response DTOs to accept and return `String zone`.
- Ensure table listing endpoints (`GET /api/v1/restaurants/{id}/tables`) return `zone`.

### Rationale
- Floor zones allow guests and staff to identify table location (indoor, outdoor, rooftop, bar).
- Non-breaking backward compatibility by defaulting to "Main Dining" when omitted.

### Alternatives Considered
- *Separate Zone Entity with Foreign Key*: Unnecessary relational overhead for a simple descriptive floor area attribute.
- *Store Zone in JSON metadata blob*: Reduces queryability and type safety in JPA.

---

## 2. Table Combination Request Signature & Capacity Resolution

### Context
When creating table combinations, manager UI sends `{"tableIds":[...],"combinedCapacity":8}` without a `name`. The endpoint `POST /api/v1/restaurants/{id}/table-combinations` expects `@Valid CreateCombinationRequest` requiring `@NotBlank String name` and `List<UUID> tableIds`, returning `400 Bad Request`.

### Decision
- Update `CreateCombinationRequest` in `restaurant-service` to:
  ```java
  public record CreateCombinationRequest(
      @Size(max = 100)
      String name,

      @NotEmpty @Size(min = 2, max = 10)
      List<UUID> tableIds,

      @Min(1) @Max(200)
      Integer combinedCapacity
  )
  ```
- If `name` is blank or null, auto-generate a descriptive label based on the selected table labels (e.g. `"Combo: T1 + T2"`).
- If `combinedCapacity` is null or zero, automatically calculate capacity as the sum of capacities of the constituent tables.
- Update manager UI `app.js` combination dialog to provide an auto-populated Name input field and pre-calculated editable capacity field.

### Rationale
- Eliminates `400 Bad Request` while allowing managers to either accept convenient defaults or provide custom combination names and capacity adjustments (e.g. lost seats due to table alignment).

### Alternatives Considered
- *Strict client-only validation*: If UI alone is updated, other API consumers omitting name or sending combinedCapacity still fail. A resilient backend contract with defaults prevents client-server coupling fragility.

---

## 3. Closed Days Operating Schedule Configuration

### Context
Some restaurants close on specific weekdays (e.g., Mondays) or specific dates. In `OpeningHoursEntity`, `isClosed` already exists, but the manager UI schedule editor lacks a "Closed" toggle and only accepts open/close time ranges.

### Decision
- Update Manager UI schedule editor to include a toggle switch/checkbox labeled "Closed" for each day of the week.
- When toggled "Closed", opening/closing time inputs are disabled, and the schedule payload sets `isClosed: true`.
- In `availability-service` and `restaurant-service`, verify operating schedule: if the requested dining day of week or specific calendar date has `isClosed == true`, the restaurant is marked unavailable with reason `"RESTAURANT_CLOSED"`.
- Existing confirmed reservations remain untouched if an operator changes schedule settings.

### Rationale
- Standard hospitality practice where weekly closing days and special holiday closures are managed cleanly without inventing artificial zero-duration hours (e.g., "00:00 - 00:00").

### Alternatives Considered
- *Deleting opening hours record for closed days*: Leads to ambiguity between "unconfigured hours" and "intentionally closed". Explicit `isClosed: true` is deterministic.

---

## 4. Table Modification and Safe Deletion Constraints

### Context
Managers need to edit table details (table number, capacity, zone) and remove tables from the floor plan. Deleting a table currently assigned to upcoming confirmed reservations risks data corruption or seating failures.

### Decision
- Add `PUT /api/v1/restaurants/{id}/tables/{tableId}` to update table label, capacity, and zone.
- Add `DELETE /api/v1/restaurants/{id}/tables/{tableId}` in `restaurant-service`.
- When deleting a table:
  - Check if any active/upcoming confirmed reservation allocates this table (via `reservation-service` or soft-delete status `status = "INACTIVE"`).
  - If active future reservations exist, return `409 Conflict` with a descriptive problem detail explaining that upcoming bookings are assigned to the table.
  - Automatically dissolve or deactivate any table combinations containing the deleted table.

### Rationale
- Preserves referential integrity for active reservations while allowing floor managers full flexibility to reorganize dining areas.

### Alternatives Considered
- *Cascading deletion of reservations*: Severe operational violation; confirmed guest reservations must never be silently purged when tables are deleted.

---

## 5. Reservation Overview Table Allocation & Cancellation Reasons

### Context
In manager UI reservation overview, the 'TABLES' column is always empty because `/api/v1/reservations?restaurantId=...` returns `Page<ReservationEntity>` which does not contain the allocated tables or map them to human-readable table labels. Cancelled reservations also do not display the cancellation reason.

### Decision
- Update `ReservationResponseDto` to include `List<String> tableLabels` or `List<UUID> allocatedTables` and `String cancellationReason`.
- Update `ReservationController.listReservations` to return page of enriched `ReservationResponseDto` records containing allocated table IDs/labels and cancellation reason.
- Update Manager UI `app.js` to render table labels (e.g. `"T1"`, `"T2, T3"`) in the TABLES column, and show cancellation reason badge/text when `status === 'CANCELLED'`.

### Rationale
- Empowers floor managers to immediately know which physical tables to set up and why reservations were cancelled.

### Alternatives Considered
- *Frontend fetching allocation details for each reservation individually*: Results in N+1 HTTP calls; slow and inefficient. Returning enriched DTOs in bulk is standard practice.

---

## 6. Total Timeslot Capacity Enforcement & Real Inventory Allocation

### Context
Currently, when a reservation is created via `ReservationController`, if `availableTables` is not passed in the request body, the controller creates a dummy `TableCandidate` with a random UUID and capacity equal to `partySize`. Consequently:
1. Every reservation is assigned a phantom table.
2. Concurrent reservations at the same time can exceed the restaurant's total physical capacity indefinitely as long as each reservation is <= max table capacity.

### Decision
- In `reservation-service`:
  - When booking a reservation, fetch the restaurant's actual active table inventory and configured combinations from `restaurant-service` (or cached view).
  - Verify total timeslot capacity: Sum of guests in confirmed/arrived reservations during overlapping timeslot + new `partySize` MUST NOT exceed total active table capacity of the restaurant.
  - Allocate actual physical tables using `TableAllocationEngine` against occupied table allocations.
  - Reject booking with `409 Conflict` if tables or total capacity are exceeded.

### Rationale
- Fixes the root architectural flaw that enabled overbooking beyond physical restaurant capacity.

### Alternatives Considered
- *Rely solely on client passing availableTables*: Untrusted client input allows malicious or outdated clients to bypass capacity limits. Server-side inventory lookup is non-negotiable.

---

## 7. Multi-Table Reservation Checkout for Large Parties

### Context
When a customer's party size exceeds single table capacity and no single combination fits, the customer previously saw a generic "fully booked" message and was offered a futile waiting list.

### Decision
- In `availability-service` and `reservation-service`:
  - Allow allocating multiple distinct tables whose aggregate capacity accommodates the party size under a single reservation if total inventory allows.
  - If party size exceeds the restaurant's total capacity, return clear error explaining that party size exceeds total restaurant capacity.
  - In customer UI, if party size exceeds single tables but multiple tables are available, present multi-table booking confirmation under a single checkout.
  - Do not prompt to join waiting list if the party size cannot physically be seated by the restaurant's total capacity.

### Rationale
- Solves user frustration and eliminates futile waiting list queues for impossible party sizes.

---

## 8. Configurable Dining Durations (`maxReservationDurationMinutes`) & Selection UI

### Context
Managers could not configure maximum dining duration, and customers could not choose or see reservation duration.

### Decision
- Add `maxReservationDurationMinutes` (Integer, default 180, min 15, max 480) to `restaurant` table and entity.
- Allow managers to edit `defaultReservationDurationMinutes` and `maxReservationDurationMinutes` in establishment settings.
- In customer portal booking UI:
  - Display default and maximum durations.
  - Provide a dropdown selector with 15-minute increments between 45 minutes and `maxReservationDurationMinutes` (e.g. 45, 60, 75, 90, 105, 120, ...), defaulting to `defaultReservationDurationMinutes`.
  - Pass selected `durationMinutes` to `POST /api/v1/reservations`.

### Rationale
- Accommodates fast lunches, standard dinners, and extended celebratory dining without scheduling conflicts.

---

## 9. Intelligent Waiting List Candidate Matching

### Context
When a reservation is cancelled, `WaitingListService` checked `entry.partySize <= cancelledPartySize`. If a 2-guest reservation was cancelled, a queued customer with party size 4 was never offered a table even if the restaurant had sufficient open tables.

### Decision
- In `WaitingListService.processCancellationOpening`:
  - Query current table inventory and active slot occupancy for the cancelled date and time.
  - Iterate through queued waiting entries in FIFO order.
  - For each candidate, evaluate whether available unallocated physical tables / combinations can seat `candidate.partySize`.
  - If yes, transition candidate to `OFFERED`, issue 15-minute offer event, and break.

### Rationale
- Decouples waiting list matching from the cancelled reservation's specific party size, offering tables whenever real physical capacity permits.

---

## 10. Customer Waiting List Party Size Presentation

### Context
In customer portal, waiting list cards did not display party size with a prominent badge like upcoming reservations cards.

### Decision
- Update `createWaitlistCard(entry)` in `ui/src/customer/app.js` (and synchronized gateway static file) to display a prominent guest count badge:
  ```html
  <span class="badge bg-light text-secondary border"><i class="bi bi-people-fill me-1"></i> ${entry.partySize} guests</span>
  ```
  matching upcoming reservation cards.

### Rationale
- Consistent, clear customer visual feedback.
