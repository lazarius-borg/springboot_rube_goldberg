# Feature Specification: Platform Functionality and Usability Fixes

**Feature Branch**: `016-platform-functionality-fixes`

**Created**: 2026-09-17

**Status**: Draft

**Input**: User description: "functionality fixes - There are some functions that not working as expected or are counterintuitive that need to be addressed.
- as a manager, the user can add a table and is presented by a dialog that has 3 input fields: 'Table Number / Label', 'Seating Capacity' and 'Floor Zone / Location'. The POST request sent to 'api/v1/restaurants/{id}/tables' endpoint has the following body'{\"tableNumber\":\"T6\",\"capacity\":4,\"zone\":\"Rooftop\"}' for example. It appears that the endpoint should be updated to include 'zone' field in the 'CreateTableRequest'.
- as a manager, the user can choose to create table combinations. However, any request always results in '400 Bad Request'. It appears that the 'CreateCombinationRequest' request has 'name' and 'tableIds' properties, while the request doesn't match that signature; Here is an example request: `{\"tableIds\":[\"307325bf-b6d9-4ac2-9e28-9f7965e67689\",\"532ba9fe-9d1a-42dc-a51b-b0c0d518e437\"],\"combinedCapacity\":8}`. The request should include the 'name' field' and the 'CreateCombinationRequest' should accept the 'combinedCapacity' field, or not send the combinedCapacity, but calculate the capcity from the capacities of the combined tables.
- some restaurants are closed on certain days; as a manager, the user should be able to choose a day as closed, besides the option to choose opening hours that is now available.
- as a manager, the user has an overview of the reservations; there is 'TABLES' column in the UI, but it is always empty, it should display the intended information; for canceled reservations, the user should be able to see the reason.
- as a manager, the user should be able to edit and remove tables.
- as a customer, the user joined a waiting list; even after all reservations were cancelled, the user was still waiting, instead of being given an offer, most likely because the request was made for a party size greater than the max table capacity, but this is only an assumption.
- as a customer, the user should be able to see party size in the waiting list, just at it is visible in the upcoming reservations.
- as a customer, when the user tries to make a reservation for more people than the max table availalbe, the user is informed that the restaurant is fully booked and offered to join the waiting list; the waiting would be futile, since an offer will never happen; the user should be informed about the reason, and either make reservations for multiple tables to fit the number of guests if that is possible, or if not only then join the waiting list; the problem here might be that the system doesn't allow for a reservation that has multiple tebles, unless the tables are combined in the system by the manager.
- as a customer, the user can make reservations at the same time that exceed the total restaurant capacity, as long as the number of guests are less or equal than the largest table capacity; it looks as if the only constraint is the maximum table capacity, not the total restaurant capacity in the given timeslot.
- as a customer, the user should be able to choose and see the duration of the reservation; in any case, when making a reservation, the default and maximum reservation duration should be visible.
- as a manager, the user should be able to set the maximum dining duration.
- as a manager, the user should be able to edit the settings for an establishment, not only the opening hours, like default duration, max duration and so on; ANy changes to the establishment settings will not affect existing reservations."

## Clarifications

### Session 2026-09-17

- Q: How should the platform handle customer reservation requests when party size exceeds the largest single table but could be accommodated by combining tables? → A: Allow customer to select and reserve multiple distinct tables under a single reservation checkout.
- Q: How should customers select their dining duration in the customer booking interface? → A: Dropdown selector with 15-minute increments between the minimum duration and the restaurant's maximum duration, defaulting to the establishment's default duration.
- Q: How should table combination names and combined capacities be handled when creating a combination in the manager portal? → A: UI provides Name (auto-defaulted e.g. "Combo: T1 + T2") and Capacity (auto-calculated sum, editable); backend accepts optional name and optional combinedCapacity, applying calculated defaults if omitted.
- Q: How should the waiting list engine evaluate candidate eligibility when a reservation is cancelled? → A: Check live table availability for waiting candidates in FIFO order; extend an offer to the first candidate whose party size can be accommodated by available tables.
- Q: How should the operating schedule interface allow managers to mark days as closed, and how should closures interact with existing bookings? → A: Add a "Closed" toggle/checkbox per day that disables open/close time pickers and persists isClosed: true, blocking new bookings while preserving existing confirmed reservations.

## User Scenarios & Testing

### User Story 1 - Table Management & Flexible Combinations (Priority: P1)

Restaurant managers need to accurately configure, modify, and combine tables to match their physical dining room floor plan, including zones, seating capacities, and custom combinations.

**Why this priority**: Correct table inventory modeling is foundational for all availability calculations, seating allocations, and reservation bookings.

