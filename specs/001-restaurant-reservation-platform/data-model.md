# Data Model & Entity Specifications

This document defines the data models, state machines, and relational schemas across the microservices. In accordance with the Database-per-Service principle, each service maintains its own isolated schema.

```
┌────────────────────────┐         ┌────────────────────────┐
│    Customer Service    │         │   Restaurant Service   │
├────────────────────────┤         ├────────────────────────┤
│ - customer_profile     │         │ - restaurant           │
│ - outbox_events        │         │ - opening_hours        │
└────────────────────────┘         │ - restaurant_table     │
                                   │ - table_combination   │
                                   │ - outbox_events        │
                                   └────────────────────────┘
                 │                             │
                 ▼                             ▼
┌────────────────────────┐         ┌────────────────────────┐
│  Reservation Service   │         │  Availability Service  │
├────────────────────────┤         ├────────────────────────┤
│ - reservation          │◄────────│ - restaurant_read_model│
│ - reservation_table    │ (Events)│ - table_read_model     │
│ - outbox_events        │         │ - slot_occupancy       │
└────────────────────────┘         │ - (Redis Cache Layer)  │
                 │                 └────────────────────────┘
                 ▼
┌────────────────────────┐         ┌────────────────────────┐
│  Waiting List Service  │         │  Notification Service  │
├────────────────────────┤         ├────────────────────────┤
│ - waiting_list_entry   │         │ - notification_log     │
│ - waiting_list_offer   │         │ - reminder_schedule    │
│ - outbox_events        │         │ - processed_events     │
└────────────────────────┘         └────────────────────────┘
                 │
                 ▼
┌────────────────────────┐
│   Analytics Service    │
├────────────────────────┤
│ - reservation_metrics  │
│ - waiting_list_metrics │
│ - processed_events     │
└────────────────────────┘
```

---

## 1. Customer Service Schema (`customer_db`)

### `customer_profile`
Represents application-level customer metadata associated with Keycloak OIDC identity.
- `id` (UUID, PK): Unique customer profile identifier.
- `keycloak_subject_id` (VARCHAR(64), UNIQUE, NOT NULL): Keycloak user identity subject identifier.
- `email` (VARCHAR(255), UNIQUE, NOT NULL): Customer primary email address.
- `first_name` (VARCHAR(100), NOT NULL): Customer first name.
- `last_name` (VARCHAR(100), NOT NULL): Customer last name.
- `phone_number` (VARCHAR(30), NULL): Contact telephone number.
- `communication_preferences` (JSONB, NOT NULL): Preferences (e.g., `{"email": true, "sms": false}`).
- `status` (VARCHAR(20), NOT NULL): `ACTIVE`, `SUSPENDED`, `DELETED`.
- `created_at` (TIMESTAMPTZ, NOT NULL): Record creation timestamp.
- `updated_at` (TIMESTAMPTZ, NOT NULL): Record update timestamp.

---

## 2. Restaurant Service Schema (`restaurant_db`)

### `restaurant`
Owns dining establishment configuration and booking policies.
- `id` (UUID, PK): Unique restaurant identifier.
- `name` (VARCHAR(150), NOT NULL): Restaurant name.
- `address` (TEXT, NOT NULL): Physical address.
- `timezone` (VARCHAR(50), NOT NULL): IANA timezone identifier (e.g., `Europe/Amsterdam`, `America/New_York`).
- `default_reservation_duration_minutes` (INT, NOT NULL, DEFAULT 90): Default table booking duration.
- `min_booking_advance_minutes` (INT, NOT NULL, DEFAULT 30): Minimum advance notice required.
- `max_booking_horizon_days` (INT, NOT NULL, DEFAULT 60): Maximum booking window in advance.
- `cancellation_window_hours` (INT, NOT NULL, DEFAULT 2): Minimum advance notice for customer cancellation.
- `status` (VARCHAR(20), NOT NULL, DEFAULT 'ACTIVE'): `ACTIVE`, `INACTIVE`.
- `created_at` (TIMESTAMPTZ, NOT NULL).
- `updated_at` (TIMESTAMPTZ, NOT NULL).

### `opening_hours`
Defines operational shifts per day of the week or calendar exceptions.
- `id` (UUID, PK).
- `restaurant_id` (UUID, FK -> `restaurant.id`, NOT NULL).
- `day_of_week` (INT, NULL): 1 (Monday) to 7 (Sunday) for recurring weekly schedules.
- `specific_date` (DATE, NULL): Specific calendar override date (e.g., holiday closure).
- `open_time` (TIME, NOT NULL): Shift start time (e.g., 12:00:00).
- `close_time` (TIME, NOT NULL): Shift end time (e.g., 15:00:00).
- `is_closed` (BOOLEAN, NOT NULL, DEFAULT FALSE): Indicates closure override for that date.

