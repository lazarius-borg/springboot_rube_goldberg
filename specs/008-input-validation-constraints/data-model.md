# Data Model & Schema Constraints: Input Values Constraints and Validation

**Feature**: `008-input-validation-constraints` | **Date**: 2026-09-14

## 1. Domain Entities & Database Changes

### 1.1 Reservation Service (`reservation-service`)

#### `ReservationEntity`
| Field | Type | Constraints | Default | Notes |
|-------|------|-------------|---------|-------|
| `id` | `UUID` | Primary Key, Not Null | Generated | Immutable identifier |
| `restaurantId` | `UUID` | Not Null | — | Target restaurant |
| `customerId` | `UUID` | Not Null | — | Booking customer |
| `customerName` | `String` | Not Blank, Max 200 | "Customer" | Customer display name |
| `customerEmail` | `String` | Not Blank, Max 255, Valid Email | — | Confirmation destination |
| `partySize` | `int` | Min 1, Max 50 | 2 | Number of dining guests |
| `startTime` | `Instant` | Not Null | Now | Start timestamp of reservation |
| `endTime` | `Instant` | Not Null | Start + duration | Derived from duration |
| `status` | `String` / `ReservationStatus` | Not Null, Max 30 | `CONFIRMED` | Modeled as `ReservationStatus` |
| `cancellationWindowHours` | `int` | Min 0, Max 168 | 2 | **New Column**: Snapshotted policy |
| `cancellationReason` | `String` | Max 500, Nullable | Null | Reason if cancelled |
| `version` | `Long` | Not Null | 0 | Optimistic locking |
| `createdAt` | `Instant` | Not Null | Now | Audit timestamp |
| `updatedAt` | `Instant` | Not Null | Now | Audit timestamp |

#### Flyway Migration: `V2__add_cancellation_window_hours.sql`
```sql
ALTER TABLE reservation
ADD COLUMN IF NOT EXISTS cancellation_window_hours INT NOT NULL DEFAULT 2;
```

#### Enumeration: `ReservationStatus`
```java
package nl.invokedynamic.demo.reservation.domain;

public enum ReservationStatus {
    CONFIRMED,
    ARRIVED,
    COMPLETED,
    NO_SHOW,
    CANCELLED
}
```

#### State Transition Machine
```text
          ┌─────────────┐
          │  CONFIRMED  │
          └──────┬──────┘
                 │
      ┌──────────┼───────────────┬────────────────┐
      ▼          ▼               ▼                ▼
┌───────────┐ ┌──────────────┐ ┌─────────────┐ ┌─────────────┐
│  ARRIVED  │ │  COMPLETED   │ │   NO_SHOW   │ │  CANCELLED  │
└─────┬─────┘ └──────────────┘ └─────────────┘ └─────────────┘
      │
      ▼
┌───────────┐
│ COMPLETED │
└───────────┘
```

---

### 1.2 Restaurant Service (`restaurant-service`)

#### `RestaurantEntity`
| Field | Type | Constraints | Default | Notes |
|-------|------|-------------|---------|-------|
| `name` | `String` | Not Blank, Max 150 | — | Restaurant trade name |
| `address` | `String` | Not Blank, Max 1000 | — | Physical street address |
| `timezone` | `String` | Valid IANA Zone ID, Max 50 | — | e.g., "America/New_York", "Europe/Amsterdam" |
| `defaultReservationDurationMinutes` | `int` | Min 15, Max 480 | 90 | Table holding duration |
| `minBookingAdvanceMinutes` | `int` | Min 0, Max 10080 | 30 | Minimum lead time for booking (up to 7 days) |
| `maxBookingHorizonDays` | `int` | Min 1, Max 365 | 60 | Maximum forward booking horizon |
| `cancellationWindowHours` | `int` | Min 0, Max 168 | 2 | Minimum cancellation notice hours (up to 7 days) |

#### `RestaurantTableEntity`
| Field | Type | Constraints | Default | Notes |
|-------|------|-------------|---------|-------|
| `tableNumber` | `String` | Not Blank, Max 50 | — | Table identifier/label |
| `capacity` | `int` | Min 1, Max 50 | 2 | Physical seating count |

#### `TableCombinationEntity`
| Field | Type | Constraints | Default | Notes |
|-------|------|-------------|---------|-------|
| `name` | `String` | Not Blank, Max 100 | — | Combination name (e.g. "Party-Combine-1") |
| `tableIds` | `List<UUID>` | Size: Min 2, Max 10 | — | Must contain >= 2 distinct table IDs |

#### `OpeningHoursEntity`
| Field | Type | Constraints | Default | Notes |
|-------|------|-------------|---------|-------|
| `dayOfWeek` | `Integer` | Min 1, Max 7 | — | 1 = Monday, 7 = Sunday |
| `openTime` | `LocalTime` | Nullable if closed | — | Opening time |
| `closeTime` | `LocalTime` | After `openTime` if not closed | — | Closing time |
| `isClosed` | `boolean` | Not Null | false | Day closure flag |

---

### 1.3 Waiting List Service (`waiting-list-service`)