**Independent Test**: A manager creates tables with location zones, updates table capacities, combines two or more tables with a name and aggregate capacity, and deletes an unused table without error.

**Acceptance Scenarios**:

1. **Given** a manager configuring a restaurant, **When** they add a table specifying Table Number/Label (e.g., "T6"), Seating Capacity (4), and Floor Zone/Location (e.g., "Rooftop"), **Then** the table is saved with the zone attribute preserved and displayed in the table inventory list.
2. **Given** two or more existing tables in the inventory, **When** the manager selects them to form a table combination, **Then** the UI auto-populates a default combination name (e.g., "Combo: T1 + T2") and calculates the combined capacity from the selected tables with an option to edit both, and upon submission the combination is successfully created without errors and becomes available for seating larger parties.
3. **Given** an existing table in inventory with no active future reservations, **When** the manager edits its label, capacity, or zone, or chooses to delete it, **Then** the changes are persisted and the inventory updates immediately.
4. **Given** an existing table with assigned upcoming confirmed reservations, **When** the manager attempts to delete the table, **Then** the system rejects the deletion and informs the manager that active reservations are assigned to that table.

---

### User Story 2 - Capacity Constraints & Seating Guidance (Priority: P1)

Customers searching for dining slots must be constrained by the actual total capacity of the restaurant and physical table configurations, and receive transparent guidance and multi-table options when party sizes exceed single table limits.

**Why this priority**: Prevents severe overbooking where concurrent reservations exceed the restaurant's total physical capacity, and empowers customers to book multi-table arrangements while preventing futile waiting list entries.

**Independent Test**: Attempting to book reservations that collectively exceed the total restaurant seating capacity in an overlapping timeslot is blocked. Requesting a party size larger than any single table or configured combination allows selecting multiple distinct tables under a single checkout, or provides clear guidance if total capacity is exceeded.

**Acceptance Scenarios**:

1. **Given** a restaurant with a total physical capacity of 20 seats across all tables, **When** multiple customers book reservations for the same timeslot that collectively exceed 20 seated guests, **Then** reservations beyond the total capacity are rejected as unavailable.
2. **Given** a restaurant where the largest single table seats 6 and the largest combinable arrangement seats 8, **When** a customer searches for a party of 12, **Then** the system allows the customer to select and reserve multiple distinct tables under a single reservation checkout if sufficient total capacity and tables exist, or clearly explains if total restaurant capacity is insufficient.
3. **Given** a customer viewing availability for an unseatable party size exceeding total available restaurant capacity, **When** availability is returned as false, **Then** the system does not prompt the customer to join a waiting list that can never be fulfilled.

---

### User Story 3 - Configurable Dining Duration & Customer Selection (Priority: P2)

Managers need to define default and maximum dining durations for their restaurant, and customers need the flexibility to select their intended dining duration up to the established limit.

**Why this priority**: Optimizes table turnover, clarifies guest expectations, and prevents table conflicts across different dining experiences.

**Independent Test**: A manager configures default dining duration (e.g., 90 minutes) and maximum dining duration (e.g., 150 minutes). A customer making a reservation sees these limits and can choose their desired duration within the allowed bounds.

**Acceptance Scenarios**:

1. **Given** a restaurant with a default duration of 90 minutes and a maximum duration of 120 minutes, **When** a customer initiates a reservation booking, **Then** the default and maximum durations are prominently displayed and duration is selectable via a dropdown menu with 15-minute increments defaulting to 90 minutes.
2. **Given** a customer booking a table, **When** they select an allowable duration (e.g., 120 minutes), **Then** the reservation is reserved for that full duration and the table is blocked from overlapping bookings until the end time.
3. **Given** a customer attempting to enter a duration exceeding the restaurant's configured maximum duration, **When** the reservation request is submitted, **Then** the system restricts the selection to the maximum allowed limit.

---

### User Story 4 - Restaurant Operating Schedule & Closed Days (Priority: P2)

Managers must be able to designate specific days of the week or individual dates as closed, and update general establishment settings without altering existing confirmed bookings.

**Why this priority**: Real-world dining establishments frequently close on specific weekdays (e.g., Mondays) or holidays, and policies change over time without invalidating prior customer commitments.

**Independent Test**: A manager marks "Monday" as closed. Searching for reservations on any Monday returns the restaurant as closed. The manager updates establishment settings, and existing reservations retain their booked times and conditions.

**Acceptance Scenarios**:

1. **Given** a restaurant operating schedule editor, **When** the manager toggles the "Closed" switch/checkbox for a day of the week (e.g., Monday) or a specific date, **Then** open and close time pickers are disabled, the schedule is saved with `isClosed: true`, and the system prevents all new bookings and availability suggestions for that day while preserving already confirmed reservations.
2. **Given** a customer searching for availability on a day marked as closed, **When** they execute the search, **Then** the system informs them that the restaurant is closed on the selected date.
3. **Given** established confirmed reservations, **When** a manager updates establishment settings (such as default duration, advance booking horizon, or cancellation window), **Then** future reservations adhere to the new settings while existing confirmed reservations remain completely unchanged.