### `restaurant_table`
Physical seating units in the restaurant.
- `id` (UUID, PK).
- `restaurant_id` (UUID, FK -> `restaurant.id`, NOT NULL).
- `table_number` (VARCHAR(20), NOT NULL): Table identifier label (e.g., "T1", "12B").
- `capacity` (INT, NOT NULL, CHECK (capacity > 0)): Seating capacity.
- `status` (VARCHAR(20), NOT NULL, DEFAULT 'ACTIVE'): `ACTIVE`, `OUT_OF_SERVICE`.
- `created_at` (TIMESTAMPTZ, NOT NULL).

### `table_combination`
Defines explicitly allowed combinations of tables for larger parties.
- `id` (UUID, PK).
- `restaurant_id` (UUID, FK -> `restaurant.id`, NOT NULL).
- `name` (VARCHAR(50), NOT NULL): Combination label (e.g., "T1+T2").
- `table_ids` (UUID[], NOT NULL): Array of `restaurant_table.id` references.
- `combined_capacity` (INT, NOT NULL): Total aggregate capacity of the combination.

---

## 3. Reservation Service Schema (`reservation_db`)

### `reservation`
Authoritative transactional record of restaurant bookings.
- `id` (UUID, PK): Unique reservation identifier.
- `restaurant_id` (UUID, NOT NULL): Target restaurant.
- `customer_id` (UUID, NOT NULL): Booking customer.
- `customer_name` (VARCHAR(200), NOT NULL): Cached snapshot of customer name.
- `customer_email` (VARCHAR(255), NOT NULL): Cached snapshot of customer email.
- `party_size` (INT, NOT NULL, CHECK (party_size > 0)).
- `start_time` (TIMESTAMPTZ, NOT NULL): Reservation start timestamp.
- `end_time` (TIMESTAMPTZ, NOT NULL): Reservation end timestamp (`start_time + duration`).
- `status` (VARCHAR(30), NOT NULL): State machine status:
  - `CONFIRMED`: Table allocated and active.
  - `ARRIVED`: Guest has checked in.
  - `COMPLETED`: Meal finished, table freed.
  - `CANCELLED`: Cancelled by customer or operator.
  - `NO_SHOW`: Guest failed to arrive.
- `cancellation_reason` (TEXT, NULL).
- `version` (BIGINT, NOT NULL, DEFAULT 0): Optimistic locking version.
- `created_at` (TIMESTAMPTZ, NOT NULL).
- `updated_at` (TIMESTAMPTZ, NOT NULL).

### `reservation_table_allocation`
Maps reserved tables to the reservation to enforce exclusion constraints.
- `id` (UUID, PK).
- `reservation_id` (UUID, FK -> `reservation.id`, NOT NULL).
- `table_id` (UUID, NOT NULL): Allocated table ID.
- `restaurant_id` (UUID, NOT NULL).
- `start_time` (TIMESTAMPTZ, NOT NULL).
- `end_time` (TIMESTAMPTZ, NOT NULL).
- **Exclusion/Overlap Constraint**: Unique or GiST index preventing overlapping active allocations for the same `table_id` during `[start_time, end_time)`.

### Reservation State Transitions
```
                ┌──────────────┐
                │  CONFIRMED   │
                └──────┬───────┘
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
   CANCELLED        ARRIVED         NO_SHOW
                       │
                       ▼
                   COMPLETED
```

---

## 4. Availability Service Schema (`availability_db` & Redis)

### Relational Read Models (PostgreSQL)
- `restaurant_view`: Mirrored restaurant configuration, timezone, policies.
- `table_inventory_view`: Mirrored table capacities and valid combinations.
- `slot_occupancy_view`: Aggregated table reservation spans updated via Kafka events.

### Redis Cache Structures
- Key: `availability:{restaurantId}:{date}:{partySize}`
- Value: JSON payload of available time slots and suggested alternative times.
- TTL: 30–60 seconds, evicted immediately upon `ReservationCreated` or `ReservationCancelled` events.

---

## 5. Waiting List Service Schema (`waiting_list_db`)

### `waiting_list_entry`
Tracks customers waiting for unavailable capacity.
- `id` (UUID, PK).
- `restaurant_id` (UUID, NOT NULL).
- `customer_id` (UUID, NOT NULL).
- `customer_email` (VARCHAR(255), NOT NULL).
- `target_date` (DATE, NOT NULL).
- `earliest_time` (TIME, NOT NULL).
- `latest_time` (TIME, NOT NULL).
- `party_size` (INT, NOT NULL).
- `status` (VARCHAR(20), NOT NULL): `WAITING`, `OFFERED`, `CONVERTED`, `CANCELLED`, `EXPIRED`.
- `created_at` (TIMESTAMPTZ, NOT NULL): Used for strict **FIFO** sorting.

