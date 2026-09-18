# Data Model: Platform Functionality and Usability Fixes

**Feature**: `016-platform-functionality-fixes`  
**Date**: 2026-09-17  
**Status**: Approved  

---

## 1. Entity Models & Schema Updates

### 1.1 Restaurant (`restaurant` table)
| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | No | - | Primary key |
| `name` | VARCHAR(150) | No | - | Trade name |
| `address` | VARCHAR(1000) | No | - | Physical location address |
| `timezone` | VARCHAR(50) | No | - | IANA timezone (e.g. Europe/Amsterdam) |
| `default_reservation_duration_minutes` | INT | No | 90 | Default dining duration in minutes (15-480) |
| `max_reservation_duration_minutes` | INT | No | 180 | **[NEW]** Maximum dining duration allowed (15-480; customer dropdown renders 15-minute increments up to this limit) |
| `min_booking_advance_minutes` | INT | No | 30 | Minimum lead time to book |
| `max_booking_horizon_days` | INT | No | 60 | Maximum advance days to book |
| `cancellation_window_hours` | INT | No | 2 | Minimum notice hours required to cancel |
| `created_at` | TIMESTAMP WITH TIME ZONE | No | - | Record creation timestamp |
| `updated_at` | TIMESTAMP WITH TIME ZONE | No | - | Record last update timestamp |

### 1.2 Restaurant Table (`restaurant_table` table)
| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | No | - | Primary key |
| `restaurant_id` | UUID | No | - | Foreign key reference to restaurant |
| `table_number` | VARCHAR(50) | No | - | Table label or identifier (e.g. "T1", "Rooftop-4") |
| `capacity` | INT | No | - | Physical seating capacity (1-50) |
| `zone` | VARCHAR(50) | No | 'Main Dining' | **[NEW]** Floor zone or location (e.g., "Rooftop", "Patio", "Bar") |
| `status` | VARCHAR(20) | No | 'ACTIVE' | Lifecycle state: `ACTIVE`, `INACTIVE` |
| `created_at` | TIMESTAMP WITH TIME ZONE | No | - | Creation timestamp |

### 1.3 Table Combination (`table_combination` table)
| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | No | - | Primary key |
| `restaurant_id` | UUID | No | - | Foreign key reference to restaurant |
| `name` | VARCHAR(100) | No | - | Combination name (e.g. "Party Hall 1", "Combo: T1 + T2") |
| `table_ids` | UUID[] or JSON | No | - | Array of constituent table UUIDs (min 2, max 10) |
| `combined_capacity` | INT | No | - | Aggregate seating capacity |
| `status` | VARCHAR(20) | No | 'ACTIVE' | Lifecycle state: `ACTIVE`, `INACTIVE` |
| `created_at` | TIMESTAMP WITH TIME ZONE | No | - | Creation timestamp |

### 1.4 Opening Hours (`opening_hours` table)
| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | No | - | Primary key |
| `restaurant_id` | UUID | No | - | Foreign key reference to restaurant |
| `day_of_week` | VARCHAR(20) | Yes | - | Day of week: `MONDAY` ... `SUNDAY` |
| `specific_date` | DATE | Yes | - | Specific calendar date for one-off schedule |
| `open_time` | TIME | Yes | - | Opening time (e.g. 10:00:00) |
| `close_time` | TIME | Yes | - | Closing time (e.g. 23:00:00) |
| `is_closed` | BOOLEAN | No | false | True if establishment is closed for service |

### 1.5 Reservation (`reservation` table) & Allocation (`reservation_table_allocation` table)
| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | UUID | No | - | Reservation primary key |
| `restaurant_id` | UUID | No | - | Restaurant reference |
| `customer_id` | UUID | No | - | Customer identity |
| `customer_name` | VARCHAR(200) | No | - | Guest name |
| `customer_email` | VARCHAR(255) | No | - | Contact email |
| `party_size` | INT | No | - | Total guest count |
| `start_time` | TIMESTAMP WITH TIME ZONE | No | - | Booking start instant |
| `end_time` | TIMESTAMP WITH TIME ZONE | No | - | Booking end instant |
| `status` | VARCHAR(30) | No | 'CONFIRMED' | `CONFIRMED`, `ARRIVED`, `COMPLETED`, `CANCELLED`, `NO_SHOW` |
| `cancellation_window_hours` | INT | No | 2 | Applicable cancellation notice window |
| `cancellation_reason` | VARCHAR(500) | Yes | - | Stored reason when cancelled |
| `created_at` | TIMESTAMP WITH TIME ZONE | No | - | Creation timestamp |
| `updated_at` | TIMESTAMP WITH TIME ZONE | No | - | Last modification timestamp |

**Allocations (`reservation_table_allocation`)**:
- Each confirmed reservation links to 1 or more records in `reservation_table_allocation` storing `(reservation_id, table_id, restaurant_id, start_time, end_time)`.

---

## 2. API Data Transfer Objects (DTOs)

### 2.1 Table Requests & Responses
```json
// POST /api/v1/restaurants/{id}/tables
// PUT /api/v1/restaurants/{id}/tables/{tableId}
{
  "tableNumber": "T6",
  "capacity": 4,
  "zone": "Rooftop"
}
```

```json
// POST /api/v1/restaurants/{id}/table-combinations
{
  "name": "Combo: T1 + T2", // Optional, defaults to "Combo: T1 + T2"
  "tableIds": [
    "307325bf-b6d9-4ac2-9e28-9f7965e67689",
    "532ba9fe-9d1a-42dc-a51b-b0c0d518e437"
  ],
  "combinedCapacity": 8     // Optional, defaults to sum of constituent table capacities
}
```

### 2.2 Restaurant Settings Update Request
```json
// PUT /api/v1/restaurants/{id}
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

### 2.3 Enriched Reservation Response
```json
// GET /api/v1/reservations?restaurantId={id}
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
      "allocatedTables": [
        "307325bf-b6d9-4ac2-9e28-9f7965e67689"
      ],
      "allocatedTableLabels": [
        "T1"
      ]
    }
  ]
}
```

### 2.4 Availability Query & Response
```json
// GET /api/v1/availability?restaurantId={id}&date=2026-09-20&time=19:00:00&partySize=4
{
  "restaurantId": "d13e9a12-887e-4054-9467-f31ea3a660d5",
  "requestedTime": "2026-09-20T19:00:00+02:00[Europe/Amsterdam]",
  "partySize": 4,
  "isAvailable": true,
  "isClosed": false,
  "reason": null,
  "availableSlots": [
    "2026-09-20T19:00:00+02:00[Europe/Amsterdam]"
  ],
  "maxTableCapacity": 12,
  "totalRestaurantCapacity": 48
}
```
If closed:
```json
{
  "restaurantId": "d13e9a12-887e-4054-9467-f31ea3a660d5",
  "requestedTime": "2026-09-21T19:00:00+02:00[Europe/Amsterdam]",
  "partySize": 4,
  "isAvailable": false,
  "isClosed": true,
  "reason": "RESTAURANT_CLOSED",
  "availableSlots": []
}
```
If party size exceeds capacity:
```json
{
  "restaurantId": "d13e9a12-887e-4054-9467-f31ea3a660d5",
  "requestedTime": "2026-09-20T19:00:00+02:00[Europe/Amsterdam]",
  "partySize": 60,
  "isAvailable": false,
  "isClosed": false,
  "reason": "EXCEEDS_TOTAL_CAPACITY",
  "availableSlots": [],
  "totalRestaurantCapacity": 48
}
```
