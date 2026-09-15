# Interface & Error Contracts: Input Values Constraints and Validation

**Feature**: `008-input-validation-constraints` | **Date**: 2026-09-14

## 1. RFC 7807 Standard Error Response Contract

All microservices adhere to the standard RFC 7807 `ProblemDetail` specification for validation errors with an added `invalidParams` property.

### 1.1 HTTP 400 Bad Request Schema
```json
{
  "type": "https://example.invalid/problems/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more request fields or parameters failed validation",
  "instance": "/api/v1/reservations",
  "invalidParams": [
    {
      "name": "partySize",
      "reason": "Party size must be at least 1 guest"
    },
    {
      "name": "durationMinutes",
      "reason": "Duration cannot exceed 480 minutes (8 hours)"
    }
  ]
}
```

### 1.2 Unrecognized Status Enum Schema
When an unrecognized status string is passed in `UpdateStatusRequest(ReservationStatus status)`:
```json
{
  "type": "https://example.invalid/problems/invalid-status",
  "title": "Invalid Lifecycle Status",
  "status": 400,
  "detail": "Invalid status value: 'UNKNOWN'. Allowed statuses: CONFIRMED, ARRIVED, COMPLETED, NO_SHOW, CANCELLED",
  "instance": "/api/v1/reservations/3fa85f64-5717-4562-b3fc-2c963f66afa6/status"
}
```

---

## 2. Service Endpoint Input Contracts & Boundaries

### 2.1 Reservation Service (`reservation-service` - Port 8084)

#### `POST /api/v1/reservations`
- **Request Body**: `application/json`
- **Validation Rules**:
  - `restaurantId`: `UUID`, Required (`@NotNull`)
  - `partySize`: `Integer`, Optional (default 2), Bounded `1 <= partySize <= 50`
  - `durationMinutes`: `Integer`, Optional (default 90), Bounded `15 <= durationMinutes <= 480`
  - `cancellationWindowHours`: `Integer`, Optional (default 2), Bounded `0 <= cancellationWindowHours <= 168`
  - `customerEmail`: `String`, Optional (default "customer@example.com"), Valid Email syntax, Max 255 chars
  - `customerName`: `String`, Optional (default "Customer"), Max 200 chars
  - `startTime`: `Instant`, Optional (default `Instant.now()`). For `CUSTOMER` role: must be >= `Instant.now().minusSeconds(300)` and <= `Instant.now().plus(Duration.ofDays(365))`. For `RESTAURANT_MANAGER` / `ADMIN`: past timestamps allowed.
- **Responses**:
  - `201 Created`: Returns `ReservationResponseDto`
  - `400 Bad Request`: Validation failure (`ProblemDetail` with `invalidParams`)
  - `409 Conflict`: Tables not available

#### `PATCH /api/v1/reservations/{id}/status`
- **Request Body**: `{"status": "ARRIVED"}`
- **Validation Rules**:
  - `id`: `UUID`, Required
  - `status`: `ReservationStatus` enum (`CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`), Required (`@NotNull`)
- **Responses**:
  - `200 OK`: Status updated successfully
  - `400 Bad Request`: Unrecognized enum or invalid state machine transition

#### `DELETE /api/v1/reservations/{id}`
- **Query Parameters**:
  - `cancellationWindowHours`: `int`, **Deprecated / Ignored**. The reservation's snapshotted `cancellationWindowHours` is authoritatively used.
  - `reason`: `String`, Optional, Max 500 characters
- **Responses**:
  - `200 OK`: Reservation cancelled and tables released
  - `409 Conflict`: Authoritative cancellation deadline has passed (`https://example.invalid/problems/cancellation-window-passed`)

---

### 2.2 Restaurant Service (`restaurant-service` - Port 8083)

#### `POST /api/v1/restaurants`
- **Request Body**: `application/json`
- **Validation Rules**:
  - `name`: `String`, Required (`@NotBlank`), Max 150 chars
  - `address`: `String`, Required (`@NotBlank`), Max 1000 chars
  - `timezone`: `String`, Required (`@NotBlank`), Valid IANA timezone identifier
  - `defaultReservationDurationMinutes`: `Integer`, Optional (default 90), Bounded `15 <= val <= 480`
  - `minBookingAdvanceMinutes`: `Integer`, Optional (default 30), Bounded `0 <= val <= 10080` (7 days)
  - `maxBookingHorizonDays`: `Integer`, Optional (default 60), Bounded `1 <= val <= 365`
  - `cancellationWindowHours`: `Integer`, Optional (default 2), Bounded `0 <= val <= 168` (7 days)
- **Responses**:
  - `201 Created`: Restaurant registered
  - `400 Bad Request`: Field validation failure (`ProblemDetail` with `invalidParams`)

#### `POST /api/v1/restaurants/{id}/tables`
- **Request Body**: `application/json`
- **Validation Rules**:
  - `tableNumber`: `String`, Required (`@NotBlank`), Max 50 chars
  - `capacity`: `Integer`, Required (`@NotNull`), Bounded `1 <= capacity <= 50`

#### `POST /api/v1/restaurants/{id}/table-combinations`
- **Request Body**: `application/json`
- **Validation Rules**:
  - `name`: `String`, Required (`@NotBlank`), Max 100 chars
  - `tableIds`: `List<UUID>`, Required (`@NotNull`), Size `2 <= size <= 10`

#### `PUT /api/v1/restaurants/{id}/opening-hours`
- **Request Body**: `application/json`
- **Validation Rules**:
  - `schedules`: `List<ScheduleItemDto>`, Required (`@NotEmpty`)
  - Each item: `dayOfWeek` (1–7), `openTime` < `closeTime` if not `isClosed`

---

### 2.3 Waiting List Service (`waiting-list-service` - Port 8086)

#### `POST /api/v1/waiting-list`
- **Request Body**: `application/json`
- **Validation Rules**:
  - `restaurantId`: `UUID`, Required (`@NotNull`)
  - `customerEmail`: `String`, Required (`@NotBlank`), Valid Email syntax, Max 255 chars
  - `targetDate`: `LocalDate`, Required (`@NotNull`), Must be >= `LocalDate.now()` and <= `LocalDate.now().plusDays(365)`
  - `earliestTime`: `LocalTime`, Required (`@NotNull`)
  - `latestTime`: `LocalTime`, Required (`@NotNull`), Must be >= `earliestTime`
  - `partySize`: `int`, Bounded `1 <= partySize <= 50`

---

### 2.4 Availability Service (`availability-service` - Port 8085)

#### `GET /api/v1/availability`
- **Query Parameters**:
  - `restaurantId`: `UUID`, Required
  - `date`: `LocalDate`, Required, Must be >= `LocalDate.now()` and <= `LocalDate.now().plusDays(365)`
  - `time`: `LocalTime`, Required
  - `partySize`: `int`, Optional (default 2), Bounded `1 <= partySize <= 50`

---

### 2.5 Customer Service (`customer-service` - Port 8087)

#### `PUT /api/v1/customers/me`
- **Request Body**: `application/json`
- **Validation Rules**:
  - `firstName`: `String`, Required (`@NotBlank`), Max 50 chars
  - `lastName`: `String`, Required (`@NotBlank`), Max 50 chars
  - `phoneNumber`: `String`, Optional, Length 5–25 chars, valid telephone pattern
