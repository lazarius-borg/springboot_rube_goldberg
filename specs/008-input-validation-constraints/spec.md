# Feature Specification: Input Values Constraints and Validation

**Feature Branch**: `008-input-validation-constraints`

**Created**: 2026-09-14

**Status**: Ready for Planning

**Input**: User description: "input values constrains and validation - Some input values are curentrly unbounded while they need to be constrained. For example, UpdateStatusRequest record in the ReservationController has a String as a type for the status field, while it has a finite number of states that can be better modeled with an enumeration. Similar, the CreateRestaurantRequest record allows the manager to choose for defaultReservationDurationMinutes but it is not bounded, so theoretically a manager can set it to the maximum positive integer value; these values should have a lower and upper bounds. Similar improvment points should be identified where variable values are allowed to be configured, but it doesn't make sense all the possible values for thattype to be considered as valid. Similarly, should the cancellationWindowHours be provided as an input parameter to the cancelReservation endpoint, or is it better to have it configured for the restaurant when the restaurant is created? As it is now, the client decided what is the cancelation window based on which the deadline is calculated. Analyze the endpoints and identify similar potential improvements. Any changes MUST NOT introduce regressions."

## Clarifications

### Session 2026-09-14

- Q: How should the reservation service obtain the restaurant's authoritative cancellation window when evaluating cancellation deadlines? → A: Option A - Snapshot `cancellationWindowHours` onto `ReservationEntity` at reservation creation time (defaulting to 2 hours if omitted), avoiding runtime cross-service network calls during cancellations.
- Q: How should the system validate the reservation `startTime` parameter during reservation creation? → A: Option C - Customer bookings must have `startTime` in the current/future (with up to 5-minute clock-skew grace period) and at most 365 days in advance; managers and administrators are permitted to submit past start times (e.g. for walk-in backfills or retroactively recorded seatings).
- Q: How should validation error details be structured within the HTTP 400 RFC 7807 ProblemDetail response across all services? → A: Option A - Attach an `invalidParams` property containing a list of `{name, reason}` objects to the RFC 7807 `ProblemDetail` for clear field-level reporting.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Type-Safe Lifecycle Status Transitions and Reservation Constraints (Priority: P1)

As a restaurant manager or automated system, I want reservation status updates and reservation creation parameters to be strictly typed and bounded to valid operational limits, so that invalid statuses and physically impossible booking values (negative, zero, or absurdly large party sizes and durations) are rejected immediately at the interface boundary.

**Why this priority**: Reservation lifecycle integrity and table allocation are core to the platform. Accepting arbitrary strings for lifecycle states or unbounded integers for dining duration compromises data consistency, downstream event consumers, and table scheduling.

**Independent Test**: Can be verified by sending status update requests with valid enum values (accepted) vs invalid strings (rejected with clear validation feedback), and creating reservations with out-of-bounds party size or duration (rejected with descriptive error).

**Acceptance Scenarios**:

1. **Given** an existing reservation in `CONFIRMED` state, **When** a manager updates status to a valid state enum such as `ARRIVED`, **Then** the system updates the reservation status and emits the corresponding status change event.
2. **Given** an existing reservation, **When** a client attempts to update status using an invalid status string or unrecognized state name, **Then** the request is rejected with a validation error indicating allowed states.
3. **Given** a reservation creation request, **When** party size is less than 1 or exceeds the maximum limit of 50 guests, **Then** the system rejects the booking request with a 400 Bad Request explaining the allowed range (1 to 50).
4. **Given** a reservation creation request, **When** duration is less than 15 minutes or exceeds the maximum upper bound of 480 minutes (8 hours), **Then** the system rejects the request with an input validation error.
5. **Given** a customer creating a reservation, **When** `startTime` is more than 5 minutes in the past or more than 365 days in the future, **Then** the system rejects the request with a temporal validation error; whereas when an authorized manager submits a walk-in seating with a past `startTime`, the reservation is accepted.
6. **Given** a reservation creation request, **When** customer email does not adhere to standard email format or customer name is blank, **Then** the system rejects the request with appropriate validation errors.

---

### User Story 2 - Authoritative Operational Boundaries for Restaurant Configuration (Priority: P2)

As a restaurant manager registering a restaurant or configuring physical layout, I want configuration parameters (reservation duration, advance booking minutes, booking horizon days, cancellation window, and table capacities) to be bounded within sensible business ranges, so that typographical errors or extreme values cannot destabilize booking calculations or system storage.

**Why this priority**: Restaurant-level settings govern all subsequent availability, booking horizons, and cancellation deadline computations. Unbounded values can cause integer overflow, extreme date math errors, or impossible scheduling.

**Independent Test**: Can be verified by attempting to register restaurants with zero, negative, or excessive values for durations and horizons (e.g., booking horizon of 1,000,000 days or table capacity of 0), ensuring the system rejects invalid parameters while accepting realistic values.

**Acceptance Scenarios**:

1. **Given** a restaurant registration request, **When** the manager provides values within valid ranges (duration 15–480 min, advance 0–10080 min [up to 7 days], horizon 1–365 days, cancellation window 0–168 hours), **Then** the restaurant is created successfully.
2. **Given** a restaurant registration request, **When** the manager submits duration, advance booking, or horizon days exceeding upper bounds (e.g., duration > 480 min or horizon > 365 days), **Then** the system rejects the registration with descriptive field validation messages.
3. **Given** a restaurant table creation request, **When** seating capacity is less than 1 or greater than 50 seats, **Then** the request is rejected with an invalid capacity error.
4. **Given** a table combination creation request, **When** fewer than 2 distinct table identifiers are provided, **Then** the system rejects the request as combinable tables require at least two tables.
5. **Given** an opening hours configuration request, **When** `dayOfWeek` is outside the range of 1 (Monday) through 7 (Sunday), or `closeTime` is before `openTime` on a non-closed day, **Then** the request is rejected with a validation error.

---

### User Story 3 - Authoritative Restaurant Cancellation Policy Enforcement (Priority: P2)

As a restaurant manager and dining customer, I want reservation cancellation deadlines to be evaluated strictly against the restaurant's authoritative cancellation policy rather than an unverified client-supplied window, so that customers cannot bypass deadlines by manipulating cancellation parameters while existing API contracts continue to function without regressions.

**Why this priority**: Permitting the client to dictate the cancellation window parameter on cancellation requests allows customers to bypass restaurant cancellation policies by passing zero or negative hours. Calculating deadlines strictly from the snapshotted cancellation window protects restaurant operations and prevents fraudulent late cancellations.

**Independent Test**: Can be verified by attempting to cancel a reservation within the restaurant's configured cancellation window, verifying that the restaurant's policy snapshotted on the reservation is strictly enforced and that any client-supplied query parameter on `cancelReservation` is deprecated/ignored.

**Acceptance Scenarios**:

1. **Given** a reservation created with the restaurant's snapshotted cancellation window (e.g., 2 hours), **When** a cancellation request is submitted after the cancellation deadline has passed, **Then** the system rejects the cancellation with a 409 Conflict stating that the cancellation deadline has passed according to the restaurant's policy.
2. **Given** a cancellation request submitted before the snapshotted cancellation deadline, **When** cancellation is processed, **Then** the reservation is cancelled and occupied tables are released.
3. **Given** a client cancellation request that includes an optional client-supplied cancellation window parameter, **When** processed, **Then** the system authoritatively enforces the snapshotted reservation policy and ignores any client-supplied window values to prevent policy tampering.

---

### User Story 4 - Waiting List, Availability Query, and Profile Constraints (Priority: P3)

As a dining guest or system operator, I want waiting list entries, availability checks, and profile updates to be bounded to valid temporal and quantitative ranges, so that queries with past dates, inverted time windows, or excessive text lengths are caught before querying backend storage or cache layers.

**Why this priority**: Prevents wasted computation, invalid cache keys, and nonsensical queue entries across the remaining microservices.

**Independent Test**: Can be verified by querying availability with past dates or negative party sizes, or joining a waiting list with `earliestTime` after `latestTime`.

**Acceptance Scenarios**:

1. **Given** an availability check request, **When** the dining date is in the past or party size is outside 1–50 guests, **Then** the system rejects the query with a validation error.
2. **Given** a waiting list join request, **When** `earliestTime` is later than `latestTime`, the target date is in the past, or party size is outside 1–50 guests, **Then** the request is rejected with a validation error.
3. **Given** a customer profile update request, **When** phone number does not conform to valid contact length/format (5–25 characters) or name fields are blank or exceed 50 characters, **Then** the request is rejected with field validation feedback.

---

### Edge Cases

- What happens when a request omits optional fields that have default values? The system MUST preserve sensible defaults within the bounded range without throwing validation errors.
- What happens when an existing database record contains legacy values outside new constraints? Reading existing records MUST NOT fail; validation MUST only constrain incoming write/update requests.
- What happens when a status update is requested for a state that is syntactically a valid enum member but represents an illegal transition (e.g., from `COMPLETED` to `CONFIRMED`)? The system MUST return an illegal state / bad request error explaining the invalid transition.
- How does the system handle string fields containing only whitespace? Such fields MUST be treated as blank and rejected where non-empty values are required.
- How does the system handle excessive string inputs designed to overflow storage (e.g., 10,000-character cancellation reason or customer name)? String fields MUST enforce maximum length constraints matching the underlying database schema.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST model reservation lifecycle statuses using a formal enumeration (`ReservationStatus`) containing all valid states (`CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`) and reject any unrecognized status values on status update requests.
- **FR-002**: The system MUST enforce state machine transition rules on reservation status updates, permitting only valid transitions (`CONFIRMED` -> `ARRIVED`, `COMPLETED`, `NO_SHOW`, or `CANCELLED`; `ARRIVED` -> `COMPLETED`).
- **FR-003**: The system MUST constrain reservation party size to a minimum of 1 guest and a maximum of 50 guests across all reservation, availability, and waiting list endpoints.
- **FR-004**: The system MUST constrain reservation duration to a minimum of 15 minutes and a maximum of 480 minutes (8 hours).
- **FR-005**: The system MUST validate email addresses on reservation and waiting list requests to ensure valid syntax and a maximum length of 255 characters.
- **FR-006**: The system MUST bound restaurant registration parameters:
  - `name`: non-blank string, maximum 150 characters
  - `address`: non-blank string, maximum 1000 characters
  - `timezone`: non-blank string, valid IANA timezone identifier (e.g., "America/New_York", "Europe/Amsterdam", maximum 50 characters)
  - `defaultReservationDurationMinutes`: 15 to 480 minutes (default 90)
  - `minBookingAdvanceMinutes`: 0 to 10,080 minutes / 7 days (default 30)
  - `maxBookingHorizonDays`: 1 to 365 days (default 60)
  - `cancellationWindowHours`: 0 to 168 hours / 7 days (default 2)