#### `WaitingListEntryEntity`
| Field | Type | Constraints | Default | Notes |
|-------|------|-------------|---------|-------|
| `restaurantId` | `UUID` | Not Null | — | Target restaurant |
| `customerId` | `UUID` | Not Null | — | Customer queue owner |
| `customerEmail` | `String` | Not Blank, Max 255, Valid Email | — | Notification recipient |
| `targetDate` | `LocalDate` | Today or Future, Max Today + 365 | — | Dining date |
| `earliestTime` | `LocalTime` | Not Null, <= `latestTime` | — | Window start |
| `latestTime` | `LocalTime` | Not Null, >= `earliestTime` | — | Window end |
| `partySize` | `int` | Min 1, Max 50 | — | Party seating requirement |

---

### 1.4 Customer Service (`customer-service`)

#### `CustomerProfileEntity`
| Field | Type | Constraints | Default | Notes |
|-------|------|-------------|---------|-------|
| `firstName` | `String` | Not Blank, Max 50 | — | Given name |
| `lastName` | `String` | Not Blank, Max 50 | — | Family name |
| `phoneNumber` | `String` | Size: 5 to 25, Pattern `^[+0-9() -]+$` | — | Contact telephone |

---

## 2. DTO & Record Constraint Definitions

### 2.1 `reservation-service`
```java
public record CreateReservationRequest(
        @NotNull(message = "Restaurant ID is required")
        UUID restaurantId,

        UUID customerId,

        @Size(max = 200, message = "Customer name cannot exceed 200 characters")
        String customerName,

        @Email(message = "Customer email must be a valid email address")
        @Size(max = 255, message = "Customer email cannot exceed 255 characters")
        String customerEmail,

        @Min(value = 1, message = "Party size must be at least 1 guest")
        @Max(value = 50, message = "Party size cannot exceed 50 guests")
        Integer partySize,

        Instant startTime,

        @Min(value = 15, message = "Duration must be at least 15 minutes")
        @Max(value = 480, message = "Duration cannot exceed 480 minutes (8 hours)")
        Integer durationMinutes,

        @Min(value = 0, message = "Cancellation window hours cannot be negative")
        @Max(value = 168, message = "Cancellation window hours cannot exceed 168 hours (7 days)")
        Integer cancellationWindowHours,

        List<TableAllocationEngine.TableCandidate> availableTables,
        List<TableAllocationEngine.CombinationCandidate> combinations
) {}

public record UpdateStatusRequest(
        @NotNull(message = "Status is required")
        ReservationStatus status
) {}
```

### 2.2 `restaurant-service`
```java
public record CreateRestaurantRequest(
        @NotBlank(message = "Restaurant name is required")
        @Size(max = 150, message = "Restaurant name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Address is required")
        @Size(max = 1000, message = "Address cannot exceed 1000 characters")
        String address,

        @NotBlank(message = "Timezone is required")
        @Size(max = 50, message = "Timezone cannot exceed 50 characters")
        String timezone,

        @Min(value = 15, message = "Default reservation duration must be at least 15 minutes")
        @Max(value = 480, message = "Default reservation duration cannot exceed 480 minutes")
        Integer defaultReservationDurationMinutes,

        @Min(value = 0, message = "Minimum booking advance minutes cannot be negative")
        @Max(value = 10080, message = "Minimum booking advance cannot exceed 10080 minutes (7 days)")
        Integer minBookingAdvanceMinutes,

        @Min(value = 1, message = "Maximum booking horizon must be at least 1 day")
        @Max(value = 365, message = "Maximum booking horizon cannot exceed 365 days")
        Integer maxBookingHorizonDays,

        @Min(value = 0, message = "Cancellation window must be at least 0 hours")
        @Max(value = 168, message = "Cancellation window cannot exceed 168 hours")
        Integer cancellationWindowHours
) {}

public record CreateTableRequest(
        @NotBlank(message = "Table number is required")
        @Size(max = 50, message = "Table number cannot exceed 50 characters")
        String tableNumber,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1 seat")
        @Max(value = 50, message = "Capacity cannot exceed 50 seats")
        Integer capacity
) {}

public record CreateCombinationRequest(
        @NotBlank(message = "Combination name is required")
        @Size(max = 100, message = "Combination name cannot exceed 100 characters")
        String name,

        @NotNull(message = "Table IDs list is required")
        @Size(min = 2, max = 10, message = "Combination must contain between 2 and 10 tables")
        List<UUID> tableIds
) {}
```

### 2.3 `waiting-list-service`
```java
public record JoinWaitingListRequest(
        @NotNull(message = "Restaurant ID is required")
        UUID restaurantId,

        UUID customerId,

        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be valid")
        @Size(max = 255, message = "Customer email cannot exceed 255 characters")
        String customerEmail,

        @NotNull(message = "Target date is required")
        LocalDate targetDate,

        @NotNull(message = "Earliest time is required")
        LocalTime earliestTime,

        @NotNull(message = "Latest time is required")
        LocalTime latestTime,

        @Min(value = 1, message = "Party size must be at least 1 guest")
        @Max(value = 50, message = "Party size cannot exceed 50 guests")
        int partySize
) {}
```

### 2.4 `customer-service`
```java
public record UpdateCustomerRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name cannot exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name cannot exceed 50 characters")
        String lastName,

        @Size(min = 5, max = 25, message = "Phone number must be between 5 and 25 characters")
        @Pattern(regexp = "^[+0-9() -]+$", message = "Phone number format is invalid")
        String phoneNumber
) {}
```
