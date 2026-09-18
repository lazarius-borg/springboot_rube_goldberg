# Feature Specification: Customer Portal Upgrade

**Feature Branch**: `015-customer-portal-upgrade`

**Created**: 2026-09-17

**Status**: Draft

**Input**: User description: "customer portal upgrade - The customer page on the portal should be accessible to authenticated users only with appropriate role. Unauthenticated users should be offered an option to authenticate with an authentication provider, in our case Keycloak. Further, a customer must have access to all functions availalbe to customer role in the system. The changes are focused on the frontend customer portal, the backend MUST remain unaffected, unless, additional functionality should be added to enable the authentication with the authentication provider. Also the UI should be intuitive, for example, user will only join the waiting list if the table is not available at the desired time. So the flow would be, the user searches for a table, if it is avaialable, the option to make a reservation is provided, otherwise it offers the user to be put on the waiting list if the user agrees. Also, the user should be able to check current upcoming reservations, cancel an existing reservation, or remove from the waiting lists."

## Clarifications

### Session 2026-09-17

- Q: When a customer searches for a specific dining time that is fully booked and chooses to join the waiting list, how should the acceptable arrival time window (earliest and latest seating times) be determined? → A: Automatically pre-populate a ±1 hour window around the searched time with inline fields to adjust before confirming.
- Q: Which user role(s) issued by the identity provider should authorize access to the customer portal? → A: Authorize users with CUSTOMER or ROLE_CUSTOMER (and allow ADMIN for audit/preview).
- Q: When a table opens up and a customer's waiting list entry receives an offer, how should the customer interact with and claim this offer on the customer portal? → A: Display an urgent callout on the waiting list card with an active countdown timer and an instant "Accept Offer" button.
- Q: How should the customer portal refresh and synchronize the status of upcoming reservations and waiting list entries? → A: Use Server-Sent Events (SSE), with authorization granted to add an SSE streaming endpoint to the backend for real-time customer push updates (offers, status changes).
- Q: Which backend service should host the Server-Sent Events (SSE) streaming endpoint for the customer portal? → A: notification-service (leverages its existing Kafka event consumers for centralized customer event streaming).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Secure Authentication and Role-Based Customer Access (Priority: P1)

An unauthenticated visitor navigating to the customer portal is presented with a clear, welcoming sign-in gate rather than raw data or non-functional controls. The visitor can initiate authentication with the identity provider. Upon successful login and verification of the appropriate customer role, the visitor is admitted into the portal, displaying their personalized identity (name, email) and access to all customer features. If an authenticated user lacks the required customer role, a clear access-restricted explanation is presented along with an option to sign out or switch accounts.

**Why this priority**: Security gatekeeping is the foundation of customer self-service. Ensuring only authenticated, authorized customers can access booking, cancellation, and queue actions protects customer privacy and prevents unauthorized operations.

**Independent Test**: Can be tested by visiting the customer portal unauthenticated, verifying that customer controls are concealed and a sign-in option is offered. After authenticating as a verified customer, the full portal interface unlocks displaying user profile details and sign-out controls.

**Acceptance Scenarios**:

1. **Given** an unauthenticated visitor, **When** they load the customer portal, **Then** all booking and reservation controls are hidden, a sign-in prompt is clearly displayed, and the user can trigger the identity provider authentication flow.
2. **Given** a user successfully authenticates with the identity provider possessing the customer role, **When** they return to the customer portal, **Then** the sign-in gate disappears, the personalized header shows their name and email, and the search, reservation, and waiting list features become interactive.
3. **Given** an authenticated user who lacks the customer role, **When** the portal verifies their permissions, **Then** an informative unauthorized screen is displayed explaining the missing role, and a sign-out button is available.
4. **Given** an authenticated customer, **When** they click "Sign Out", **Then** their session is terminated, and the portal returns to the unauthenticated sign-in prompt.

---

### User Story 2 - Intuitive Table Search, Direct Booking, and Conditional Waiting List Flow (Priority: P1)

A customer seeking a dining table chooses a restaurant, specifies a date, preferred dining time, and number of guests, and searches for availability. The portal delivers an immediate, intuitive response:
- If a table matching the request is available, the portal presents a direct booking option allowing the customer to confirm the guaranteed reservation with one click without retyping their personal contact details.
- If no table is available for the desired slot, the portal transparently explains that the time slot is fully booked and proactively offers the customer the opportunity to join the fair waiting list for that date and time window. The customer is added to the waiting list only after explicitly opting in.

