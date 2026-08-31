# Feature Specification: Restaurant Reservation Platform (Rube Goldberg Showcase)

**Feature Branch**: `001-restaurant-reservation-platform`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "rube goldberg restaurant reservation - Follow the instructions provided in springboot-rube-goldberg-prd.md to implement the microservices application."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Restaurant Configuration & Management (Priority: P1)

Restaurant managers configure their restaurant details, opening hours with multiple daily shifts, table inventory with individual capacities, combinable table sets, and booking policies (minimum advance notice, maximum booking horizon, cancellation window, and default duration).

**Why this priority**: Foundational prerequisite for all discovery, availability search, table allocation, and reservation operations. Without configured restaurants and tables, no reservations or availability checks can function.

**Independent Test**: Can be fully tested by creating and configuring a restaurant with opening hours and tables, and verifying that the configuration is stored, retrieved, and enforces policy bounds.

**Acceptance Scenarios**:

1. **Given** an authenticated restaurant manager, **When** they register a restaurant with name, address, IANA timezone (e.g., `Europe/Amsterdam`), reservation duration, advance booking windows, and cancellation window, **Then** the restaurant profile is created and viewable.
2. **Given** a configured restaurant, **When** the manager adds tables with specific capacities and defines allowed table combinations (e.g., Table 1 [cap 2] + Table 2 [cap 2]), **Then** the table inventory and combination rules are saved.
3. **Given** a configured restaurant, **When** the manager defines daily opening hour intervals (e.g., 12:00–15:00 and 18:00–23:00) and special closure dates, **Then** the schedule is active and queryable in the restaurant's local timezone.

---

### User Story 2 - Customer Discovery & Real-Time Availability Search (Priority: P1)

Customers browse restaurants and search for table availability for a specific party size, date, and time. The system calculates available seating options considering operating hours, existing bookings, table capacities, and valid table combinations.

**Why this priority**: Core customer discovery flow that enables reservations. It allows customers to quickly find when and where they can book a table.

**Independent Test**: Can be fully tested by querying availability for various party sizes and time slots against a restaurant with pre-existing bookings, verifying correct available slots and table allocation possibilities.

**Acceptance Scenarios**:

1. **Given** a restaurant with tables, **When** a customer searches for availability for a party of 4 at 19:00 on a given date, **Then** the system returns whether suitable capacity exists (single table or valid combination) and possible time slots within operating hours.
2. **Given** a restaurant whose tables are fully booked for a requested time slot, **When** a customer searches for availability, **Then** the system reports no availability for that slot and suggests alternative open slots on the same day.
3. **Given** a search requested outside restaurant operating hours or beyond maximum booking horizon, **When** the customer submits the query, **Then** the system rejects the search with clear guidance on allowed booking windows.

---

### User Story 3 - Guaranteed Reservation Creation & Automatic Table Allocation (Priority: P1)

Customers book a table for an available slot. The system deterministically allocates the optimal table (preferring the smallest single table that fits the party, falling back to an allowed combination), confirms the reservation immediately without manual restaurant approval, and prevents double-booking under concurrent load.

**Why this priority**: The primary transactional value stream of the platform. Guarantees business consistency and instant customer confirmation.

**Independent Test**: Can be fully tested by making simultaneous reservation requests for overlapping times and verifying that valid reservations are confirmed, double bookings are strictly prevented, and table allocation rules are obeyed.

**Acceptance Scenarios**:

1. **Given** available tables (Table A: cap 2, Table B: cap 4, Table C: cap 6), **When** a customer books for party size 4 at 19:00, **Then** the system allocates Table B (smallest sufficient), creates a confirmed reservation, and reserves the table for the restaurant's configured duration.
2. **Given** no single table of cap 4 is free, but combinable tables A (cap 2) and D (cap 2) are free, **When** a customer books for party size 4, **Then** the system allocates combination A+D and confirms the reservation.
3. **Given** two customers attempting to book the last available table for the same time slot concurrently, **When** both submit reservations, **Then** exactly one reservation succeeds and is confirmed, while the other is rejected with a conflict error.

---

### User Story 4 - Reservation Lifecycle Management & Cancellation (Priority: P2)