- **FR-007**: The system MUST bound table seating capacity on table creation to a minimum of 1 seat and a maximum of 50 seats.
- **FR-008**: The system MUST require table combination configurations to specify at least 2 distinct table identifiers.
- **FR-009**: The system MUST validate opening hours schedule items: `dayOfWeek` MUST be an integer between 1 (Monday) and 7 (Sunday), and for non-closed days, `closeTime` MUST be strictly after `openTime`.
- **FR-010**: The system MUST enforce that cancellation deadlines are calculated strictly against the restaurant's authoritative cancellation policy, snapshotted onto the `ReservationEntity` (`cancellationWindowHours`, default 2 hours) at reservation creation time; client-supplied cancellation window query parameters on the cancellation endpoint are deprecated/ignored to prevent clients from bypassing restaurant policy.
- **FR-011**: The system MUST validate waiting list entries: target date MUST NOT be in the past, party size MUST be between 1 and 50 guests, and `earliestTime` MUST be before or equal to `latestTime`.
- **FR-012**: The system MUST validate customer profile updates: first and last names MUST be non-blank with a maximum of 50 characters, and phone number MUST be between 5 and 25 characters.
- **FR-013**: The system MUST return standardized HTTP 400 Bad Request responses conforming to RFC 7807 `ProblemDetail` with an `invalidParams` property containing a list of `{name, reason}` objects when input validation fails.
- **FR-014**: Any introduced validation constraints MUST NOT break existing valid API contracts, automated integration tests, or walkthrough workflows.
- **FR-015**: The system MUST constrain reservation `startTime` for customer bookings to current/future timestamps (with up to 5 minutes clock-skew tolerance) and at most 365 days in advance; managers and administrators MAY submit past timestamps to record walk-in dining or historical seatings.

### Key Entities

- **Reservation**: Represents a dining booking. Constrained attributes: `partySize` (1–50), `durationMinutes` (15–480), `cancellationWindowHours` (0–168, default 2), `startTime` (current/future for customer, past allowed for manager), `status` (formal `ReservationStatus` enum), `customerEmail` (valid format, max 255 chars), `customerName` (non-blank, max 200 chars).
- **Restaurant**: Represents a dining venue. Constrained attributes: `name` (non-blank, max 150 chars), `address` (non-blank, max 1000 chars), `timezone` (valid IANA zone ID, max 50 chars), `defaultReservationDurationMinutes` (15–480), `minBookingAdvanceMinutes` (0–10080), `maxBookingHorizonDays` (1–365), `cancellationWindowHours` (0–168).
- **RestaurantTable**: Physical dining table. Constrained attributes: `tableNumber` (non-blank, max 50 chars), `capacity` (1–50).
- **TableCombination**: Virtual combination of tables. Constrained attributes: `name` (non-blank, max 100 chars), `tableIds` (minimum 2 distinct IDs).
- **WaitingListEntry**: Fair queue entry for cancellations. Constrained attributes: `targetDate` (current or future date), `earliestTime` <= `latestTime`, `partySize` (1–50), `customerEmail` (valid format).
- **CustomerProfile**: Registered dining customer. Constrained attributes: `firstName` (1–50 chars), `lastName` (1–50 chars), `phoneNumber` (5–25 chars).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of API endpoints receiving request bodies or query parameters reject invalid, unbounded, or malformed inputs with a 400 Bad Request before processing business logic.
- **SC-002**: 100% of invalid reservation status update requests are rejected at the controller boundary without triggering database queries or throwing unhandled internal exceptions.
- **SC-003**: 100% of existing test suites across all 10 reactor modules continue to pass cleanly without regression.
- **SC-004**: Input validation failure responses provide specific, actionable field-level violation descriptions in under 10 milliseconds of processing overhead.

## Assumptions

- Standard Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Min`, `@Max`, `@Size`, `@Email`) is supported via `spring-boot-starter-validation` across all services.
- Validation bounds chosen (e.g. party sizes 1–50, durations 15–480 minutes) cover realistic dining hospitality requirements without artificially restricting legitimate business use cases.
- Timezone validation will verify against `ZoneId.getAvailableZoneIds()` to ensure valid geographic timezone identifiers.
- Existing records in persisted databases are assumed valid and will not be retroactively invalidated unless modified via API.