**Why this priority**: Searching, booking, and conditional wait-listing represent the core value proposition of the dining platform. An intuitive, adaptive flow prevents confusion (such as joining a waiting list when tables are open, or trying to reserve when completely booked) and guarantees a frictionless guest journey.

**Independent Test**: Can be tested by searching for an open dining time slot and successfully confirming a reservation in one step, followed by searching for a fully booked dining time slot, observing the absence of the instant reservation button, and confirming placement onto the waiting list only after accepting the prompt.

**Acceptance Scenarios**:

1. **Given** an authenticated customer searching for a party of 4 on a future date at 19:00, **When** matching table capacity is available, **Then** the portal displays an "Available" confirmation, lists available seating times, and enables a prominent "Book Reservation" action pre-filled with the customer's identity.
2. **Given** an authenticated customer reviewing an available table result, **When** they confirm the booking, **Then** a confirmed reservation is created, a distinct confirmation reference is presented, and their upcoming reservations view immediately updates.
3. **Given** an authenticated customer searching for a slot that has no available tables, **When** search results return, **Then** the direct booking action is disabled/hidden, an informative "Fully Booked" message appears, and an intuitive "Join Waiting List" prompt is offered specifying the requested date, acceptable time window, and party size.
4. **Given** a customer presented with the waiting list prompt, **When** they decline or choose not to join, **Then** no waiting list entry is created and they can adjust their search parameters.
5. **Given** a customer presented with the waiting list prompt, **When** they agree to join, **Then** they are placed in the fair chronological waiting queue, an acknowledgement with queue details is displayed, and their active waiting list view is refreshed.

---

### User Story 3 - Managing and Cancelling Upcoming Reservations (Priority: P2)

An authenticated customer can review all their active and upcoming reservations in a clear chronological summary card or table. Each reservation entry displays the restaurant name, booking date, dining time, party size, and current status. If plans change, the customer can cancel an upcoming reservation directly from the portal, provided the cancellation request satisfies the restaurant's advance notice policy. To prevent accidental cancellations, the portal requires an explicit confirmation step before executing the cancellation.

**Why this priority**: Self-service cancellation provides customers peace of mind and flexibility while promptly returning unneeded tables back to available inventory or opening them to waiting list patrons.

**Independent Test**: Can be tested by booking a reservation, verifying it appears in the customer's upcoming reservations list, clicking cancel, confirming the cancellation in the prompt dialog, and verifying that the reservation status updates to cancelled and tables are released.

**Acceptance Scenarios**:

1. **Given** an authenticated customer with existing reservations, **When** they view the reservations section, **Then** their upcoming bookings are displayed with restaurant name, date, time, party size, and status (e.g., Confirmed).
2. **Given** an upcoming reservation eligible for cancellation under the policy, **When** the customer clicks "Cancel Reservation", **Then** a confirmation dialog appears detailing the booking and asking for confirmation.
3. **Given** a customer confirms the cancellation in the dialog, **When** the cancellation is processed, **Then** the reservation status updates to Cancelled, an alert confirms the cancellation, and the list updates without requiring a page reload.
4. **Given** a reservation that has passed the allowable cancellation deadline, **When** the customer attempts cancellation, **Then** an informative message explains that the advance notice deadline has passed and directs the customer to contact the restaurant directly.

---

### User Story 4 - Tracking and Leaving Waiting List Queues (Priority: P2)

An authenticated customer can inspect all their active waiting list entries across restaurants. Each entry details the restaurant name, requested dining date, preferred seating time window, party size, and queue status (e.g., Waiting, Offered, Expired). If the customer no longer needs the table or has made alternate arrangements, they can voluntarily remove themselves from the waiting list queue with a single confirmation, freeing the spot for other guests.

**Why this priority**: Customers must maintain visibility and control over their waitlist requests. Self-service removal prevents ghost entries in the queue and ensures notifications and offers are dispatched only to genuinely interested patrons.

**Independent Test**: Can be tested by joining a waiting list, confirming the entry appears under active waiting list entries, clicking "Leave Waiting List", and verifying the entry is removed from the active queue.

**Acceptance Scenarios**:

1. **Given** an authenticated customer on one or more waiting lists, **When** they navigate to their waiting list section, **Then** all active entries are listed with restaurant, target date, preferred time window, and party size.
2. **Given** an active waiting list entry, **When** the customer clicks "Leave Waiting List" and confirms, **Then** the entry is cancelled/removed from the active queue, a notification confirms removal, and the view updates.
3. **Given** a customer who has no active waiting list entries, **When** they view the waiting list section, **Then** an encouraging empty state message is shown explaining how to join a waiting list when search results are full.
4. **Given** a waiting list entry that receives an offer (`Offered` status), **When** the customer views the waiting list section, **Then** an urgent callout displays an active countdown timer reflecting the expiration deadline and an instant "Accept Offer" button that confirms the reservation upon click.

---

### Edge Cases

- **Expired Session or Token**: What happens when an authenticated customer's session expires while they are browsing or submitting a form? The portal detects the expired session, attempts a transparent refresh, and if unrecoverable, preserves input context where possible while safely redirecting to the sign-in gate.
- **Simultaneous Table Sniping**: What happens if a table was available when searched, but another customer books the last slot before the user clicks "Book Reservation"? The portal handles the booking conflict gracefully, informs the customer that the slot was just taken, and immediately offers the option to join the waiting list for that slot.
- **Network or Service Interruption**: How does the portal handle unexpected service outages or connectivity issues during search or booking? The portal displays clear, non-technical error notifications with actionable retry suggestions rather than broken UI layouts or empty white screens.
- **Past Date and Time Boundary Validation**: What happens if a customer attempts to select past dates or times? The portal UI restricts date pickers to current and future operational dates, preventing invalid submission before reaching the backend.
- **Cancellation Window Boundary**: What happens when a customer attempts to cancel an upcoming reservation within the restricted cancellation window? The UI clearly explains the restriction and indicates the restaurant's policy (minimum 2 hours advance notice required), disabling the cancel action or displaying an informative prompt directing the customer to contact the restaurant directly.
- **Server-Sent Events (SSE) Stream Disconnection**: What happens if the real-time event stream drops due to network interruption or server restart? The portal automatically attempts exponential-backoff reconnection and falls back to periodic status polling until the streaming link is successfully re-established.
- **Active Waiting List Offer Expiration**: What happens if an offer's 15-minute countdown reaches 00:00 while the customer is actively viewing the screen? The offer card immediately transitions from active to expired state, disables the "Accept Offer" button, displays an informative message explaining that the table opening was released to the next waiting guest, and updates the entry status badge to Expired without requiring a manual page refresh.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The customer portal MUST enforce authentication, restricting access to all customer features to authenticated users only. Sensitive bearer tokens MUST be maintained securely in runtime memory or ephemeral session state, never logged to browser console or telemetry, and transmitted exclusively via authorization headers to prevent exposure in browser URLs or history.
- **FR-002**: Unauthenticated visitors MUST be presented with a clear landing view explaining the portal and offering an explicit action to authenticate via the configured identity provider.
- **FR-003**: The portal MUST verify that the authenticated user possesses an authorized customer role (specifically `CUSTOMER`, `ROLE_CUSTOMER`, or `ADMIN`) before granting access to customer operations.
- **FR-004**: If an authenticated user lacks the required customer role, the portal MUST display an unauthorized notification with an option to sign out.
- **FR-005**: Upon successful authentication, the portal MUST display the customer's name, email, and a sign-out control in the top navigation or header.
- **FR-006**: The portal MUST provide an intuitive table search interface enabling customers to select a restaurant, dining date, desired seating time, and party size.
- **FR-007**: When search indicates available tables, the portal MUST present a clear option to book a confirmed reservation for the requested or adjacent available time slots.
- **FR-008**: When booking a reservation, the portal MUST automatically use the authenticated customer's name and contact email without requiring redundant manual data entry.
- **FR-009**: When search indicates no tables are available for the desired criteria, the portal MUST explicitly indicate that the time slot is fully booked and present an opt-in opportunity to join the fair waiting list, automatically pre-populating an arrival time window spanning ±1 hour around the searched dining time while providing inline controls for the customer to adjust earliest and latest boundaries before confirming.
- **FR-010**: The portal MUST NOT place a customer onto the waiting list automatically; it MUST require explicit customer consent (e.g., clicking "Join Waiting List").
- **FR-011**: The portal MUST display the customer's upcoming and active dining reservations, showing restaurant, dining date, start time, party size, and authoritative status values (`CONFIRMED`, `ARRIVED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`).
- **FR-012**: The portal MUST allow customers to cancel an upcoming reservation, requiring an explicit confirmation step before finalizing cancellation.
- **FR-013**: The portal MUST inform the customer if an upcoming reservation can no longer be cancelled due to restaurant cancellation policy restrictions (minimum 2 hours advance notice required).
- **FR-014**: The portal MUST display the customer's active waiting list entries, showing restaurant, target date, preferred time window, party size, and status.
- **FR-015**: When a waiting list entry transitions to an offered state, the portal MUST present a prominent callout with an active countdown timer reflecting the offer deadline and an instant "Accept Offer" button, enabling the customer to immediately confirm and secure the dining reservation. If the countdown reaches zero (00:00), the offer card MUST automatically disable acceptance and indicate expiration.
- **FR-016**: The portal MUST allow customers to voluntarily remove themselves from an active waiting list entry with a confirmation prompt.
- **FR-017**: The portal UI MUST provide timely feedback (loading indicators during searches/actions, clear success alerts, and understandable error messages).
- **FR-018**: The customer portal MUST establish a real-time Server-Sent Events (SSE) connection to receive immediate push notifications when reservation statuses change or when a waiting list offer is extended to the customer.
- **FR-019**: Backend modifications are explicitly authorized solely to implement the authenticated customer SSE streaming endpoint in `notification-service` (consuming existing platform Kafka events) and necessary gateway routing / authentication integration, leaving core reservation, waiting list, and availability business logic intact.
- **FR-020**: The portal MUST adhere to standard web accessibility guidelines: all form inputs MUST have associated descriptive labels, interactive modal dialogs MUST trap focus and close on Escape key, and real-time alerts or countdown announcements MUST leverage ARIA live regions (`aria-live="polite"` or `"assertive"`).