Customers can view, modify, or cancel their reservations within the restaurant's cancellation policy window. Restaurant operators can update the status of reservations through their operational lifecycle (`CONFIRMED` -> `ARRIVED` -> `COMPLETED`, or `NO_SHOW` / `CANCELLED`).

**Why this priority**: Essential for operational execution on the day of the reservation and customer self-service.

**Independent Test**: Can be fully tested by transitioning a reservation through all lifecycle states and verifying policy enforcement (e.g., attempting cancellation after the policy deadline fails).

**Acceptance Scenarios**:

1. **Given** a confirmed reservation more than 2 hours in advance (where cancellation window is 2 hours), **When** the customer cancels the reservation, **Then** the reservation status transitions to `CANCELLED` and the allocated table is released.
2. **Given** a confirmed reservation less than 2 hours before the start time, **When** the customer attempts to cancel, **Then** the system rejects the cancellation due to policy violation.
3. **Given** a confirmed reservation on the reservation date, **When** the restaurant manager marks the customer as `ARRIVED` and later `COMPLETED`, **Then** the status transitions correctly and audit history is preserved.

---

### User Story 5 - Fair FIFO Waiting List & Automated Offer Lifecycle (Priority: P2)

When no availability exists for a requested party size and time range, customers can join a waiting list. When a cancellation or capacity change creates an opening, the system automatically offers the slot to the oldest matching waiting list entry (FIFO). The customer receives an offer with an expiration timer to accept or reject.

**Why this priority**: Recovers lost restaurant revenue, delights customers who missed initial availability, and demonstrates advanced asynchronous event coordination and eventual consistency.

**Independent Test**: Can be fully tested by putting two customers on a waiting list, cancelling an overlapping confirmed reservation, and verifying that the first customer receives the offer, which can be accepted into a confirmed reservation or timed out to offer to the second customer.

**Acceptance Scenarios**:

1. **Given** a fully booked slot, **When** customer A (10:00 AM) and customer B (10:05 AM) join the waiting list for 4 people on Friday evening, **Then** both entries are recorded with timestamp and requested time boundaries.
2. **Given** customer A and B on the waiting list, **When** an existing reservation for 4 is cancelled, **Then** customer A (the earliest entry) receives a time-limited reservation offer, while customer B remains waiting.
3. **Given** an active offer for customer A, **When** customer A accepts within the expiration window, **Then** the offer is converted into a confirmed reservation and customer A is removed from the waiting list.
4. **Given** an active offer for customer A, **When** the offer expires without acceptance or is rejected, **Then** the offer expires and the system automatically generates an offer for customer B.

---

### User Story 6 - Automated Customer Notifications & Reminders (Priority: P3)

Customers receive automated notifications for all significant reservation events (confirmation, modification, cancellation, upcoming reminders before the booking, waiting list offers, and expiration notices).

**Why this priority**: Keeps customers informed throughout their lifecycle and reduces no-shows.

**Independent Test**: Can be fully tested by executing reservation actions and checking that corresponding structured email messages are dispatched with accurate details and timestamps.

**Acceptance Scenarios**:

1. **Given** a newly confirmed reservation, **When** the reservation transaction completes, **Then** a confirmation notification containing restaurant address, date, time, party size, and cancellation policy is dispatched to the customer.
2. **Given** an upcoming reservation approaching its reminder threshold (e.g., 24h prior), **When** scheduled reminder processing runs, **Then** a single reminder notification is sent to the customer without duplicate dispatches on service restarts.
3. **Given** a waiting list match, **When** an offer is generated, **Then** an offer notification with an acceptance link and expiration deadline is dispatched to the customer.

---

### User Story 7 - Read-Only Business Analytics & Operational Telemetry (Priority: P3)

Restaurant managers and system administrators view real-time aggregated metrics including reservation volume, cancellation rates, no-show rates, average party sizes, waiting list conversion efficiency, and notification delivery metrics.

**Why this priority**: Provides business visibility and feedback loops without impacting the performance or availability of the core transactional booking path.

**Independent Test**: Can be fully tested by generating reservations, cancellations, and waiting list conversions, and verifying that the analytics summaries reflect exact aggregated counts and rates.

**Acceptance Scenarios**:

