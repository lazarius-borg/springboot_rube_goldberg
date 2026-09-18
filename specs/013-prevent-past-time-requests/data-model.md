# Data Model: Prevent Past-Time Temporal Requests (with Manager Back-Filling)

**Feature**: `013-prevent-past-time-requests`
**Date**: 2026-09-16

---

## 1. Request DTOs & Validation Schemas

### 1.1 `AvailabilityQuery` (`availability-service`)
Used for querying real-time table availability.

| Field | Type | Required | Validation Constraints |
|---|---|---|---|
| `restaurantId` | `UUID` | Yes | `@NotNull` |
| `date` | `LocalDate` | Yes | `@NotNull`, for non-managers combined with `time` must be >= `now - 5m` in restaurant timezone, and <= `now + 365d` |
| `time` | `LocalTime` | Yes | `@NotNull`, for non-managers combined with `date` must be >= `now - 5m` in restaurant timezone |
| `partySize` | `Integer` | No | `@Min(1) @Max(50)`, default `2` |

**Role-Based Temporal Rule**:
- Check security context:
  - If caller possesses `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`:
    - Skip past-time evaluation (permit back-filling availability queries).
  - If caller is unauthenticated or holds `ROLE_CUSTOMER`:
    - `ZonedDateTime target = ZonedDateTime.of(date, time, restaurantZoneId)`
    - `ZonedDateTime threshold = ZonedDateTime.now(clock.withZone(restaurantZoneId)).minusMinutes(5)`
    - Condition: `!target.isBefore(threshold)` and `!target.isAfter(threshold.plusDays(365))`
    - If violated: Throw `IllegalArgumentException` / return 400 ProblemDetail with `invalidParams`.

---

### 1.2 `CreateReservationRequest` (`reservation-service`)
Used for creating confirmed table reservations.

| Field | Type | Required | Validation Constraints |
|---|---|---|---|
| `restaurantId` | `UUID` | Yes | `@NotNull` |
| `customerId` | `UUID` | No | Optional UUID |
| `customerName` | `String` | No | Max 200 characters |
| `customerEmail` | `String` | No | Valid email syntax, max 255 characters |
| `partySize` | `Integer` | No | `@Min(1) @Max(50)`, default `2` |
| `startTime` | `Instant` | Yes | For non-managers, must be >= `now - 300s`. For all roles, must be <= `now + 365d` |
| `durationMinutes` | `Integer` | No | `@Min(15) @Max(480)`, default `90` |
| `cancellationWindowHours` | `Integer` | No | `@Min(0) @Max(168)`, default `2` |
| `availableTables` | `List<TableCandidate>` | No | Optional candidate tables |
| `combinations` | `List<CombinationCandidate>` | No | Optional candidate combinations |

**Role-Based Temporal Rule**:
- Check security context:
  - `boolean isManagerOrAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RESTAURANT_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));`
  - If `!isManagerOrAdmin`:
    - `Instant threshold = clock.instant().minusSeconds(300)`
    - `if (startTime.isBefore(threshold))` → Reject with 400 ProblemDetail: `"Reservation start time cannot be in the past"`
  - For all roles:
    - `if (startTime.isAfter(clock.instant().plus(Duration.ofDays(365))))` → Reject with 400 ProblemDetail: `"Reservation start time cannot be more than 365 days in advance"`

---

### 1.3 `JoinWaitingListRequest` (`waiting-list-service`)
Used for placing a customer on the FIFO waiting list for a given date and time window (universal across all roles; no back-filling).

| Field | Type | Required | Validation Constraints |
|---|---|---|---|
| `restaurantId` | `UUID` | Yes | `@NotNull` |
| `customerId` | `UUID` | No | Optional UUID |
| `customerEmail` | `String` | Yes | `@NotBlank @Email @Size(max = 255)` |
| `targetDate` | `LocalDate` | Yes | `@NotNull`, must be current or future calendar date in restaurant timezone |
| `earliestTime` | `LocalTime` | Yes | `@NotNull`, must be <= `latestTime` |
| `latestTime` | `LocalTime` | Yes | `@NotNull`, must be >= `earliestTime` |
| `partySize` | `int` | Yes | `@Min(1) @Max(50)` |

**Temporal Validation Rules (Universal)**:
- In restaurant timezone (`ZoneId`):
  - `targetDate` < `today` → Reject: `"Target date must not be in the past"`
  - `targetDate` > `today.plusDays(365)` → Reject: `"Target date cannot be more than 365 days in advance"`
  - If `targetDate == today`:
    - `latestTime < now.toLocalTime()` → Reject: `"Seating time window has already passed"`
    - `earliestTime < now.toLocalTime().minusMinutes(5)` → Reject: `"Earliest seating time cannot be in the past"`
    - `earliestTime >= now.toLocalTime().minusMinutes(5)` and `earliestTime < now.toLocalTime()`:
      - Clamped effective earliest time: `effectiveEarliest = now.toLocalTime()`

---

## 2. Error Payload Model: `ProblemDetail` (RFC 7807)

When validation fails on any of the three services, the HTTP response body conforms to RFC 7807 `ProblemDetail`:

```json
{
  "type": "https://example.invalid/problems/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Requested dining time cannot be in the past",
  "instance": "/api/v1/reservations",
  "invalidParams": [
    {
      "name": "startTime",
      "reason": "Reservation start time cannot be in the past"
    }
  ]
}
```

---

## 3. Timezone Projection / Resolution Model

- **`RestaurantViewEntity`** (stored in PostgreSQL and updated via Kafka `RestaurantCreatedEvent`):
  - `id`: `UUID` (Primary Key)
  - `name`: `VARCHAR(100)`
  - `timezone`: `VARCHAR(50)` (e.g. `"Europe/Amsterdam"`, `"America/New_York"`)
- **Fallback Rule**:
  - If `timezone == null`, empty, or invalid IANA string, default to `"Europe/Amsterdam"`.
