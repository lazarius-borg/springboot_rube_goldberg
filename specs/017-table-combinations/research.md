# Research & Technical Decisions: Table Combination Management & Capacity Optimization

**Feature**: `017-table-combinations`  
**Date**: 2026-09-18

---

## 1. Table Combination Same-Zone Validation

### Decision
Enforce strictly that all constituent tables in a proposed combination belong to the exact same `zone` (case-insensitive string comparison after trimming).

### Rationale
- Physical proximity is bounded by floor zones (e.g. "Main Dining Room", "Patio", "Bar"). Joining tables across physical walls or floors is physically impossible in restaurant operations.
- Storing the resolved `zone` on the `TableCombinationEntity` (or deriving it directly from member tables during validation and query mapping) allows the UI and reporting to display the zone clearly.

### Alternatives Considered
- *Allowing cross-zone combinations with a manager override*: Rejected because it violates the core domain invariant that combinations represent physically conjoined tables.

---

## 2. Order-Independent Duplicate Combination Detection

### Decision
Compare combinations based on `Set<UUID>` equality of the constituent `table_ids`. Specifically, for a restaurant $R$, a new combination with table IDs $\{T_a, T_b\}$ is considered a duplicate if any existing combination for $R$ has $\{T_x \mid x \in \text{existing.tableIds}\} == \{T_a, T_b\}$.

### Rationale
- Physical combination $[T_1, T_2]$ is identical to $[T_2, T_1]$ on the floor.
- Different combinations with partial overlaps (e.g. $[T_1, T_2]$ and $[T_1, T_3]$) or different sizes (e.g. $[T_1, T_2]$ vs $[T_1, T_2, T_3]$) represent distinct physical groupings and must be permitted.
- In-memory set comparison at the service layer (`new HashSet<>(newIds).equals(new HashSet<>(existingIds))`) is efficient given typical restaurant floor table counts (tens of tables per restaurant).

### Alternatives Considered
- *Database-level unique constraint on sorted array*: While PostgreSQL supports array operators, Hibernate/JPA cross-database testing (H2 slice tests) makes application-level transactional validation cleaner, portable, and allows richer error messages with exact table labels.

---

## 3. Capacity Invariant & Custom Capacity Capping

### Decision
1. **Default Capacity**: If `combinedCapacity` is omitted or null in the creation request, the system sets it to the exact sum of constituent table capacities: $\sum_{t \in T} \text{capacity}(t)$.
2. **Custom Capacity**: If `combinedCapacity` is provided:
   - Must be $\ge 2$ (or at least minimum party size).
   - Must be $\le \sum_{t \in T} \text{capacity}(t)$.
   - If `combinedCapacity > sum`, the request is rejected with `IllegalArgumentException` / HTTP 400.
3. **Total Restaurant Capacity Invariant**: Restaurant capacity is defined strictly as $\sum_{t \in \text{allTables}} \text{capacity}(t)$. Table combinations are logical configurations, not physical seats, so adding/updating/deleting combinations never alters total restaurant capacity.

### Rationale
- Directly fulfills FR-006 and FR-007 and user clarification answers.
- In real restaurants, joining tables can lose seating capacity due to table ends meeting (e.g., two 4-tops joined might seat 6 rather than 8), hence allowing a custom lower capacity. But joining tables can never magically create more chairs than physical tables support.

---

## 4. Deletion Lifecycle & Inter-Service Coherence

### Decision
1. **Immediate Deletion**: Deleting a combination (`DELETE /api/v1/restaurants/{id}/table-combinations/{combinationId}`) removes the record from `restaurant-service` and publishes `TableConfigurationChangedEvent`.
2. **Event Reaction**:
   - `availability-service` consumes `TableConfigurationChangedEvent`, updates its `TableCombinationViewEntity` records, and invalidates Redis availability cache.
   - `reservation-service` already maintains reservations bound to physical `table_id` allocations in `reservation_table_allocation`. Existing reservations are completely unaffected.
3. **Table Deletion / Zone Modification Cascading**:
   - Deleting a physical table deletes all combinations referencing that table (existing behavior in `RestaurantService.java`).
   - Updating a physical table's zone checks if any existing combinations containing that table now span multiple zones. If so, those invalidated combinations are pruned and an updated event is published.

### Rationale
- Completely decoupled and aligns with clarification answer 2: no blocking errors when deleting combinations with future reservations.

---

## 5. Manager Portal UI Integration

### Decision
1. **Tab 3 ("Floor & Tables") Layout**:
   - Add a dedicated "Table Combinations Overview" table below the "Dining Table Inventory" table.
   - Display: Combination Name, Zone, Member Tables (e.g. "T1, T2"), Combined Capacity, and an "Actions" column with a Delete button.
2. **Creation Modal**:
   - In `#addCombinationModal`, display table options with zone indicators.
   - Prevent selecting tables from different zones (disable checkboxes of tables in differing zones once the first table is selected, or validate and display a clear error alert).
   - Dynamically compute default capacity as sum of selected tables, allow user to edit capacity down, and validate client-side before sending POST request.
   - Refresh both table and combination lists on success.
