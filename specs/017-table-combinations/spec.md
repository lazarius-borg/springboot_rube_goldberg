# Feature Specification: Table Combination Management & Capacity Optimization

**Feature Branch**: `017-table-combinations`

**Created**: 2026-09-18

**Status**: Draft

**Input**: User description: "table combination - In the manager portal the user can combine tables to achieve higher table capacity and offer the combination when checking availability and making reservations. - there should be overview of combined tables - probably only tables in the same zone should be possible for combining - the system should recognize if same tables are already combined, duplicate combination should be prevented; for example T1+T2 can appear only once and another T1+T2 is not allowed, but T1+T2 and T1+T3 is - when calculating the total restaurant capacity for the selected time slot, it should be considered if a combination or individual tables are reserved - adding combination of tables, can never increase the total capacity of the restaurant"

## Clarifications

### Session 2026-09-18

- Q: When a manager creates a table combination, should the system always calculate its capacity automatically as the exact sum of constituent tables, or allow setting a custom capacity up to that sum? → A: Default to the exact sum of member table capacities, with an optional field to set a lower capacity (capped at the sum).
- Q: When a restaurant manager deletes an existing table combination, how should the system handle upcoming reservations that were already booked using that combination? → A: Immediate deletion: unpublishes the combination from future availability offers, while keeping confirmed upcoming reservations and their assigned physical tables unaffected.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and Manage Same-Zone Table Combinations (Priority: P1)

As a restaurant manager,  
I want to group two or more physical dining tables located within the same floor zone into a named table combination,  
so that larger parties can be accommodated at joined tables while ensuring physical seating proximity and preventing redundant combination configurations.

**Why this priority**: Fundamental administrative capability required to define viable large-party seating arrangements and prevent configuration errors such as cross-room pairings or duplicate definitions.

**Independent Test**: Can be tested independently by creating a combination of two tables in the same zone (e.g., Table 1 + Table 2 in "Main Dining"), verifying it is created successfully, attempting to create the same combination again and verifying it is rejected as a duplicate, and attempting to combine tables from different zones and verifying it is rejected.

**Acceptance Scenarios**:

1. **Given** two or more active tables belonging to the same zone (e.g., Table 1 and Table 2 in "Main Dining"), **When** the manager creates a combination specifying these tables without providing a custom capacity, **Then** the combination is created with a capacity equal to the exact sum of the constituent tables.
2. **Given** tables located in different zones (e.g., Table 1 in "Main Dining" and Table 4 in "Patio"), **When** the manager attempts to create a combination using these tables, **Then** the request is rejected with a validation error indicating that combined tables must reside in the same floor zone.
3. **Given** an existing combination consisting of Table 1 and Table 2, **When** a manager attempts to create another combination consisting of Table 1 and Table 2 (regardless of selection order, e.g., Table 2 and Table 1), **Then** the request is rejected with a validation error indicating that this exact combination already exists.
4. **Given** an existing combination of Table 1 and Table 2, **When** a manager creates a new combination consisting of Table 1 and Table 3 in the same zone, **Then** the new combination is successfully created.
5. **Given** a combination creation request where the manager specifies an optional custom capacity, **When** the request is submitted, **Then** the system accepts the custom capacity if it is less than or equal to the sum of the constituent tables, and rejects the request if the custom capacity exceeds the sum.

---

### User Story 2 - Table Combination Overview and Lifecycle Management (Priority: P2)

As a restaurant manager,  
I want a clear, dedicated overview of all configured table combinations in the restaurant management portal,  
so that I can review which tables can be joined together, inspect their combined capacity and zone, and safely remove combinations that are no longer supported.

**Why this priority**: Visibility and management of existing combinations prevents operational confusion on the dining floor and allows updating seating layouts as floor plans change.

**Independent Test**: Can be tested by navigating to the manager portal's restaurant configuration view, verifying all existing combinations are listed with their member tables, combined capacity, and zone, and deleting an unused combination.

**Acceptance Scenarios**:

1. **Given** a restaurant with configured table combinations, **When** the manager views the table management section, **Then** a dedicated table combinations overview displays each combination's name, zone, member table numbers/identifiers, and total combined capacity.
2. **Given** a configured table combination, **When** the manager deletes the combination, **Then** the combination is immediately removed from the catalog and is no longer offered for future availability searches or bookings.
3. **Given** active upcoming reservations that were booked using a table combination, **When** the manager deletes that combination, **Then** the confirmed reservations remain intact with their assigned physical tables unaffected, and the combination is unpublished from future bookings.

---

### User Story 3 - Coordinated Availability and Timeslot Capacity Calculation (Priority: P3)

As a dining guest or reservation coordinator,  
I want table combinations to be offered automatically when searching for larger party sizes, and I want the system to strictly account for mutual exclusions between individual tables and combinations during capacity calculation,  
so that booking a combination reserves all of its physical tables, booking an individual table makes overlapping combinations unavailable, and total restaurant physical capacity is never exceeded or artificially inflated.

**Why this priority**: Ensures core booking integrity, accurate real-time availability, and prevents double-booking between constituent tables and combinations.