### `waiting_list_offer`
Time-limited offers made to waiting customers.
- `id` (UUID, PK).
- `waiting_list_entry_id` (UUID, FK -> `waiting_list_entry.id`, NOT NULL).
- `restaurant_id` (UUID, NOT NULL).
- `offered_start_time` (TIMESTAMPTZ, NOT NULL).
- `offered_table_ids` (UUID[], NOT NULL).
- `expires_at` (TIMESTAMPTZ, NOT NULL): Expiration deadline.
- `status` (VARCHAR(20), NOT NULL): `PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED`.
- `created_at` (TIMESTAMPTZ, NOT NULL).
- `updated_at` (TIMESTAMPTZ, NOT NULL).

---

## 6. Notification Service Schema (`notification_db`)

### `notification_log`
- `id` (UUID, PK).
- `customer_id` (UUID, NOT NULL).
- `recipient_email` (VARCHAR(255), NOT NULL).
- `notification_type` (VARCHAR(50), NOT NULL): `RESERVATION_CONFIRMED`, `RESERVATION_MODIFIED`, `RESERVATION_CANCELLED`, `RESERVATION_REMINDER`, `WAITING_LIST_OFFER`, `WAITING_LIST_EXPIRED`.
- `subject` (VARCHAR(255), NOT NULL).
- `content` (TEXT, NOT NULL).
- `status` (VARCHAR(20), NOT NULL): `PENDING`, `SENT`, `FAILED`.
- `retry_count` (INT, NOT NULL, DEFAULT 0).
- `error_detail` (TEXT, NULL).
- `created_at` (TIMESTAMPTZ, NOT NULL).
- `sent_at` (TIMESTAMPTZ, NULL).

### `reminder_schedule`
Tracks scheduled reminders to guarantee idempotency.
- `id` (UUID, PK).
- `reservation_id` (UUID, UNIQUE, NOT NULL).
- `customer_id` (UUID, NOT NULL).
- `scheduled_reminder_time` (TIMESTAMPTZ, NOT NULL).
- `status` (VARCHAR(20), NOT NULL): `SCHEDULED`, `SENT`, `CANCELLED`.

---

## 7. Analytics Service Schema (`analytics_db`)

### `reservation_daily_metrics`
- `restaurant_id` (UUID, NOT NULL).
- `metric_date` (DATE, NOT NULL).
- `reservations_created_count` (BIGINT, DEFAULT 0).
- `reservations_completed_count` (BIGINT, DEFAULT 0).
- `reservations_cancelled_count` (BIGINT, DEFAULT 0).
- `reservations_no_show_count` (BIGINT, DEFAULT 0).
- `total_guests_count` (BIGINT, DEFAULT 0).
- `avg_party_size` (NUMERIC(4,2), DEFAULT 0.0).
- `PRIMARY KEY (restaurant_id, metric_date)`.

### `waiting_list_daily_metrics`
- `restaurant_id` (UUID, NOT NULL).
- `metric_date` (DATE, NOT NULL).
- `entries_created_count` (BIGINT, DEFAULT 0).
- `offers_created_count` (BIGINT, DEFAULT 0).
- `offers_accepted_count` (BIGINT, DEFAULT 0).
- `offers_expired_count` (BIGINT, DEFAULT 0).
- `conversion_rate` (NUMERIC(5,2), DEFAULT 0.0).
- `PRIMARY KEY (restaurant_id, metric_date)`.

---

## 8. Common Transactional Outbox Schema (`outbox_events`)
Deployed in all services publishing Kafka events (`customer-service`, `restaurant-service`, `reservation-service`, `waiting-list-service`):
- `id` (UUID, PK).
- `aggregate_type` (VARCHAR(50), NOT NULL): e.g., `Reservation`, `Restaurant`, `WaitingListEntry`.
- `aggregate_id` (VARCHAR(64), NOT NULL): Unique ID of the affected domain aggregate.
- `event_type` (VARCHAR(100), NOT NULL): e.g., `ReservationCreated`, `ReservationCancelled`.
- `payload` (JSONB, NOT NULL): The full event payload serialized as JSON.
- `trace_context` (JSONB, NULL): Distributed trace context (`traceparent`, `tracestate`).
- `created_at` (TIMESTAMPTZ, NOT NULL).
- `published` (BOOLEAN, NOT NULL, DEFAULT FALSE).
- `published_at` (TIMESTAMPTZ, NULL).

## 9. Common Processed Events Schema (`processed_events`)
Deployed in all event-consuming services to guarantee idempotent consumption:
- `event_id` (UUID, PK): ID of the processed Kafka message.
- `event_type` (VARCHAR(100), NOT NULL).
- `consumer_group` (VARCHAR(100), NOT NULL).
- `processed_at` (TIMESTAMPTZ, NOT NULL).