---

### User Story 5 - Reservation Overview Visibility & Table Allocations (Priority: P3)

Managers reviewing the reservation dashboard need clear visibility into which specific physical table(s) are assigned to each reservation, and the reason recorded for any cancelled reservation.

**Why this priority**: Empowers restaurant floor managers to seat guests efficiently at their allocated tables and analyze operational patterns behind cancellations.

**Independent Test**: The manager views the reservation list; the "TABLES" column displays assigned table numbers/labels (e.g., "T1", "T2, T3"), and cancelled reservations show the cancellation reason.

**Acceptance Scenarios**:

1. **Given** confirmed reservations with allocated tables, **When** the manager views the reservations overview, **Then** the TABLES column displays the specific assigned table numbers/labels instead of remaining empty or showing a placeholder.
2. **Given** a reservation that was cancelled with a specified reason, **When** the manager inspects the reservation row or details, **Then** the recorded cancellation reason is clearly visible.

---

### User Story 6 - Customer Waiting List Experience & Intelligent Matching (Priority: P3)

Customers tracking their waiting list queue need to see their party size clearly indicated, and the waiting list matching process must accurately evaluate available seating capacity when reservations are cancelled.

**Why this priority**: Eliminates customer confusion regarding their queued party size and ensures that cancelled bookings trigger waiting list offers to appropriately sized parties whenever physical capacity allows.

**Independent Test**: A customer views their waiting list queue card and sees a clear badge displaying their party size. When a reservation is cancelled, waiting list entries with matching date, time window, and party size that can physically be seated receive an offer.

**Acceptance Scenarios**:

1. **Given** an active waiting list entry, **When** the customer views their active waitlist in the portal, **Then** the party size is prominently displayed in a badge alongside target date and time window, matching the presentation of upcoming reservations.
2. **Given** customers queued on the waiting list in FIFO order, **When** a reservation is cancelled, **Then** the waiting list service checks live table availability and extends an offer to the first candidate whose party size can physically be accommodated by the available table inventory within their requested time window, without restricting matching to the cancelled reservation's specific party size.
3. **Given** a customer on the waiting list, **When** an offer is generated, **Then** the customer receives real-time notification with the countdown timer to accept the offer.

---

### Edge Cases

- **Concurrent Booking Race Condition**: If two customers simultaneously attempt to book the last available table or capacity in a timeslot, exactly one succeeds and the other is informed that the slot is no longer available.
- **Table Deletion Protection**: Attempting to delete a table assigned to a confirmed reservation in the future must be prevented with a descriptive warning message.
- **Closed Day Booking Guard**: Direct API requests attempting to book reservations on a designated closed day must be rejected with a descriptive business validation error.
- **Duration Boundary Enforcement**: Selecting a reservation duration shorter than the minimum (e.g., 15 minutes) or longer than the restaurant's configured maximum duration is prevented.
- **Unseatable Party Size**: When a customer requests a party size larger than total restaurant capacity, the system provides an informative message explaining the constraint rather than a generic "fully booked" error.
- **Constituent Table Modification or Deactivation**: When a constituent table that belongs to an active table combination is modified (e.g., its capacity changes), any default-calculated combination capacity is automatically re-synchronized. If a constituent table is deleted or deactivated (allowed only when no active future reservations depend on it), all table combinations containing that table are automatically dissolved and deactivated to prevent impossible physical seating arrangements.
- **Multi-Day Settings Changes**: Updating restaurant operating hours or closed days does not cancel or modify already confirmed reservations that were booked prior to the change.

## Requirements

### Functional Requirements

