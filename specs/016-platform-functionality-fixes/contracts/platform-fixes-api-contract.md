# API Contract: Platform Functionality & Usability Fixes

**Feature**: `016-platform-functionality-fixes`  
**Date**: 2026-09-17  
**Status**: Approved  

---

## 1. Restaurant Service Endpoints

### 1.1 Create Table
- **Method / Path**: `POST /api/v1/restaurants/{id}/tables`
- **Security**: Bearer JWT (Role: `RESTAURANT_MANAGER` or `ADMIN`)
- **Request Body**:
  ```json
  {
    "tableNumber": "T6",
    "capacity": 4,
    "zone": "Rooftop"
  }
  ```
- **Responses**:
  - `201 Created`: Returns created `RestaurantTableEntity`.
  - `400 Bad Request`: Validation error (tableNumber blank, capacity < 1, etc.).
  - `409 Conflict`: Table with number already exists in restaurant.

### 1.2 Update Table
- **Method / Path**: `PUT /api/v1/restaurants/{id}/tables/{tableId}`
- **Security**: Bearer JWT (Role: `RESTAURANT_MANAGER` or `ADMIN`)
- **Request Body**:
  ```json
  {
    "tableNumber": "T6-A",
    "capacity": 6,
    "zone": "Patio"
  }
  ```
- **Responses**:
  - `200 OK`: Returns updated table entity.
  - `404 Not Found`: Restaurant or table not found.
  - `400 Bad Request`: Validation failure.

### 1.3 Delete Table
- **Method / Path**: `DELETE /api/v1/restaurants/{id}/tables/{tableId}`
- **Security**: Bearer JWT (Role: `RESTAURANT_MANAGER` or `ADMIN`)
- **Responses**:
  - `204 No Content`: Table deleted or deactivated.
  - `404 Not Found`: Table not found.
  - `409 Conflict`: Table cannot be deleted because it is allocated to active upcoming reservations.

### 1.4 Create Table Combination
- **Method / Path**: `POST /api/v1/restaurants/{id}/table-combinations`
- **Security**: Bearer JWT (Role: `RESTAURANT_MANAGER` or `ADMIN`)
- **Request Body**:
  ```json
  {
    "name": "Combo: T1 + T2", // Optional, defaults to "Combo: {tableNumbers}"
    "tableIds": ["307325bf-b6d9-4ac2-9e28-9f7965e67689", "532ba9fe-9d1a-42dc-a51b-b0c0d518e437"],
    "combinedCapacity": 8     // Optional, defaults to sum of table capacities
  }
  ```
- **Responses**:
  - `201 Created`: Returns created `TableCombinationEntity`.
  - `400 Bad Request`: Less than 2 tables provided or invalid table IDs.

### 1.5 Update Establishment Settings
- **Method / Path**: `PUT /api/v1/restaurants/{id}`
- **Security**: Bearer JWT (Role: `RESTAURANT_MANAGER` or `ADMIN`)
- **Request Body**:
  ```json
  {
    "name": "The Grand Bistro",
    "address": "123 Main St, Amsterdam",
    "timezone": "Europe/Amsterdam",
    "defaultReservationDurationMinutes": 90,
    "maxReservationDurationMinutes": 180,
    "minBookingAdvanceMinutes": 30,
    "maxBookingHorizonDays": 60,
    "cancellationWindowHours": 2
  }
  ```
- **Responses**:
  - `200 OK`: Returns updated restaurant entity.
  - `400 Bad Request`: Validation failure.

### 1.6 Configure Operating Hours Schedule
- **Method / Path**: `PUT /api/v1/restaurants/{id}/opening-hours`
- **Security**: Bearer JWT (Role: `RESTAURANT_MANAGER` or `ADMIN`)
- **Request Body**:
  ```json
  {
    "schedules": [
      {
        "dayOfWeek": "MONDAY",
        "openTime": null,
        "closeTime": null,
        "isClosed": true
      },
      {
        "dayOfWeek": "TUESDAY",
        "openTime": "11:00:00",
        "closeTime": "22:00:00",
        "isClosed": false
      }
    ]
  }
  ```
- **Responses**:
  - `200 OK`: Updated schedule saved.

---

## 2. Availability Service Endpoints

### 2.1 Check Availability
- **Method / Path**: `GET /api/v1/availability?restaurantId={id}&date={YYYY-MM-DD}&time={HH:mm:ss}&partySize={N}`
- **Security**: Public / Authenticated
- **Response `200 OK`**:
  ```json
  {
    "restaurantId": "d13e9a12-887e-4054-9467-f31ea3a660d5",
    "requestedTime": "2026-09-20T19:00:00+02:00[Europe/Amsterdam]",
    "partySize": 4,
    "isAvailable": true,
    "isClosed": false,
    "reason": null,
    "availableSlots": ["2026-09-20T19:00:00+02:00[Europe/Amsterdam]"],
    "maxTableCapacity": 12,
    "totalRestaurantCapacity": 48
  }
  ```

---

## 3. Reservation Service Endpoints

### 3.1 List Reservations
- **Method / Path**: `GET /api/v1/reservations?restaurantId={id}&page=0&size=50`
- **Security**: Bearer JWT (Role: `RESTAURANT_MANAGER` or `ADMIN`)
- **Response `200 OK`**:
  ```json
  {
    "content": [
      {
        "id": "782ca7fa-1f19-45d6-8488-ea17a1262d05",
        "restaurantId": "d13e9a12-887e-4054-9467-f31ea3a660d5",
        "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
        "customerName": "Alice Customer",
        "customerEmail": "customer1@example.com",
        "partySize": 4,
        "startTime": "2026-09-20T19:00:00Z",
        "endTime": "2026-09-20T20:30:00Z",
        "status": "CONFIRMED",
        "cancellationReason": null,
        "allocatedTables": ["307325bf-b6d9-4ac2-9e28-9f7965e67689"],
        "allocatedTableLabels": ["T1"]
      }
    ],
    "totalElements": 1,
    "totalPages": 1
  }
  ```

### 3.2 Create Reservation
- **Method / Path**: `POST /api/v1/reservations`
- **Security**: Bearer JWT (Role: `CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`)
- **Request Body**:
  ```json
  {
    "restaurantId": "d13e9a12-887e-4054-9467-f31ea3a660d5",
    "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
    "customerName": "Alice Customer",
    "customerEmail": "customer1@example.com",
    "partySize": 4,
    "startTime": "2026-09-20T19:00:00Z",
    "durationMinutes": 105,
    "cancellationWindowHours": 2
  }
  ```
- **Responses**:
  - `201 Created`: Reservation created with actual physical table allocation.
  - `409 Conflict`: Conflict - Timeslot fully booked or party exceeds total capacity.

---

## 4. Waiting List Service Endpoints

### 4.1 Query Active Waiting List by Customer
- **Method / Path**: `GET /api/v1/waiting-list?customerId={customerId}`
- **Security**: Bearer JWT
- **Response `200 OK`**:
  ```json
  [
    {
      "id": "e4125a1b-1234-4567-89ab-cdef01234567",
      "restaurantId": "d13e9a12-887e-4054-9467-f31ea3a660d5",
      "customerId": "c7128e4e-0a56-43b8-89c5-7f2834789b12",
      "customerEmail": "customer1@example.com",
      "targetDate": "2026-09-20",
      "earliestTime": "18:00:00",
      "latestTime": "20:00:00",
      "partySize": 4,
      "status": "WAITING",
      "createdAt": "2026-09-17T12:00:00Z"
    }
  ]
  ```
