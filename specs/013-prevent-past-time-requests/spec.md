# Feature Specification: Prevent Past-Time Temporal Requests

**Feature Branch**: `013-prevent-past-time-requests`

**Created**: 2026-09-16

**Status**: Draft

**Input**: User description: "time constraints - The availability, reservation and waiting-list services accept requests in past, so a user can check availability in the past, make a reservation in the past, or join a waiting list in the past. This shouldn't be possible since it doesn't make sense. All requests where this is possible should be identified and should not allow past date-times. RESTAURANT_MANAGER should be able to check availability and make a reservation in the past to allow for back-filling. Everything else stays the same."

## Clarifications

### Session 2026-09-16
- Q: How should the system determine the local time zone when evaluating whether a requested dining date and time is in the past? → A: Look up the specific restaurant's configured IANA timezone in the database/cache for each request before validating the temporal boundary (with a fallback to Europe/Amsterdam if unresolved).
- Q: When joining a waiting list for today, what rule should determine if the seating time window is considered "in the past"? → A: Require both earliestTime and latestTime to be in the future, allowing earliestTime to be clamped to the current time if within the clock-skew grace period.
- Q: Should the 5-minute clock-skew grace period also apply to real-time table availability queries, or should availability checks strictly require the requested time to be >= the current time? → A: Apply the uniform 5-minute grace window to availability checks, allowing queries for time slots within 5 minutes of current time.
- Q: Should privileged roles be allowed to check availability and create reservations in the past? → A: Yes, callers possessing RESTAURANT_MANAGER or ADMIN roles are permitted to check availability in the past and create reservations in the past to enable operational back-filling. CUSTOMER and unauthenticated callers remain strictly prohibited from past availability checks and past reservations.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prevent Customers from Checking Availability for Elapsed Times (Priority: P1)

As a restaurant customer checking seating availability,
I want the system to reject any availability query scheduled for a past date or an already elapsed time slot on the current date,
So that customers only receive meaningful table availability for present or future dining opportunities.

**Why this priority**: Availability queries are the top of the customer dining funnel. Permitting customer queries in the past misleads guests and wastes computing resources. Meanwhile, restaurant managers require the ability to check past availability to verify historical table states for back-filling.

**Independent Test**: Submit availability check requests with past dates and past times today using a customer token and an unauthenticated client. Verify the requests are rejected with a 400 validation error. Then submit the same past queries using a restaurant manager token and verify availability is successfully returned.

**Acceptance Scenarios**:

1. **Given** an availability search by a customer or unauthenticated caller with a date prior to today in the restaurant's operational timezone, **When** the search is submitted, **Then** the request is rejected with a 400 validation error stating the date must not be in the past.
2. **Given** an availability search by a customer or unauthenticated caller for the current calendar date with a time slot earlier than the current local time minus the 5-minute clock-skew grace window, **When** the search is submitted, **Then** the request is rejected with a 400 validation error stating the dining time must not be in the past.
3. **Given** an availability search by a customer for the current calendar date with a time slot within the 5-minute clock-skew grace window or in the future, **When** the search is submitted, **Then** the search succeeds and returns accurate seating availability.
4. **Given** an availability search by an authenticated user with `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN` for a past date or past time slot, **When** the search is submitted, **Then** the past temporal check is bypassed to allow back-filling and availability is returned.

---

### User Story 2 - Prevent Customers from Booking Reservations in the Past (Priority: P1)

As a dining platform administrator,
I want the system to prevent customers and unauthenticated callers from booking reservations in the past, while permitting restaurant managers to back-fill past reservations,
So that customer bookings always represent genuine upcoming dining events while restaurant staff can record historical or walk-in reservations retroactively.

**Why this priority**: Allowing customers to book past reservations corrupts operational schedules and allows fraudulent bookings. However, restaurant managers legitimately need to back-fill reservations that were taken offline or as walk-ins during busy shifts.

**Independent Test**: Submit reservation creation requests specifying past start timestamps using customer credentials and manager credentials. Verify that customer past-time booking attempts are rejected with an explicit validation error, whereas restaurant managers can successfully back-fill reservations for past timestamps.