### Key Entities *(include if feature involves data)*

- **Customer Profile**: Represents the authenticated customer identity, including unique identifier, display name, contact email, and authorized role.
- **Restaurant**: Represents a dining establishment available for booking, including its unique identifier, display name, and operational context.
- **Table Availability**: Represents the real-time capacity and available seating time slots for a specified restaurant, date, time, and party size.
- **Dining Reservation**: Represents a confirmed or past dining booking, including reservation reference, restaurant, customer identity, party size, dining start and end timestamps, and lifecycle status (e.g., Confirmed, Cancelled, Completed).
- **Waiting List Entry**: Represents an active queue request for a fully booked dining window, including entry reference, restaurant, customer identity, target dining date, acceptable time range, party size, and queue status (e.g., Waiting, Offered, Expired, Cancelled).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of unauthenticated attempts to access customer operations are blocked and routed to the authentication provider sign-in gate.
- **SC-002**: First-time or returning customers can complete a search-to-booking flow for an available table in under 60 seconds with 3 or fewer clicks.
- **SC-003**: 100% of fully booked search queries seamlessly transition to an opt-in waiting list prompt with all search parameters (date, time window, party size) pre-populated.
- **SC-004**: Customers can review and execute cancellation of an eligible upcoming reservation in under 30 seconds with immediate visual confirmation of status change.
- **SC-005**: Customers can review active waiting list entries and remove themselves from the queue in under 20 seconds.
- **SC-006**: Zero regression in existing backend test suites across all reactor microservices.

## Assumptions

- **Identity Provider**: Keycloak is configured with realm `rube-goldberg` and client credentials capable of issuing customer tokens with standard roles (e.g., `ROLE_CUSTOMER` or `CUSTOMER`).
- **Single Page Architecture**: The customer portal operates as a responsive web application served via the API gateway static assets, matching the design aesthetic of the platform.
- **Browser Compatibility**: Target users access the portal via standard modern web browsers (Chrome, Firefox, Safari, Edge) with JavaScript and session storage enabled.
- **Restaurant Selection**: The portal dynamically loads the list of available restaurants from the existing platform catalog, allowing customers to easily pick a dining venue.
- **Real-Time Streaming**: The Server-Sent Events (SSE) stream is hosted by `notification-service` and routed through the API Gateway, listening to existing Kafka event topics to dispatch real-time events to connected customer browser sessions.
- **Backend Stability**: The core business logic for table availability, reservations, and waiting lists remains intact; frontend integration connects directly to the documented API endpoints exposed through the gateway.
