# Interface Contract: Customer Portal API Specification

**Feature**: Customer Portal Upgrade (`015-customer-portal-upgrade`)
**Date**: 2026-09-17

This contract specifies the HTTP and Server-Sent Events interfaces accessed by the Customer Portal Single Page Application.

---

## 1. Gateway Static Asset Routing

| Path | Target | Description | Security |
|------|--------|-------------|----------|
| `/ui/customer/index.html` | Static File | Customer portal HTML entrypoint | Public / OIDC gatekeeper |
| `/ui/customer/app.js` | Static File | Customer portal single-page logic | Public |
| `/ui/customer/keycloak.js` | Static File | Self-hosted Keycloak JavaScript adapter | Public |

---

## 2. Customer Profile Endpoint

### `GET /api/v1/customers/me`
Retrieves or lazily provisions the customer profile matching the authenticated JWT token subject.

- **Headers**: `Authorization: Bearer <jwt>`
- **Response `200 OK`**:
```json
{
  "id": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
  "keycloakSubject": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "customer1@example.com",
  "firstName": "Alice",
  "lastName": "Customer",
  "phoneNumber": "+31612345678"
}
```

---

## 3. Table Availability Endpoint

### `GET /api/v1/availability`
Checks table capacity for a requested date, time, and party size.

- **Query Parameters**:
  - `restaurantId`: UUID (required)
  - `date`: `YYYY-MM-DD` (required, current or future)
  - `time`: `HH:mm:ss` (required)
  - `partySize`: integer (required, 1-50)
- **Response `200 OK` (Available)**:
```json
{
  "isAvailable": true,
  "availableSlots": ["18:30:00", "19:00:00", "19:30:00"],
  "availableTables": [
    {
      "tableId": "d3b07384-d113-467d-944a-4467d8d2ef32",
      "capacity": 4
    }
  ],
  "combinations": []
}
```
- **Response `200 OK` (Fully Booked)**:
```json
{
  "isAvailable": false,
  "availableSlots": [],
  "availableTables": [],
  "combinations": []
}
```

---

## 4. Dining Reservations Endpoints

### `POST /api/v1/reservations`
Creates a confirmed dining reservation.

- **Headers**:
  - `Content-Type: application/json`
  - `Authorization: Bearer <jwt>`
- **Request Body**:
```json
{
  "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
  "customerName": "Alice Customer",
  "customerEmail": "customer1@example.com",
  "partySize": 4,
  "startTime": "2026-09-20T19:00:00Z",
  "durationMinutes": 90,
  "availableTables": [
    {
      "tableId": "d3b07384-d113-467d-944a-4467d8d2ef32",
      "capacity": 4
    }
  ]
}
```
- **Response `201 Created`**:
```json
{
  "id": "7b8f88c8-cf36-4c4f-a0e2-8ea98048290f",
  "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
  "customerName": "Alice Customer",
  "customerEmail": "customer1@example.com",
  "partySize": 4,
  "startTime": "2026-09-20T19:00:00Z",
  "endTime": "2026-09-20T20:30:00Z",
  "status": "CONFIRMED",
  "allocatedTableIds": ["d3b07384-d113-467d-944a-4467d8d2ef32"]
}
```
- **Response `409 Conflict`**: Table conflict or slot filled concurrently.

---

### `GET /api/v1/reservations`
Lists reservations for a restaurant or the authenticated customer.

- **Headers**: `Authorization: Bearer <jwt>`
- **Query Parameters**:
  - `restaurantId`: UUID (optional / filter)
  - `page`: integer (default 0)
  - `size`: integer (default 50)
- **Response `200 OK`**:
```json
{
  "content": [
    {
      "id": "7b8f88c8-cf36-4c4f-a0e2-8ea98048290f",
      "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
      "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
      "customerName": "Alice Customer",
      "customerEmail": "customer1@example.com",
      "partySize": 4,
      "startTime": "2026-09-20T19:00:00Z",
      "endTime": "2026-09-20T20:30:00Z",
      "status": "CONFIRMED",
      "cancellationWindowHours": 2
    }
  ],
  "totalElements": 1
}
```

---

### `DELETE /api/v1/reservations/{id}`
Cancels an upcoming reservation within the allowable policy window.

- **Headers**: `Authorization: Bearer <jwt>`
- **Query Parameters**:
  - `reason`: string (optional, defaults to "Customer cancellation")
- **Response `200 OK`**:
```json
{
  "id": "7b8f88c8-cf36-4c4f-a0e2-8ea98048290f",
  "status": "CANCELLED",
  "cancellationReason": "Customer cancellation",
  "updatedAt": "2026-09-17T12:00:00Z"
}
```
- **Response `409 Conflict`**: Cancellation deadline has passed (e.g., within 2 hours of reservation).

---

## 5. Waiting List Endpoints

### `POST /api/v1/waiting-list`
Places a customer on the fair FIFO waiting queue for an unavailable slot.

- **Headers**:
  - `Content-Type: application/json`
  - `Authorization: Bearer <jwt>`
- **Request Body**:
```json
{
  "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
  "customerEmail": "customer1@example.com",
  "targetDate": "2026-09-20",
  "earliestTime": "18:00:00",
  "latestTime": "20:00:00",
  "partySize": 4
}
```
- **Response `201 Created`**:
```json
{
  "id": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "restaurantId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
  "customerEmail": "customer1@example.com",
  "targetDate": "2026-09-20",
  "earliestTime": "18:00:00",
  "latestTime": "20:00:00",
  "partySize": 4,
  "status": "WAITING",
  "createdAt": "2026-09-17T12:05:00Z"
}
```

---

### `POST /api/v1/waiting-list/offers/{offerId}/accept`
Claims an offered table opening and converts it to a confirmed reservation.

- **Headers**: `Authorization: Bearer <jwt>`
- **Path Parameter**: `offerId` (UUID)
- **Response `200 OK`**:
```json
{
  "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "waitingListEntryId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "status": "ACCEPTED",
  "updatedAt": "2026-09-20T17:35:00Z"
}
```
- **Response `410 Gone`**: Offer expired or already converted.

---

## 6. Real-Time Server-Sent Events (SSE) Endpoint

### `GET /api/v1/notifications/stream`
Establishes a persistent, streaming HTTP response using `text/event-stream` for live customer notifications.

- **Headers**:
  - `Accept: text/event-stream`
  - `Authorization: Bearer <jwt>` (or query parameter `token=<jwt>`)
- **Response `200 OK`**:
  - Content-Type: `text/event-stream`
  - Cache-Control: `no-cache`
  - Connection: `keep-alive`

#### SSE Stream Format:
```text
event: connected
data: {"status":"CONNECTED","customerId":"c7128e4e-0a56-43b8-89c5-7f2834789b12"}

event: WAITING_LIST_OFFER
data: {"offerId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","waitingListEntryId":"1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed","restaurantId":"550e8400-e29b-41d4-a716-446655440000","offeredStartTime":"2026-09-20T19:00:00Z","expiresAt":"2026-09-20T17:45:00Z"}

event: RESERVATION_CONFIRMED
data: {"reservationId":"7b8f88c8-cf36-4c4f-a0e2-8ea98048290f","startTime":"2026-09-20T19:00:00Z","partySize":4}

event: RESERVATION_CANCELLED
data: {"reservationId":"7b8f88c8-cf36-4c4f-a0e2-8ea98048290f","reason":"Customer cancellation"}
```