**Acceptance Scenarios**:

1. **Given** a reservation booking request by a customer or unauthenticated caller specifying a start timestamp that has already passed (beyond the 5-minute grace tolerance), **When** the reservation is submitted, **Then** the request is rejected with a validation error indicating that reservation start time cannot be in the past.
2. **Given** a reservation booking request by an authenticated user with `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN` specifying a past start timestamp, **When** the reservation is submitted with available tables, **Then** the reservation is successfully created and confirmed to support back-filling.
3. **Given** a reservation booking request by any user specifying a start timestamp scheduled within an acceptable immediate window (within clock-skew tolerance) or future date, **When** the reservation is submitted with available tables, **Then** the reservation is created and confirmed.
4. **Given** a reservation booking request by any user specifying a start timestamp beyond the maximum allowed advance booking window (> 365 days), **When** the reservation is submitted, **Then** the request is rejected with a validation error indicating the advance booking limit.

---

### User Story 3 - Prevent Joining Waiting Lists for Elapsed Time Windows (Priority: P1)

As a dining guest or restaurant host managing queues,
I want the system to reject waiting list requests for past dates or elapsed time windows on the current date across all users,
So that the waiting list queue strictly contains active guests awaiting future cancellation openings.

**Why this priority**: A waiting list entry for an elapsed time window cannot be fulfilled or converted into an active table reservation. Even for restaurant staff, back-filling a waiting list makes no operational sense because waiting lists exist solely to dispatch upcoming offers.

**Independent Test**: Submit waiting list join requests specifying past target dates or specifying today's date with a latest seating time that has already passed across all caller roles. Verify the requests are universally rejected with a descriptive validation error.

**Acceptance Scenarios**:

1. **Given** a waiting list join request with a target dining date prior to today, **When** the request is submitted by any user, **Then** the request is rejected with a validation error stating the target date must not be in the past.
2. **Given** a waiting list join request with a target dining date of today where either the earliest seating time (beyond the grace window) or latest acceptable seating time has already passed, **When** the request is submitted, **Then** the request is rejected with a validation error stating the seating time window has already passed.
3. **Given** a waiting list join request with a target dining date of today where the earliest seating time is within the clock-skew grace window and latest time is in the future, **When** the request is submitted, **Then** the earliest seating time is clamped to the current time and the request is accepted.
4. **Given** a waiting list join request with a valid future date and positive time window (earliest time <= latest time), **When** the request is submitted, **Then** the request is accepted and the customer is placed in the waiting list queue.

---

### Edge Cases

