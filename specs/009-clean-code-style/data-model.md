# Phase 1 Data Model & Transformation Schema: Code Style Refactoring

**Branch**: `009-clean-code-style` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

## Component Transformation Mapping

This document describes the architectural entities, target source files, and before/after transformation models for the code style refactoring.

---

### 1. FQCN to Simple Name Import Model

| Target File | Package | FQCN Usage (Before) | Replacement (After) | Required Top-Level Import |
| :--- | :--- | :--- | :--- | :--- |
| `AvailabilityEventListener.java` | `nl.invokedynamic.demo.availability.events` | `com.fasterxml.jackson.databind.JsonNode` | `JsonNode` | `import com.fasterxml.jackson.databind.JsonNode;` |
| `NotificationEventListener.java` | `nl.invokedynamic.demo.notification.events` | `com.fasterxml.jackson.databind.JsonNode` | `JsonNode` | `import com.fasterxml.jackson.databind.JsonNode;` |
| `ReservationCancelledListener.java`| `nl.invokedynamic.demo.waitinglist.events` | `com.fasterxml.jackson.databind.JsonNode` | `JsonNode` | `import com.fasterxml.jackson.databind.JsonNode;` |
| `AnalyticsEventListener.java` | `nl.invokedynamic.demo.analytics.events` | `com.fasterxml.jackson.databind.JsonNode` | `JsonNode` | `import com.fasterxml.jackson.databind.JsonNode;` |
| `RestaurantSecurityTest.java` | `nl.invokedynamic.demo.restaurant` | `java.util.List.of`, `java.util.Map.of` | `List.of`, `Map.of` | `import java.util.List;`, `import java.util.Map;` |
| `ReservationSecurityTest.java` | `nl.invokedynamic.demo.reservation` | `java.util.List.of`, `java.util.Map.of` | `List.of`, `Map.of` | `import java.util.List;`, `import java.util.Map;` |
| `CustomerSecurityTest.java` | `nl.invokedynamic.demo.customer` | `java.util.List.of`, `java.util.Map.of` | `List.of`, `Map.of` | `import java.util.List;`, `import java.util.Map;` |
| `AvailabilitySecurityTest.java` | `nl.invokedynamic.demo.availability` | `java.util.List.of`, `java.util.Map.of` | `List.of`, `Map.of` | `import java.util.List;`, `import java.util.Map;` |
| `WaitingListSecurityTest.java` | `nl.invokedynamic.demo.waitinglist` | `java.util.List.of`, `java.util.Map.of` | `List.of`, `Map.of` | `import java.util.List;`, `import java.util.Map;` |
| `AnalyticsSecurityTest.java` | `nl.invokedynamic.demo.analytics` | `java.util.List.of`, `java.util.Map.of` | `List.of`, `Map.of` | `import java.util.List;`, `import java.util.Map;` |

---

### 2. Stream API Transformation Model

#### Entity 1: Availability Check Algorithm (`AvailabilityService.java`)
- **Method**: `AvailabilityResponse checkAvailability(UUID restaurantId, LocalDate date, LocalTime time, int partySize)`
- **Input Entities**:
  - `List<SlotOccupancyViewEntity> occupancies`
  - `List<TableInventoryViewEntity> tables`
  - `List<TableCombinationViewEntity> combinations`
- **Output**:
  - `Set<UUID> occupiedTableIds` (derived via Stream mapping)
  - `boolean available` (derived via short-circuiting Stream predicates `anyMatch()` / `noneMatch()`)
- **State Invariants**: The resulting boolean availability and available slots list must match the existing imperative implementation for all permutations of table/combination capacity and occupancy.

#### Entity 2: FIFO Candidate Allocation (`WaitingListService.java`)
- **Method**: `void processCancellationOpening(UUID restaurantId, Instant cancelledStart, int partySize, List<UUID> releasedTableIds)`
- **Input Entities**:
  - `List<WaitingListEntryEntity> waiting` (pre-sorted chronologically by `createdAt ASC`)
- **Stream Operations**:
  - Predicate: `partySize <= candidate.partySize` AND `time in [earliestTime, latestTime]`
  - Short-circuit: `findFirst()`
  - Consumer: `ifPresent(this::offerTableToEntry)`
- **State Invariants**: At most one candidate (the earliest created matching entry) is transitioned to `OFFERED` status, preserving strict FIFO ordering.

#### Entity 3: Keycloak Security Authority Converter (`KeycloakRealmRoleConverter.java`)
- **Target Services**: `customer-service`, `restaurant-service`, `reservation-service`, `availability-service`, `waiting-list-service`, `analytics-service`
- **Method**: `Collection<GrantedAuthority> convert(Jwt jwt)`
- **Input**:
  - `Map<String, Object> realmAccess` -> `List<?> roles`
- **Stream Pipeline**:
  - `roles.stream().filter(String.class::isInstance).map(String.class::cast).filter(not(String::isBlank)).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).forEach(authorities::add)`
- **State Invariants**: Output `Collection<GrantedAuthority>` contains identical authorities as the imperative nested conditional loop.

#### Entity 4: Parameter Validation Exception Handler (`ValidationExceptionHandler.java`)
- **Target Services**: `customer-service`, `restaurant-service`, `reservation-service`, `availability-service`, `waiting-list-service`
- **Method**: `ResponseEntity<ProblemDetail> handleHandlerMethodValidation(HandlerMethodValidationException ex)`
- **Input**:
  - `List<ParameterValidationResult> parameterValidationResults`
- **Stream Pipeline**:
  - `flatMap` over `result.getResolvableErrors()` mapping each error to `{name, reason}` map
- **State Invariants**: The generated `invalidParams` list structure is identical in format and error ordering.

#### Entity 5: Batch Persistence in Event Consumers (`AvailabilityEventListener.java`)
- **Method**: `void handleRestaurantEvent(String message)`, `void handleReservationEvent(String message)`
- **Stream Transformations**:
  - `event.tables().stream().map(TableInventoryViewEntity::new).toList()` -> `tableRepository.saveAll(...)`
  - `event.combinations().stream().map(TableCombinationViewEntity::new).toList()` -> `combinationRepository.saveAll(...)`
  - `event.allocatedTableIds().stream().map(SlotOccupancyViewEntity::new).toList()` -> `occupancyRepository.saveAll(...)`
- **State Invariants**: Database projection views (`table_inventory_view`, `table_combination_view`, `slot_occupancy_view`) receive identical rows.
