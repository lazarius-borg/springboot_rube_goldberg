# Component Refactoring & Style Contracts

**Branch**: `009-clean-code-style` | **Date**: 2026-09-15 | **Spec**: [spec.md](../spec.md)

This contract defines the structural and behavioral invariants that all refactored classes must maintain.

---

## 1. Code Style & Import Invariants

1. **No Inline FQCN in Class Bodies**:
   - Unless a class name collision is present within the compilation unit, no type name in method signatures, local variable declarations, or instantiations shall be preceded by its package name (e.g., `com.fasterxml.jackson.databind.JsonNode` -> `JsonNode`).
   - All referenced external classes must be imported using top-level `import` statements.
2. **Import Organization**:
   - Standard Java packages (`java.*`, `javax.*`, `jakarta.*`) followed by third-party packages (`org.springframework.*`, `com.fasterxml.*`), followed by internal project packages (`nl.invokedynamic.*`).
3. **No Wildcard Imports for Ambiguous Types**:
   - Explicit single-class imports should be preferred over wildcards where simple names could become ambiguous.

---

## 2. Service Logic Behavioral Contracts

### Contract A: `AvailabilityService.checkAvailability`
```java
// Method signature:
public AvailabilityResponse checkAvailability(UUID restaurantId, LocalDate date, LocalTime time, int partySize)
```
- **Invariants**:
  - `isAvailable == true` if and only if:
    - At least one active single table of the restaurant with `capacity >= partySize` is NOT present in the set of occupied table IDs for the target timeslot, OR
    - At least one active table combination of the restaurant with `combinedCapacity >= partySize` has ALL of its composite table IDs free from the occupied set.
  - Evaluation MUST short-circuit as soon as availability is confirmed (i.e. if a single table satisfies the request, combination checking is skipped).
  - Throws `IllegalArgumentException` with status 404 when `restaurantId` does not exist in `restaurant_view`.

### Contract B: `WaitingListService.processCancellationOpening`
```java
// Method signature:
public void processCancellationOpening(UUID restaurantId, Instant cancelledStart, int partySize, List<UUID> releasedTableIds)
```
- **Invariants**:
  - Scans waiting entries in chronological FIFO order (`createdAt ASC`).
  - Matches the first entry where `partySize <= releasedCapacity` AND `cancelledStart` falls between `earliestTime` and `latestTime`.
  - Exactly one matching entry is transitioned to `OFFERED` and an outbox event is created.
  - If no entry matches, no state change occurs.

### Contract C: `KeycloakRealmRoleConverter.convert`
```java
// Method signature:
public Collection<GrantedAuthority> convert(Jwt jwt)
```
- **Invariants**:
  - Preserves all default authorities produced by `JwtGrantedAuthoritiesConverter`.
  - Extracts the `roles` list from the `realm_access` claim map.
  - Converts every non-blank String role to a `SimpleGrantedAuthority("ROLE_" + role)`.
  - Ignores non-String, null, or blank role entries gracefully without throwing `ClassCastException`.

### Contract D: `ValidationExceptionHandler.handleHandlerMethodValidation`
```java
// Method signature:
public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(HandlerMethodValidationException ex)
```
- **Invariants**:
  - Returns `HttpStatus.BAD_REQUEST` (`400`).
  - Response type is `https://example.invalid/problems/validation-error`.
  - `invalidParams` property contains `{name: <field-or-param>, reason: <error-message>}` for all resolvable parameter errors.

---

## 3. Public API & Messaging Non-Regression Contract

- **REST Contracts**: No changes to HTTP endpoints, request bodies, query parameters, or response structures.
- **Kafka Contracts**: No changes to topic names, serialization formats, or event payload schemas.
- **Database Contracts**: No changes to Flyway migrations, database schemas, or projection table columns.