- **FR-001**: System MUST allow restaurant managers to specify an optional floor zone/location (e.g., "Main Dining", "Patio", "Rooftop") when creating or updating tables, and persist and display this attribute.
- **FR-002**: System MUST allow restaurant managers to create table combinations with an optional custom name (defaulting to a descriptive label derived from constituent tables if omitted) and an optional combined seating capacity (defaulting to the sum of table capacities if omitted), accepting requests without 400 Bad Request errors.
- **FR-003**: System MUST allow restaurant managers to edit existing table properties (table label/number, seating capacity, and floor zone) and delete tables that have no active future reservations, automatically re-synchronizing or dissolving any table combinations containing the modified or deleted table.
- **FR-004**: System MUST provide an explicit 'Closed' toggle/checkbox for each day of the week in the manager schedule editor that disables opening hours inputs, persists the closure (`isClosed: true`), and blocks new bookings while leaving existing confirmed reservations intact.
- **FR-005**: System MUST prevent reservation bookings and availability suggestions on dates or days marked as closed.
- **FR-006**: System MUST display assigned table numbers/labels in the manager reservation overview for all reservations with allocated tables.
- **FR-007**: System MUST display the cancellation reason for all cancelled reservations in the manager portal.
- **FR-008**: System MUST enforce that concurrent active reservations in any timeslot do not exceed the restaurant's total physical table seating capacity.
- **FR-009**: System MUST allocate actual available physical tables or configured table combinations for each reservation rather than fabricated arbitrary table identifiers.
- **FR-010**: System MUST allow customers whose party size exceeds single table or pre-configured combination capacities to select and reserve multiple distinct tables under a single reservation checkout if total table inventory permits, and MUST only offer the waiting list if the restaurant has sufficient cumulative physical capacity to seat the party.
- **FR-011**: System MUST allow restaurant managers to configure both a default dining duration and a maximum dining duration (in minutes) for each establishment.
- **FR-012**: System MUST allow customers to select their desired reservation duration via a dropdown in 15-minute increments within the range between the minimum allowed duration and the restaurant's configured maximum dining duration, defaulting to the restaurant's default duration.
- **FR-013**: System MUST prominently display the default and maximum reservation durations to customers when searching and booking reservations.
- **FR-014**: System MUST allow restaurant managers to update establishment settings (including default duration, maximum duration, advance booking window, and cancellation policy notice), and MUST ensure that modifications do NOT alter or invalidate existing confirmed reservations.
- **FR-015**: System MUST prominently display the guest party size on waiting list entries in the customer portal matching the presentation style of upcoming reservations.
- **FR-016**: System MUST evaluate waiting list candidates in FIFO order upon reservation cancellations by checking live table availability, and extend an offer to the first candidate whose party size can physically be accommodated by available table inventory within their target time window.

### Key Entities

- **Restaurant Table**: Represents a physical dining table with an identifier, restaurant reference, table label/number, seating capacity, floor zone (e.g., "Main Dining", "Patio"), operational status (ACTIVE/INACTIVE), and creation timestamp.
- **Table Combination**: Represents an approved physical joining of two or more tables with an identifier, restaurant reference, combination name, list of constituent table identifiers, and combined seating capacity.
- **Establishment Settings**: Represents the operational configuration of a restaurant, including trade name, address, timezone, default dining duration, maximum dining duration, minimum advance booking lead time, maximum forward booking horizon, cancellation window notice, and weekly/date-specific operating schedules and closures.
- **Reservation**: Represents a booked dining event with customer details, guest party size, scheduled start time, selected duration, scheduled end time, allocated table identifiers/labels (supporting one or multiple assigned distinct physical tables under a single checkout), status (CONFIRMED, ARRIVED, COMPLETED, CANCELLED, NO_SHOW), and optional cancellation reason.
- **Waiting List Entry**: Represents a customer queue request with customer details, restaurant reference, target dining date, earliest acceptable time, latest acceptable time, guest party size, status (WAITING, OFFERED, CONVERTED, CANCELLED), and creation timestamp.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of table creation and update operations in the manager portal capture and persist the floor zone attribute without validation or schema errors.
- **SC-002**: 100% of valid table combination requests succeed without 400 Bad Request errors.
- **SC-003**: Overbooking rate drops to 0%: concurrent reservations within any overlapping timeslot never exceed the total physical seating capacity of the restaurant.
- **SC-004**: 100% of party size inquiries exceeding single table capacity receive multi-table selection options or clear guidance, with 0 futile waiting list entries created for unseatable party sizes.
- **SC-005**: 100% of reservations listed in the manager portal display assigned table labels and recorded cancellation reasons where applicable.
- **SC-006**: 100% of existing confirmed reservations maintain their original booking times and parameters when restaurant establishment settings are modified.

## Assumptions

- Table combinations require at least two distinct tables belonging to the same restaurant.
- Floor zone is an optional descriptive string (e.g., "Main Dining", "Patio", "Bar", "Rooftop") that defaults to "Main Dining" if not specified.
- When deleting a table, if historical (completed/cancelled) reservations reference the table, soft deletion or archival retains historical data while removing it from active allocation inventory.
- Dining durations can be selected in 15-minute intervals between the minimum allowed booking duration (45 minutes, or down to 15 minutes if configured) and the restaurant's configured maximum duration (up to 480 minutes), defaulting to the restaurant's default duration.
- When establishment settings are updated, new rules apply strictly to reservations created after the update timestamp; existing reservations remain bound by the terms confirmed at booking time.