- **Role Differentiation in Availability Queries**: Availability queries can be unauthenticated or carry a bearer token. If the caller holds `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`, the past date-time rejection is bypassed. If unauthenticated or holding only `ROLE_CUSTOMER`, past date-time rejection is enforced.
- **Clock Skew and Network Transit Grace Period**: For customer requests submitted right at the transition of the current minute (within a 5-minute tolerance threshold), the request must not be penalized as "in the past".
- **Same-Day Boundary Transitions**: When querying or booking for today, if the requested time is minutes into the future vs. minutes in the past, the boundary must be evaluated against the current wall-clock time in the restaurant's operational timezone.
- **End-of-Day Midnight Roll-Over**: Requests made at 23:59 for a 00:30 seating must properly evaluate day roll-over without misclassifying tomorrow's early morning slot as today's past slot.
- **Combined Date and Time Coordinates**: Where endpoints separate date and time into distinct fields (e.g. `date` and `time` or `targetDate` and `latestTime`), validation must evaluate the combined date-time instant rather than validating the date field in isolation.
- **Non-Existent or Invalid Restaurant ID**: If an invalid or non-existent restaurant UUID is supplied, the system MUST return HTTP 404 Resource Not Found with RFC 7807 ProblemDetail before performing temporal validation.
- **Daylight Saving Time (DST) Transitions**: Evaluations use the restaurant's canonical IANA timezone (e.g., `Europe/Amsterdam`), ensuring daylight saving time shifts (e.g. spring forward / fall back) are automatically and chronologically handled without invalidating valid bookings.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST reject table availability queries from customers or unauthenticated callers where the specified dining date is prior to the current calendar date in the restaurant's operational zone.
- **FR-002**: The system MUST reject table availability queries from customers or unauthenticated callers where the specified dining date is today and the specified dining time is earlier than the current time minus the uniform 5-minute clock-skew grace window.
- **FR-003**: The system MUST permit callers holding `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN` to query table availability and create reservations in the past to enable operational back-filling of historical dining events.
- **FR-004**: The system MUST reject reservation creation requests from customers or unauthenticated callers where the reservation start timestamp precedes the current timestamp minus the allowed clock-skew grace window (300 seconds).
- **FR-005**: The system MUST reject waiting list requests where the target dining date precedes the current calendar date across all caller roles.
- **FR-006**: The system MUST reject waiting list requests where the target dining date is today and either the earliest or latest acceptable seating time has already passed (outside the allowed clock-skew grace window), clamping the effective earliest seating time to the current time when within the grace window, across all caller roles.
- **FR-007**: The system MUST return standardized, descriptive error responses (HTTP 400 Bad Request) detailing the specific temporal parameter that failed validation and the reason for rejection.
- **FR-008**: The system MUST preserve existing advance booking limits, ensuring requests cannot exceed the maximum permitted advance booking horizon (365 days).
- **FR-009**: The system MUST evaluate temporal constraints against the specific restaurant's configured IANA timezone looked up from the database or cache (falling back to Europe/Amsterdam if not resolved).
- **FR-010**: The system MUST reject requests with missing or null mandatory date-time parameters (`date` and `time` for availability; `startTime` for reservations; `targetDate`, `earliestTime`, `latestTime` for waiting list) with HTTP 400 Bad Request and descriptive field-level error messages in `invalidParams`.
- **FR-011**: If an unknown or non-existent restaurant ID is supplied in a temporal query, the system MUST return HTTP 404 Resource Not Found with RFC 7807 ProblemDetail.
- **FR-012**: The system MUST accurately account for Daylight Saving Time (DST) shifts using the resolved canonical IANA timezone when performing same-day comparisons.

### Key Entities *(include if feature involves data)*

- **Temporal Query**: Represents a dining inquiry or booking request containing temporal coordinates (a date, a time, or an ISO timestamp) submitted by a user.
- **Validation Failure Detail**: A structured error payload identifying the invalid field (e.g., `date`, `time`, `startTime`, `targetDate`), the rejected value, and a human-readable explanation indicating why past date-times are impermissible.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of customer and unauthenticated availability check requests with past temporal coordinates are rejected with descriptive validation errors before reaching downstream caching or storage layers.
- **SC-002**: 100% of customer and unauthenticated reservation creation requests with past start timestamps are rejected, while 100% of authorized restaurant manager back-filling requests for past slots are accepted and processed.
- **SC-003**: 100% of waiting list requests for elapsed dates or elapsed same-day seating windows are rejected across all caller roles.
- **SC-004**: Valid current and future requests submitted within normal operational timing (including within the 5-minute clock-skew grace window) experience zero false-positive rejections.
- **SC-005**: Error response payloads adhere to standard structured error problem formats with clear field-level validation messages.

## Assumptions

- **Clock-Skew Tolerance**: A grace window of 5 minutes (300 seconds) is permitted when comparing customer requested start times to the current time, accommodating network latency, client clock variations, and form completion delays.
- **Manager Back-Filling Scope**: Back-filling privileges apply to table availability checks and reservation creations for users with `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`. Waiting list joins remain strictly future-only because queuing for a past opening is not an actionable operation.
- **Restaurant Timezone Resolution**: Evaluations for local date and time fields are resolved against the specific restaurant's registered IANA timezone (with a fallback to `Europe/Amsterdam` if unresolved), ensuring accurate local dining schedules.
- **Read/Historical Endpoints Unaffected**: Endpoints intended for reading historical records (such as viewing past reservations or audit summaries) are read-only and explicitly excluded from future-time constraints.
- **Maximum Advance Horizon**: The maximum allowable booking and waiting list horizon remains 365 days into the future.