**Independent Test**: Can be tested by creating a combination of Table 1 (cap 2) and Table 2 (cap 4) for a total combo capacity of 6 in a restaurant with total physical capacity 10. Reserve the combination for a timeslot, and verify that Table 1 and Table 2 are both occupied, timeslot available capacity decreases by 6, and neither Table 1 nor Table 2 can be booked separately for that timeslot.

**Acceptance Scenarios**:

1. **Given** a combination of Table 1 and Table 2, **When** an availability check or reservation is made for a party requiring this combination, **Then** the combination is allocated, marking both Table 1 and Table 2 as occupied for that timeslot.
2. **Given** Table 1 is already reserved for a given timeslot, **When** an availability check or reservation is requested for a party that would require the combination of Table 1 and Table 2, **Then** the combination is treated as unavailable for that timeslot because Table 1 is occupied.
3. **Given** a restaurant with a set of physical tables having a combined total capacity of $N$, **When** table combinations are created, **Then** the restaurant's total capacity for any timeslot remains exactly $N$ and never increases.
4. **Given** overlapping reservations during a timeslot where some guests reserve individual tables and others reserve combinations, **When** total occupied capacity is calculated, **Then** each physical table is counted at most once toward occupied seats, ensuring accurate timeslot capacity headroom.

---

### Edge Cases

- **Single Table Selection**: A combination requires at least two physical tables; attempting to create a combination with fewer than 2 tables must be rejected.
- **Order-Insensitive Duplicate Matching**: Combinations `[T1, T2]` and `[T2, T1]` represent the exact same physical configuration and must be recognized as identical.
- **Subset vs. Exact Match**: Combinations `[T1, T2]` and `[T1, T2, T3]` are distinct valid configurations (a 2-table combo vs a 3-table combo) and must both be allowed provided all member tables are in the same zone.
- **Inactive or Deleted Member Table**: If a physical table is deactivated or deleted, any combinations containing that table must be automatically invalidated or cascade-removed to prevent dangling table references.
- **Zone Reassignment of Member Table**: If a physical table's zone is updated such that it no longer matches the rest of a combination's tables, the system must either prevent the zone change or flag/invalidate the combination.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow restaurant managers to view an overview of all table combinations defined for a selected restaurant, including combination name, zone, member tables, and combined capacity.
- **FR-002**: The system MUST allow restaurant managers to create a new table combination by selecting two or more physical tables that belong to the same restaurant.
- **FR-003**: The system MUST enforce that all physical tables in a proposed combination belong to the exact same floor zone; combinations spanning multiple zones MUST be rejected.
- **FR-004**: The system MUST detect duplicate combinations by comparing the set of constituent table identifiers regardless of selection order; identical sets of tables for the same restaurant MUST be rejected.
- **FR-005**: The system MUST allow partial overlaps across distinct combinations (e.g., Table 1 + Table 2 and Table 1 + Table 3 are both permitted simultaneously).
- **FR-006**: The system MUST enforce that creating or modifying a table combination NEVER increases the total physical capacity of the restaurant. The total capacity of a restaurant MUST remain strictly the sum of all distinct physical tables.
- **FR-007**: When creating or configuring a table combination, the system MUST default its capacity to the exact sum of the capacities of its constituent physical tables. The manager MAY optionally specify a custom capacity lower than this sum, but the system MUST strictly reject any capacity configuration that exceeds the sum of the constituent table capacities.
- **FR-008**: The system MUST allow deleting a table combination at any time, immediately removing it from future availability calculations and booking offers; any existing confirmed reservations previously booked under that combination MUST retain their assigned physical tables without disruption.
- **FR-009**: When checking availability or booking a table combination for a timeslot, the system MUST consider all constituent physical tables as occupied for that duration.
- **FR-010**: When any constituent physical table of a combination is reserved individually for a timeslot, the combination MUST be considered unavailable for that timeslot.
- **FR-011**: The system MUST accurately calculate timeslot capacity headroom by counting each physical table's guest allocation without double-counting between combinations and member tables.

---

### Key Entities

- **Restaurant Table**: A physical dining unit with an identifier, table label/number, seating capacity, and floor zone.
- **Table Combination**: A logical grouping of two or more physical tables within the same floor zone of a restaurant, having a name, an ordered or set-based list of physical table references, and a combined seating capacity that does not exceed the sum of its member tables.
- **Floor Zone**: A designated physical area within a restaurant (e.g., "Main Dining", "Patio", "Bar", "Upstairs") that bounds physical table proximity.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Managers can create, view, and delete valid table combinations in the manager portal in under 30 seconds.
- **SC-002**: 100% of attempts to create duplicate table combinations (same tables in different order) are rejected with clear user guidance.
- **SC-003**: 100% of attempts to combine tables across different zones are rejected with clear user guidance.
- **SC-004**: Total restaurant capacity metrics remain invariant regardless of the number of table combinations defined.
- **SC-005**: Zero instances of overlapping bookings between a table combination and any of its constituent physical tables for any given dining timeslot.

---

## Assumptions

- A table combination requires a minimum of 2 physical tables and may combine up to the total number of tables in a single zone.
- Tables in the same zone are physically adjacent or reconfigurable by floor staff to accommodate a single combined dining party.
- If a constituent table is deleted or deactivated, associated combinations are safely decommissioned or flagged so that incomplete combinations cannot be offered.
- Customer booking queries continue to automatically select combinations when a party size exceeds the capacity of individual tables.
