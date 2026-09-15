# Phase 0 Research: Code Style Refactoring - FQCN Elimination and Stream API Modernization

**Branch**: `009-clean-code-style` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

## Summary of Findings

This research establishes standard patterns and concrete refactoring targets for eliminating inline Fully Qualified Class Names (FQCNs) and modernizing imperative loop-and-conditional structures into idiomatic Java Stream API pipelines across the multi-module Spring Boot Rube Goldberg codebase.

---

## 1. Fully Qualified Class Names (FQCN) Analysis

### Problem Statement
Class bodies throughout the service layer and test suites occasionally reference types using their fully qualified package names (e.g., `com.fasterxml.jackson.databind.JsonNode`, `java.util.List.of`, `java.util.Map.of`). This introduces syntactic clutter and violates clean code conventions.

### Identified Occurrences
1. **Event Listeners** (`com.fasterxml.jackson.databind.JsonNode`):
   - `services/availability-service/.../AvailabilityEventListener.java`
   - `services/notification-service/.../NotificationEventListener.java`
   - `services/waiting-list-service/.../ReservationCancelledListener.java`
   - `services/analytics-service/.../AnalyticsEventListener.java`
2. **Security Test Suites** (`java.util.List.of`, `java.util.Map.of`):
   - `services/restaurant-service/.../RestaurantSecurityTest.java`
   - `services/reservation-service/.../ReservationSecurityTest.java`
   - `services/customer-service/.../CustomerSecurityTest.java`
   - `services/availability-service/.../AvailabilitySecurityTest.java`
   - `services/waiting-list-service/.../WaitingListSecurityTest.java`
   - `services/analytics-service/.../AnalyticsSecurityTest.java`
3. **Repository & Config References**:
   - Any remaining inline package-qualified classes in configuration classes or controller methods.

### Decision & Disambiguation Rules
- **Rule 1 (Import Optimization)**: Whenever a type is referenced in a class body and has no name collision within the file, replace the inline FQCN with its simple name and add a top-level `import` declaration.
- **Rule 2 (Name Collision Preservation)**: If two classes sharing the identical simple name are required in the same file, the primary domain class receives the `import` statement, and the secondary/auxiliary class retains explicit FQCN at usage points. In this codebase, no genuine collisions exist for the targeted types (`JsonNode`, `List`, `Map`), so all can be cleanly imported.
- **Rule 3 (Static Factory Imports)**: For repetitive `List.of` and `Map.of` in tests, standard `import java.util.List;` and `import java.util.Map;` will be added.

---

## 2. Java Stream API Modernization Candidates

### Candidate A: Table & Combination Availability Evaluation (`AvailabilityService.java`)
- **Current Implementation**:
  - Collects occupied table IDs using an imperative `for` loop over `SlotOccupancyViewEntity`.
  - Searches for available single tables using an imperative loop with a boolean flag and `break`.
  - If unavailable, searches table combinations with a nested loop and conditional `break`.
- **Stream Refactoring**:
  ```java
  // 1. Occupied Table Set
  Set<UUID> occupiedTableIds = occupancies.stream()
          .map(SlotOccupancyViewEntity::getTableId)
          .collect(Collectors.toSet());

  // 2. Single Table Availability
  boolean singleTableAvailable = tables.stream()
          .anyMatch(t -> !occupiedTableIds.contains(t.getId()) && t.getCapacity() >= partySize);

  // 3. Table Combination Availability (fallback)
  boolean combinationAvailable = !singleTableAvailable && combinations.stream()
          .filter(c -> c.getCombinedCapacity() >= partySize)
          .anyMatch(c -> c.getTableIds().stream().noneMatch(occupiedTableIds::contains));

  boolean isAvailable = singleTableAvailable || combinationAvailable;
  ```
- **Rationale**: Eliminates mutable boolean flags, manual loop breaks, and separate nested loops. Uses short-circuiting (`anyMatch()`) and declarative predicates matching the business rule.

### Candidate B: FIFO Waiting List Candidate Matching (`WaitingListService.java`)
- **Current Implementation**:
  - Evaluates `waiting` entries in FIFO order using `for (WaitingListEntryEntity entry : waiting)` with nested `if` condition and a `break` after processing the first eligible match.
- **Stream Refactoring**:
  ```java
  waiting.stream()
          .filter(entry -> entry.getPartySize() <= partySize
                  && !time.isBefore(entry.getEarliestTime())
                  && !time.isAfter(entry.getLatestTime()))
          .findFirst()
          .ifPresent(entry -> createAndPublishOffer(entry, restaurantId, cancelledStart, releasedTableIds));
  ```
- **Rationale**: Replaces manual loop breaking with idiomatic `.findFirst().ifPresent(...)`, encapsulating offer creation into a clean helper method and improving readability.

### Candidate C: Keycloak Realm Role Conversion (`KeycloakRealmRoleConverter.java`)
- **Current Implementation**:
  - Nested `if (rolesObj instanceof List<?> roles)` containing `for (Object role : roles)` with nested `if (role instanceof String roleName && !roleName.isBlank())`.
- **Stream Refactoring**:
  ```java
  if (rolesObj instanceof List<?> roles) {
      roles.stream()
              .filter(String.class::isInstance)
              .map(String.class::cast)
              .filter(roleName -> !roleName.isBlank())
              .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
              .forEach(authorities::add);
  }
  ```
- **Rationale**: Eliminates nested indentation across all 6 microservice security modules and expresses type filtering and role transformation fluently.

### Candidate D: Validation Error Extraction (`ValidationExceptionHandler.java`)
- **Current Implementation**:
  - Nested `forEach` callbacks accumulating errors into a mutable `List<Map<String, String>> invalidParams`.
- **Stream Refactoring**:
  ```java
  List<Map<String, String>> invalidParams = ex.getParameterValidationResults().stream()
          .flatMap(result -> {
              String paramName = result.getMethodParameter().getParameterName();
              return result.getResolvableErrors().stream()
                      .map(err -> Map.of(
                              "name", err instanceof FieldError fe ? fe.getField() : Objects.requireNonNullElse(paramName, "parameter"),
                              "reason", Objects.requireNonNullElse(err.getDefaultMessage(), "Invalid parameter value")
                      ));
          })
          .toList();
  ```
- **Rationale**: Produces an immutable list via `flatMap` pipeline, removing mutable collection state.

### Candidate E: Batch Entity Persistence in Event Listeners (`AvailabilityEventListener.java`)
- **Current Implementation**:
  - Multiple individual `repository.save(...)` calls inside `for` loops.
- **Stream Refactoring**:
  - Transform items via `.stream().map(...).toList()` and call `repository.saveAll(...)` for tables, combinations, and slot occupancies.
- **Rationale**: Leverages batch operations provided by Spring Data JPA while expressing entity mapping cleanly.

### Candidate F: Sequential Outbox Publishing Loops (`RestaurantEventPublisher`, `ScheduledOutboxPoller`)
- **Decision**: Keep the imperative `for` loop with try-catch and `break`.
- **Rationale**: The outbox poller requires strict FIFO sequential ordering and must halt (`break`) immediately when a Kafka dispatch fails so subsequent events are not prematurely sent out of order. Streams are not suitable for stateful sequential error break loops.

---

## 3. Verification Strategy

- **Static Inspection**: Verify 0 inline FQCNs remain for `JsonNode`, `List`, `Map`.
- **Automated Regression Testing**: Execute `./mvnw test` across all 10 modules to confirm 100% pass rate.