1. **Given** multiple reservation and waiting list events across several restaurants, **When** an administrator queries analytics, **Then** aggregated counts (reservations created, cancelled, no-shows, average party size, waiting list conversion rate) are displayed.
2. **Given** an unexpected downtime in the analytics component, **When** customers make or cancel reservations, **Then** all core booking operations continue to succeed without disruption, and analytics catches up upon recovery.

---

### User Story 8 - Role-Based Customer & Manager Portal Experience (Priority: P3)

End-to-end user experience allowing customers to log in, search, book, view, and manage their reservations and waiting lists, and allowing restaurant managers to log in, configure restaurant profiles, tables, hours, and update day-of-service reservation statuses.

**Why this priority**: Demonstrates end-to-end usability and role-based access control across customer, restaurant manager, and administrative personas.

**Independent Test**: Can be fully tested by logging in as a customer persona and manager persona respectively, verifying that access boundaries are enforced and that each persona can complete their workflows.

**Acceptance Scenarios**:

1. **Given** an authenticated customer, **When** accessing the portal, **Then** they can view only their own reservations, active waiting list entries, and browse public restaurant information.
2. **Given** an authenticated restaurant manager, **When** accessing the portal, **Then** they can manage tables, hours, and live reservation rosters for their assigned restaurants, but cannot modify other restaurants.
3. **Given** an unauthenticated or unauthorized user, **When** attempting to access protected management endpoints, **Then** access is denied with standard authentication challenges.

---

### Edge Cases

- **Timezone Transitions (Daylight Saving Time)**: All availability, operating hour, and reservation duration calculations MUST evaluate against the restaurant's configured IANA timezone rules rather than system UTC or server time.
- **Concurrent Booking Race Conditions**: When two users attempt to book the final available table simultaneously, the system MUST strictly ensure only one booking succeeds; the second receives a conflict response and an invitation to join the waiting list.
- **Orphaned Waiting List Offers**: If a customer neither accepts nor rejects a waiting list offer, the offer MUST automatically expire when the deadline passes, triggering immediate re-evaluation and offer dispatch to the next FIFO candidate.
- **Service Isolation & Outages**: If notification or analytics subsystems are temporarily unavailable, core reservation creation, modification, and cancellation MUST continue without interruption.
- **Boundary Party Sizes**: Requests smaller than a table capacity fit on the smallest available table; requests exceeding all individual table and combined table capacities are rejected with a clear message.
- **Late Arrivals & No-Shows**: When a reservation start time passes without customer check-in, managers can transition status to `NO_SHOW`, freeing records for analytics and releasing table holds.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow restaurant managers to create and maintain restaurant profiles with name, address, IANA timezone, default reservation duration, minimum advance booking time, maximum booking horizon, and cancellation window.
- **FR-002**: System MUST allow managers to configure multiple opening hour intervals per day of week and specific calendar date overrides/closures for each restaurant.
- **FR-003**: System MUST allow managers to manage tables with explicit capacities and define valid multi-table combination groups.
- **FR-004**: System MUST allow customers to query table availability for a specific restaurant, date, time, and party size, evaluating operating hours and existing reservation occupancy in the restaurant's local timezone.
- **FR-005**: System MUST automatically allocate the smallest sufficient table or valid table combination when a reservation is requested.
- **FR-006**: System MUST create confirmed reservations immediately upon successful table allocation without requiring manual restaurant confirmation.
- **FR-007**: System MUST strictly prevent double-booking of any table or combined table set across overlapping reservation time spans.
- **FR-008**: System MUST allow customers to modify reservation details (party size, time) or cancel reservations if the request is made before the restaurant's cancellation window deadline.
- **FR-009**: System MUST allow restaurant operators to update reservation operational status following the valid state machine (`CONFIRMED` -> `ARRIVED` -> `COMPLETED`, `CONFIRMED` -> `NO_SHOW`, `CONFIRMED` -> `CANCELLED`).
- **FR-010**: System MUST allow customers to join a waiting list with desired restaurant, date, acceptable time window (earliest/latest time), and party size when no immediate availability exists.
- **FR-011**: System MUST match newly freed capacity to eligible waiting list entries strictly in First-In, First-Out (FIFO) order based on entry creation time.
- **FR-012**: System MUST generate time-limited reservation offers for matched waiting list entries and allow customers to accept or reject them.
- **FR-013**: System MUST automatically expire unaccepted waiting list offers when the expiration deadline passes and immediately offer the opening to the next eligible FIFO candidate.
- **FR-014**: System MUST dispatch structured notifications (confirmations, modifications, cancellations, reminders, waiting list offers, and expiration notices) to customers.
- **FR-015**: System MUST execute scheduled reservation reminder processing asynchronously and ensure idempotency to prevent duplicate reminders upon service restarts.
- **FR-016**: System MUST collect and aggregate operational metrics (reservations by status, cancellations, no-shows, average party size, waiting list conversions) in a read-only manner decoupled from transactional booking.
- **FR-017**: System MUST enforce role-based access control distinguishing `CUSTOMER`, `RESTAURANT_MANAGER`, and `ADMIN` roles for all protected actions.
- **FR-018**: System MUST provide structured error responses compliant with RFC 9457 Problem Details for all API validation failures and business rule rejections.
- **FR-019**: System MUST expose comprehensive OpenAPI / Swagger documentation for all service endpoints.
- **FR-020**: System MUST guarantee eventual consistency and reliable event delivery for all domain events across service boundaries without data loss.

