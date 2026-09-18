# API & Interface Contracts: Manager Portal Upgrade

**Feature**: `014-manager-portal-upgrade`  
**Protocol**: HTTP / OAuth 2.0 (OpenID Connect PKCE + JWT Bearer)  
**Gateway Base URL**: `http://localhost:8080`  
**Keycloak Base URL**: `http://localhost:8081`

---

## 1. Authentication & Security Contract

### 1.1 Keycloak OpenID Connect Flow
The manager portal initiates standard Authorization Code Flow with PKCE against Keycloak:
- **Authorization Endpoint**: `http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/auth`
- **Token Endpoint**: `http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token`
- **Client ID**: `rube-goldberg-app`
- **Response Type**: `code`
- **PKCE Method**: `S256`
- **Redirect URI**: Current portal URL (e.g. `http://localhost:8080/ui/manager/index.html`)

### 1.2 Authorization Header
All subsequent API calls to `/api/v1/**` include:
```http
Authorization: Bearer <JWT_ACCESS_TOKEN>
```

### 1.3 Role Authorization Rules
- Portal UI gate requires `realm_access.roles` to contain `RESTAURANT_MANAGER` or `ADMIN`.
- Backend endpoints enforce resource server role validation (`403 Forbidden` if role is missing).

---

## 2. Manager Portal API Contracts

### 2.1 Analytics Service (`/api/v1/analytics`)

#### `GET /api/v1/analytics/summary`
Retrieves live aggregated performance metrics.

- **Query Parameters**:
  - `restaurantId` (UUID, optional): When supplied, filters metrics to that restaurant. When omitted, returns platform-wide aggregated totals.
- **Headers**:
  - `Authorization: Bearer <token>`
- **Response (200 OK)**:
```json
{
  "totalReservations": 42,
  "cancelledReservations": 3,
  "noShows": 1,
  "waitingListEntries": 8,
  "waitingListConversionRate": 37.5,
  "averagePartySize": 3.2,
  "cancellationRate": 7.1,
  "cancellationCategoryBreakdown": {
    "CHANGE_OF_PLANS": 2,
    "ILLNESS": 1
  },
  "partySizeDistribution": {
    "2": 15,
    "4": 20,
    "6": 7
  }
}
```
- **Error Responses**:
  - `401 Unauthorized`: Missing or invalid Bearer token.
  - `403 Forbidden`: Authenticated user lacks manager/admin role.

---

### 2.2 Restaurant Service (`/api/v1/restaurants`)

#### `GET /api/v1/restaurants`
Populates the restaurant dropdown selector.
- **Query Parameters**: `page` (int, default 0), `size` (int, default 50)
- **Response (200 OK)**: Paginated list of `RestaurantEntity` objects.

#### `POST /api/v1/restaurants`
Registers an establishment.
- **Request Body**:
```json
{
  "name": "De Kas",
  "address": "Kamerlingh Onneslaan 3, Amsterdam",
  "timezone": "Europe/Amsterdam",
  "defaultReservationDurationMinutes": 90,
  "minBookingAdvanceMinutes": 30,
  "maxBookingHorizonDays": 60,
  "cancellationWindowHours": 2
}
```
- **Response (201 Created)**: Created `RestaurantEntity`.

#### `GET /api/v1/restaurants/{id}/tables`
Lists tables for the selected restaurant.
- **Response (200 OK)**: List of `TableEntity` objects (`id`, `tableNumber`, `capacity`, `zone`).

#### `POST /api/v1/restaurants/{id}/tables`
Adds a new dining table to the floor inventory.
- **Request Body**:
```json
{
  "tableNumber": "T12",
  "capacity": 4,
  "zone": "Main Dining Room"
}
```
- **Response (201 Created)**: Created table object.

#### `PUT /api/v1/restaurants/{id}/opening-hours`
Updates operating hours.
- **Request Body**: Array of daily opening schedule entries.
- **Response (200 OK)**: Updated schedule.

---

### 2.3 Reservation Service (`/api/v1/reservations`)

#### `GET /api/v1/reservations`
Lists scheduled and past reservations for an establishment.
- **Query Parameters**:
  - `restaurantId` (UUID, required)
  - `page` (int, optional, default 0)
  - `size` (int, optional, default 50)
- **Response (200 OK)**: Paginated reservations with guest name, party size, start time, status, allocated tables.

#### `PATCH /api/v1/reservations/{id}/status`
Transitions the dining lifecycle status.
- **Request Body**:
```json
{
  "status": "ARRIVED"
}
```
- **Valid Values**: `"ARRIVED"`, `"COMPLETED"`, `"NO_SHOW"`, `"CANCELLED"`
- **Response (200 OK)**: Updated `ReservationEntity`.

#### `DELETE /api/v1/reservations/{id}`
Cancels an active reservation and releases table inventory.
- **Query Parameters**: `reason` (string, optional)
- **Response (200 OK)**: Cancelled reservation.

#### `POST /api/v1/reservations`
Creates a confirmed reservation (including retroactive back-filling for managers).
- **Request Body**:
```json
{
  "restaurantId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "customerName": "Walk-in Guest",
  "customerEmail": "guest@example.com",
  "partySize": 4,
  "startTime": "2026-09-16T12:00:00Z",
  "durationMinutes": 90
}
```
- **Response (201 Created)**: Confirmed reservation details.

---

### 2.4 Availability Service (`/api/v1/availability`)

#### `GET /api/v1/availability`
Checks seating availability (managers are permitted past temporal checks).
- **Query Parameters**:
  - `restaurantId` (UUID, required)
  - `date` (LocalDate, required, `YYYY-MM-DD`)
  - `time` (LocalTime, required, `HH:mm:ss`)
  - `partySize` (int, required)
- **Response (200 OK)**:
```json
{
  "isAvailable": true,
  "availableSlots": ["19:00:00", "19:15:00", "19:30:00"]
}
```

---

### 2.5 Waiting List Service (`/api/v1/waiting-list`)

#### `GET /api/v1/waiting-list` *(NEW Endpoint)*
Lists guests in the FIFO queue for a restaurant.
- **Required Role**: `RESTAURANT_MANAGER` or `ADMIN`
- **Query Parameters**:
  - `restaurantId` (UUID, required)
  - `targetDate` (LocalDate, optional)
  - `status` (string, optional, default `"WAITING"`)
- **Response (200 OK)**:
```json
[
  {
    "id": "7b13e944-938b-4a55-bd42-f29e37cfcb33",
    "restaurantId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "customerId": "81014e30-b3e8-466c-bd18-472061bb0e9b",
    "customerEmail": "guest@example.com",
    "targetDate": "2026-09-16",
    "earliestTime": "19:00:00",
    "latestTime": "20:30:00",
    "partySize": 4,
    "status": "WAITING",
    "createdAt": "2026-09-16T15:22:10.123Z"
  }
]
```
- **Error Responses**:
  - `401 Unauthorized`: Unauthenticated caller.
  - `403 Forbidden`: Caller lacks `RESTAURANT_MANAGER` or `ADMIN` role.
