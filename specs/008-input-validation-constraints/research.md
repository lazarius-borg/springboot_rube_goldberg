# Research & Technical Decisions: Input Values Constraints and Validation

**Feature**: `008-input-validation-constraints` | **Date**: 2026-09-14

## 1. Input Validation Architecture & Annotations

### Decision
Utilize standard Jakarta Bean Validation (`jakarta.validation.*`) via `spring-boot-starter-validation` across all microservices, annotating controller request record fields (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Email`) combined with class-level `@Validated` for `@RequestParam` / `@PathVariable` constraints.

### Rationale
- **Declarative & Standard**: Standardizes validation on the boundary records, separating request syntax validation from core domain service logic.
- **Fail-Fast**: Automatically intercepts invalid requests before any database transaction, allocation engine calculation, or Kafka publishing occurs.
- **Zero Overhead**: Native Spring Boot 3 Bean Validation integrates directly with Jackson deserialization and Spring MVC argument resolvers with sub-millisecond execution time.

### Alternatives Considered
- *Manual imperative if/else checks inside controller methods*: Rejected because it leads to repetitive, boilerplate code across controllers, inconsistent error structures, and higher risk of missing boundary checks.
- *Domain-layer-only assertion*: Rejected because domain exceptions (e.g., in JPA entities) occur late in the transaction, often resulting in unformatted HTTP 500 Internal Server Errors rather than descriptive HTTP 400 Bad Request responses.

---

## 2. Reservation Status Modeling & Serialization

### Decision
Model reservation lifecycle status as a formal Java enum `nl.invokedynamic.demo.reservation.domain.ReservationStatus` with values:
`CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`.
Update `UpdateStatusRequest(ReservationStatus status)` in `ReservationController`.
In `ReservationEntity`, persist the status as String or `@Enumerated(EnumType.STRING)` (preserving backwards database compatibility with existing `VARCHAR(30)` column).
Maintain String representation on `ReservationStatusChangedEvent` in `event-contracts` (`previousStatus`, `newStatus`) for seamless Kafka event backwards compatibility.

### Rationale
- Eliminates raw string mutation of lifecycle states, preventing typographical errors (e.g. `"confirmed"`, `"Arrived"`).
- Spring MVC / Jackson automatically maps string payloads to enum values and rejects unrecognized status names with an informative message.
- Preserves external Kafka contract stability without requiring schema bumps or consumer recompilation.

### State Transition Validation Matrix
- `CONFIRMED` -> `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`
- `ARRIVED` -> `COMPLETED`
- `COMPLETED`, `NO_SHOW`, `CANCELLED` -> Terminal states (no further transitions permitted)

---

## 3. Authoritative Cancellation Window Enforcement & Database Migration

### Decision
1. **Flyway Migration (`V2__add_cancellation_window_hours.sql`)**: Add `cancellation_window_hours INT NOT NULL DEFAULT 2` to the `reservation` table in `reservation-service`.
2. **Entity Snapshot**: Add `@Column(name = "cancellation_window_hours", nullable = false) private int cancellationWindowHours = 2;` to `ReservationEntity`.
3. **Creation Snapshot**: In `CreateReservationRequest`, accept optional `cancellationWindowHours` (bounded 0–168 hours). If omitted or null, default to 2 hours (or restaurant's policy if supplied).
4. **Authoritative Cancellation Deadline**: In `cancelReservation`, calculate the deadline strictly as:
   `reservation.getStartTime().minus(Duration.ofHours(reservation.getCancellationWindowHours()))`.
   Deprecate/ignore any client-supplied `cancellationWindowHours` query parameter on `DELETE /api/v1/reservations/{id}`, logging a deprecation notice.

### Rationale
- **Prevents Fraud / Tampering**: Clients cannot bypass restaurant cancellation policies by passing `?cancellationWindowHours=0` on the cancel request.
- **Decoupled Architecture**: Does not require synchronous inter-service HTTP calls from `reservation-service` to `restaurant-service` at cancellation time.
- **Contract Immutability**: Guarantees that the policy agreed to when the booking was accepted remains in force even if restaurant settings change later.

---

## 4. Temporal Validation & Role-Based Grace Periods

### Decision
For reservation creation:
- Customers submitting `POST /api/v1/reservations`: `startTime` must be current or future (allowing a 5-minute clock-skew grace period: `Instant.now().minusSeconds(300)`) and at most 365 days in the future.
- Restaurant managers and admins (`RESTAURANT_MANAGER`, `ADMIN`): Permitted to submit historical `startTime` values (e.g., to record walk-in dining or backfill seatings).
- Checked via validation logic in `ReservationService` / `ReservationController` inspecting the authenticated principal's authorities.

For availability queries & waiting list:
- `GET /api/v1/availability`: `date` must be >= `LocalDate.now()` (within 365 days).
- `POST /api/v1/waiting-list`: `targetDate` must be >= `LocalDate.now()` (within 365 days), and `earliestTime` must be <= `latestTime`.

### Rationale
- Satisfies business need for managers to record unscheduled walk-in seatings while stopping customer abuse or invalid calendar bookings.
- Clock-skew tolerance prevents transient network latency or client-server clock drift from rejecting valid immediate bookings.

---

## 5. RFC 7807 ProblemDetail Validation Exception Handler

### Decision
Implement a shared/standardized `@RestControllerAdvice` in each service (e.g., `ValidationExceptionHandler` or `GlobalExceptionHandler`) that catches:
1. `MethodArgumentNotValidException` (thrown when `@Valid` fails on request bodies)
2. `HandlerMethodValidationException` / `ConstraintViolationException` (thrown when `@RequestParam` / `@PathVariable` constraints fail)
3. `HttpMessageNotReadableException` (thrown when request JSON is malformed or an invalid enum value is submitted)

The handler builds an RFC 7807 `ProblemDetail`:
- `status`: 400 Bad Request
- `title`: "Validation Failed"
- `detail`: "One or more request parameters failed validation"
- `type`: `URI.create("https://example.invalid/problems/validation-error")`
- `invalidParams`: `List<Map<String, String>>` where each item has `name` (field name) and `reason` (validation message).

### Rationale
- Adheres to RFC 7807 standards and modern Spring Boot 3 `ProblemDetail` architecture.
- Gives frontends and automated API clients exact field-level attribution to highlight form fields.
