# API Contract: Temporal Validation & Past-Time Prevention (with Manager Back-Filling)

**Feature**: `013-prevent-past-time-requests`
**Date**: 2026-09-16

---

## 1. Availability Service (`availability-service` - Port 8084)

### `GET /api/v1/availability`
Queries table availability for a specific restaurant, date, and time.

#### Request
- **Method**: `GET`
- **Headers**: Optional `Authorization: Bearer <token>`
- **Query Parameters**:
  - `restaurantId` (`UUID`, required): ID of restaurant
  - `date` (`LocalDate`, required, format `YYYY-MM-DD`): Dining date
  - `time` (`LocalTime`, required, format `HH:mm:ss`): Dining time
  - `partySize` (`Integer`, optional, 1-50): Party size (default: 2)

#### Validation Rules
1. If caller possesses `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`, past-time checks are bypassed to allow back-filling.
2. For customers and unauthenticated callers:
   - `(date, time)` in restaurant timezone MUST NOT precede `now - 5 minutes`.
3. For all callers:
   - `(date, time)` MUST NOT exceed `now + 365 days`.

#### Responses
- **`200 OK`**: Table availability returned (allowed for future queries by anyone, or past queries by managers/admins).
- **`400 Bad Request`**: Temporal validation failure (for customers/unauthenticated callers trying past queries, or exceeding 365 days).
  ```json
  {
    "type": "https://example.invalid/problems/validation-error",
    "title": "Validation Failed",
    "status": 400,
    "detail": "Dining time cannot be in the past",
    "invalidParams": [
      {
        "name": "time",
        "reason": "Dining time cannot be in the past"
      }
    ]
  }
  ```
- **`404 Not Found`**: Restaurant does not exist.

---

## 2. Reservation Service (`reservation-service` - Port 8085)

### `POST /api/v1/reservations`
Creates a confirmed table reservation.

#### Request
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`, `Authorization: Bearer <token>`
- **Body**:
  ```json
  {
    "restaurantId": "11111111-1111-1111-1111-111111111111",
    "customerId": "22222222-2222-2222-2222-222222222222",
    "customerName": "Alice Smith",
    "customerEmail": "alice@example.com",
    "partySize": 4,
    "startTime": "2026-09-20T19:00:00Z",
    "durationMinutes": 90,
    "cancellationWindowHours": 2
  }
  ```

#### Validation Rules
1. If caller possesses `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`, past-time check is bypassed to allow back-filling.
2. For customers and unauthenticated callers:
   - `startTime` MUST NOT precede `Instant.now() - 300 seconds`.
3. For all callers:
   - `startTime` MUST NOT exceed `Instant.now() + 365 days`.

#### Responses
- **`201 Created`**: Reservation created successfully (allowed for future bookings by anyone, or past bookings by managers/admins).
- **`400 Bad Request`**: Start time in the past (customers/unauthenticated) or exceeds advance window (all).
  ```json
  {
    "type": "https://example.invalid/problems/validation-error",
    "title": "Validation Failed",
    "status": 400,
    "detail": "Reservation start time cannot be in the past",
    "invalidParams": [
      {
        "name": "startTime",
        "reason": "Reservation start time cannot be in the past"
      }
    ]
  }
  ```
- **`409 Conflict`**: No tables available for the requested time slot.

---

## 3. Waiting List Service (`waiting-list-service` - Port 8086)

### `POST /api/v1/waiting-list`
Enters the FIFO queue for table cancellation openings (universal across all roles; no back-filling).

#### Request
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`, `Authorization: Bearer <token>`
- **Body**:
  ```json
  {
    "restaurantId": "11111111-1111-1111-1111-111111111111",
    "customerId": "22222222-2222-2222-2222-222222222222",
    "customerEmail": "alice@example.com",
    "targetDate": "2026-09-20",
    "earliestTime": "18:00:00",
    "latestTime": "21:00:00",
    "partySize": 4
  }
  ```

#### Validation Rules (All Roles)
1. `targetDate` in restaurant timezone MUST NOT precede `today`.
2. `targetDate` MUST NOT exceed `today + 365 days`.
3. `earliestTime` MUST be `<= latestTime`.
4. If `targetDate == today`:
   - `latestTime` MUST NOT precede `now.toLocalTime()`.
   - `earliestTime` MUST NOT precede `now.toLocalTime() - 5 minutes`.
   - If `earliestTime` is between `now - 5 minutes` and `now`, it is clamped to `now.toLocalTime()`.

#### Responses
- **`201 Created`**: Customer placed on waiting list.
- **`400 Bad Request`**: Temporal validation failure.
  ```json
  {
    "type": "https://example.invalid/problems/validation-error",
    "title": "Validation Failed",
    "status": 400,
    "detail": "Seating time window has already passed",
    "invalidParams": [
      {
        "name": "latestTime",
        "reason": "Seating time window has already passed"
      }
    ]
  }
  ```
