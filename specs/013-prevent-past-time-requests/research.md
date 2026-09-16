# Research: Prevent Past-Time Temporal Requests (with Manager Back-Filling)

**Feature**: `013-prevent-past-time-requests`
**Date**: 2026-09-16

---

## 1. Temporal Validation & Role-Based Back-Filling

### Context & Problem
Three distinct microservices handle temporal requests from users and external callers:
1. **`availability-service`** (`GET /api/v1/availability`): Accepts separate `LocalDate date` and `LocalTime time` query parameters via `@ModelAttribute AvailabilityQuery`.
2. **`reservation-service`** (`POST /api/v1/reservations`): Accepts an ISO-8601 UTC timestamp `Instant startTime` via `@RequestBody CreateReservationRequest`.
3. **`waiting-list-service`** (`POST /api/v1/waiting-list`): Accepts `LocalDate targetDate`, `LocalTime earliestTime`, and `LocalTime latestTime` via `@RequestBody JoinWaitingListRequest`.

Previously:
- `availability-service` had no temporal validation, allowing any caller to query arbitrary past dates and times.
- `reservation-service` only validated `startTime` for `ROLE_CUSTOMER`, while managers bypassed validation.
- `waiting-list-service` used a flawed `@AssertTrue isTargetDate()` method anchored against a historical baseline (`2026-09-01`), allowing past dates and ignoring whether same-day time windows had already elapsed.

### Decision
- **Role-Based Back-Filling Exception**:
  - `RESTAURANT_MANAGER` and `ADMIN` roles are permitted to:
    1. Query table availability in the past (`GET /api/v1/availability`).
    2. Create reservations in the past (`POST /api/v1/reservations`).
  - This supports vital operational scenarios: logging offline walk-ins, phone reservations received during network outages, and retroactive table assignments.
  - `ROLE_CUSTOMER` and unauthenticated callers remain strictly prohibited from querying availability or creating reservations in the past.
  - `waiting-list-service` does NOT permit back-filling for any role; queueing for past openings is nonsensical.
- **Unified Clock-Skew Grace Period**:
  - Adopt a uniform 5-minute (300-second) clock-skew grace window for non-manager callers on `availability-service` and `reservation-service`.
- **Restaurant Timezone Resolution**:
  - For endpoints accepting local `(date, time)` coordinates, evaluate "now" in the specific restaurant's configured IANA timezone (`RestaurantViewEntity` / local projection or lookup), falling back safely to `Europe/Amsterdam` if unresolved.
- **Waiting List Same-Day Clamping**:
  - For same-day waiting list requests across all roles:
    - Both `earliestTime` and `latestTime` must be in the future.
    - If `earliestTime` is within the 5-minute grace window, clamp the effective `earliestTime` to `now.toLocalTime()`.
    - If `earliestTime` is older than the 5-minute grace window or `latestTime` is in the past, reject with 400 Bad Request.

### Alternatives Evaluated
- *Universal prohibition across all roles without exception*: Rejected per user requirement; restaurant managers need back-filling capabilities to account for real-world restaurant operations (walk-ins, paper logs).
- *Permitting back-filling on waiting lists*: Rejected because a waiting list only dispatches upcoming cancellation offers; adding an entry for an elapsed time slot serves no purpose.

---

## 2. Standardized ProblemDetail Error Format

### Context
Spring 6 / Spring Boot 3 natively supports RFC 7807 Problem Details via `org.springframework.http.ProblemDetail`.

### Decision
When temporal validation fails for a customer or unauthenticated caller, return HTTP 400 Bad Request with:
```json
{
  "type": "https://example.invalid/problems/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Requested dining time cannot be in the past",
  "invalidParams": [
    {
      "name": "time",
      "reason": "Dining time cannot be in the past"
    }
  ]
}
```
In `availability-service`, update `AvailabilityController` exception handling so temporal validation errors thrown by the service layer return 400 Bad Request instead of getting caught and converted to 404 Not Found.

---

## 3. Testability and Clock Injection

### Decision
- Provide an injectable `java.time.Clock` bean (defaulting to `Clock.systemUTC()`) in each service's configuration.
- In automated unit and slice tests (`@WebMvcTest`, JUnit 5), inject a fixed `Clock` to verify:
  - Customer past requests rejected (400)
  - Customer future requests accepted (200/201)
  - Manager past requests accepted for back-filling (200/201)
  - Waiting list same-day clamping and past-time rejection across roles (400)