### Key Entities

- **Customer**: Represents an application user profile associated with an authenticated identity (Customer ID, Identity Subject ID, Name, Email, Phone, Account Status).
- **Restaurant**: Represents a dining establishment owning operational settings, IANA timezone, booking advance limits, cancellation policies, and default reservation durations.
- **OpeningHours**: Represents operating time ranges for days of the week or specific calendar dates for a restaurant.
- **Table**: Represents physical seating capacity (Table ID, Restaurant ID, Capacity, Combinable Group associations).
- **TableCombination**: Represents an explicit set of tables that may be combined to accommodate larger party sizes.
- **Reservation**: Authoritative booking record (Reservation ID, Restaurant ID, Customer ID, Party Size, Start Time, End Time, Status [`CONFIRMED`, `ARRIVED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`], Allocated Tables, Creation/Update Timestamps).
- **WaitingListEntry**: Represents a request for unavailable capacity (Entry ID, Restaurant ID, Customer ID, Date, Earliest Time, Latest Time, Party Size, Status, Creation Timestamp).
- **WaitingListOffer**: Represents a time-limited offer generated from a waiting list match (Offer ID, Waiting List Entry ID, Allocated Tables, Proposed Time, Expiration Time, Status [`PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED`]).
- **Notification**: Outbound message record (Notification ID, Customer ID, Notification Type, Channel, Recipient, Content, Status, Dispatched Timestamp).
- **AnalyticsSummary**: Aggregated analytical projection of reservation, cancellation, and waiting list throughput metrics.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Customers can discover a restaurant, search availability, and complete a confirmed reservation in under 60 seconds end-to-end.
- **SC-002**: Zero double-bookings occur under concurrent booking attempts for the same table slot (100% reservation consistency).
- **SC-003**: 100% of freed reservation slots with matching waiting list requests generate a FIFO offer to the oldest eligible entry within 5 seconds of cancellation.
- **SC-004**: 100% of reservation creation, cancellation, and offer events result in dispatched customer notifications.
- **SC-005**: 100% of REST API errors conform to RFC 9457 Problem Details with descriptive problem types and status codes.
- **SC-006**: Complete application build, module compilation, and full test suite execution passes cleanly via a single `./mvnw clean verify` command.
- **SC-007**: Core reservation booking operations maintain 100% availability even when auxiliary systems (Notification, Analytics) experience temporary disruptions.
- **SC-008**: Full distributed environment with all microservices, databases, messaging, identity provider, and observability tools can be launched with a single `docker compose up` command.

## Assumptions

- **Authentication & Identity**: User authentication and identity token issuance are handled by an OIDC Identity Provider (Keycloak); the application validates standard JWTs and extracts user claims and roles.
- **Notification Channel**: The primary initial notification channel is email, delivered to a local development mailbox (Mailpit) in development and staging environments.
- **Currency & Payments**: Payment processing, deposits, and billing are explicitly out of scope per non-goals.
- **Language & Platform**: Built on modern Java (26) with virtual threads and the latest Spring Boot 3.x release, structured as a Maven reactor multi-module project.
- **Time Representation**: All timestamps across APIs and events use standard ISO-8601 strings with explicit UTC or timezone offsets.
