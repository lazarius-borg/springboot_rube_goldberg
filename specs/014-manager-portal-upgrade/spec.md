# Feature Specification: Manager Portal Upgrade & Authentication

**Feature Branch**: `014-manager-portal-upgrade`

**Created**: 2026-09-16

**Status**: Draft

**Input**: User description: "manager portal upgrade - The manager portal offers only access to insights to analytics, which itslef doesn't work, gets 404 when it tries to access '/api/v1/analytics/summary'. The manager page on the portal should be accessible to authenticated users only with appropriate roles. Unauthenticated users should be offered an option to authenticate with an authentication provider, in our case Keycloak. Further, a manager must have access to all management functions availalbe in the system. The changes are focused on the frontend manager portal, the backend MUST remain unaffected, unless, additional functionality should be added to enable the authentication with the authentication provider."

## Clarifications

### Session 2026-09-16
- Q: Which authentication user experience should be implemented for managers authenticating with Keycloak? → A: Keycloak Standard OIDC Redirect with PKCE (standard authorization code flow with PKCE redirecting to Keycloak login page and returning tokens to the manager portal).
- Q: How should the manager portal determine which restaurant's reservations, tables, and analytics to display? → A: Interactive Restaurant Dropdown Selector (dynamically loaded from the restaurant catalog allowing managers to switch between establishments or inspect platform-wide metrics).
- Q: How should waiting list oversight be presented in the manager portal given the backend change constraint? → A: Add a GET /api/v1/waiting-list query endpoint to waiting-list-service to enable a full tabular list of active waiting list entries (waiving the backend change restriction for this targeted oversight capability).
- Q: When viewing operational analytics, should the summary display the currently selected restaurant's metrics or platform-wide aggregate metrics by default? → A: Scoped by default to the currently selected restaurant, with an explicit "All Restaurants (Platform Total)" dropdown option to inspect platform-wide aggregates.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Authentication & Role-Based Access Control (Priority: P1)

As an unauthenticated user or restaurant manager visiting the manager portal,
I want the portal to guard all management capabilities behind authentication and require appropriate manager or administrator roles,
So that sensitive operational data, analytics, and restaurant controls cannot be viewed or manipulated by unauthorized visitors or regular diners.

**Why this priority**: Security and access gating are foundational. The manager portal currently exposes broken and unprotected placeholder views. Enforcing authenticated access with role validation is the prerequisite for all operational functions.

**Independent Test**:
1. Open the manager portal without credentials; verify that the portal displays an authentication prompt rather than operational controls.
2. Log in with user credentials lacking the manager/admin role (e.g. a customer); verify that an "Access Denied / Insufficient Privileges" screen is shown with a logout option.
3. Log in with valid manager credentials; verify that the operational dashboard unlocks, displaying manager identity and management tabs.

**Acceptance Scenarios**:

1. **Given** an unauthenticated visitor navigating to the manager portal, **When** the page loads, **Then** all management controls and data views are hidden and the user is presented with an option to authenticate with Keycloak.
2. **Given** an unauthenticated user who initiates login, **When** they authenticate successfully with Keycloak credentials holding `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`, **Then** the manager dashboard is displayed, their identity and active roles are visible, and their session token is retained for subsequent service requests.
3. **Given** an authenticated user whose credentials only contain non-manager roles (such as `ROLE_CUSTOMER`), **When** they access the manager portal, **Then** access is denied, explaining that manager or administrator privileges are required, with an option to log out or switch accounts.
4. **Given** an authenticated manager on the portal, **When** they select "Sign Out", **Then** their session and credentials are cleared from the browser and the portal returns to the unauthenticated login prompt.

---

### User Story 2 - Operational Analytics & Live Performance Insights (Priority: P1)

As a restaurant manager,
I want to view real-time business and operations analytics without encountering errors,
So that I can monitor reservation volumes, cancellation rates, no-show trends, and waiting list conversion efficiency.

**Why this priority**: Resolving the existing 404/broken analytics connection restores baseline operational visibility and verifies that authenticated API communication functions properly end-to-end.

**Independent Test**: Authenticate as a manager and view the analytics view. Confirm that analytics metrics load accurately from the backend without 404 or authorization failures, and that metrics refresh both on a scheduled interval and via a manual refresh button.

**Acceptance Scenarios**:

1. **Given** an authenticated manager viewing the analytics section, **When** the summary data loads, **Then** the portal presents total reservations, cancellations, no-shows, waiting list entries, and conversion rate percentage populated from live backend data.
2. **Given** a network or communication issue when loading analytics, **When** a request fails, **Then** an informative error message is displayed to the manager with a retry option rather than silent degradation or broken placeholders.
3. **Given** an authenticated manager, **When** they view the analytics dashboard, **Then** metrics are scoped by default to their currently selected restaurant, and selecting "All Restaurants (Platform Total)" updates the summary to display system-wide aggregated metrics.

---

### User Story 3 - Reservation Management & Table Turn Execution (Priority: P2)

As a restaurant manager on shift,
I want to view, filter, update, and back-fill reservations for my restaurant,
So that I can manage dining room flow, mark guest arrivals, handle cancellations/no-shows, and record offline walk-ins.

**Why this priority**: Managing bookings and updating dining states is the primary recurring daily task of restaurant managers.

**Independent Test**: Select a restaurant, list upcoming reservations, update a reservation status from confirmed to arrived, cancel a reservation, and create a retroactive reservation for an earlier time slot today (back-filling). Confirm all updates persist in the system.

**Acceptance Scenarios**:

1. **Given** an authenticated manager viewing the reservation management tab, **When** selecting a restaurant and date, **Then** all scheduled reservations for that restaurant and date are listed with guest name, party size, dining time, contact details, and current status.
2. **Given** a reservation in `CONFIRMED` state, **When** the manager marks the party as `ARRIVED` or `COMPLETED`, **Then** the updated status is saved and reflected in the reservation list.
3. **Given** a reservation where the guest did not arrive or called to cancel, **When** the manager updates the status to `NO_SHOW` or `CANCELLED`, **Then** the status change is recorded.
4. **Given** a walk-in guest or phone reservation taken earlier during a rush, **When** the manager enters a reservation with a past start time today, **Then** the reservation is accepted and confirmed under manager back-filling privileges.

---

### User Story 4 - Restaurant Profile, Dining Tables & Floor Configuration (Priority: P2)

As a restaurant manager or administrator,
I want to inspect and configure restaurant details, operating hours, dining tables, and combinable table groups,
So that our physical floor layout and seating inventory accurately match on-premise operational capabilities.

**Why this priority**: Managers need to onboard new restaurants, modify hours of operation, and adjust table capacities or zones as dining spaces evolve.

**Independent Test**: View registered restaurants, inspect floor tables for a restaurant, add a new table with a designated capacity and zone, and update operating hours.

**Acceptance Scenarios**:

1. **Given** an authenticated manager, **When** viewing the restaurant configuration section, **Then** the list of restaurants with address, contact info, and operational hours is displayed.
2. **Given** a manager configuring a restaurant's floor, **When** they add a new table specifying table number, capacity, and zone, **Then** the table is saved and displayed in the restaurant's table inventory.
3. **Given** a manager updating dining schedule, **When** they update opening and closing times, **Then** the new operating hours are saved and applied to future availability checks.
4. **Given** an administrator or manager registering an establishment, **When** they submit a new restaurant profile, **Then** the restaurant is created and available in the management portal selector.

---

### User Story 5 - Live Seating Availability Checks & Waiting List Oversight (Priority: P3)

As a restaurant manager handling guest seating inquiries and waitlists,
I want to perform real-time table availability lookups and inspect active waiting list queues,
So that I can inform walk-in guests of immediate table availability and oversee guests waiting for cancellations.

**Why this priority**: Table lookups and waitlist monitoring allow floor managers to optimize table utilization during peak meal periods.

**Independent Test**: Run a table availability check for a specific party size and time slot. Check the waiting list view to see current entries waiting for openings.

**Acceptance Scenarios**:

1. **Given** an authenticated manager checking table availability, **When** they specify restaurant, date, time, and party size, **Then** the portal displays whether matching tables are available and lists candidate time slots.
2. **Given** an authenticated manager checking availability for a past time slot to verify historical floor availability for back-filling, **When** the search is submitted, **Then** the query succeeds under manager privileges.
3. **Given** guests waiting for tables on a busy evening, **When** the manager views the waiting list section for a restaurant, **Then** active queue entries are displayed with guest contact info, target date, desired time window, and party size.

---

### Edge Cases

- **Expired Authentication Token**: If the user's authentication token expires during active management operations, API requests must detect the authorization failure, attempt token refresh if supported, or prompt the user to re-authenticate without losing unsubmitted form state.
- **Role Mismatch**: When a user authenticated with customer-only credentials accesses the manager portal URL, the system must clearly display an access denied message and prevent any background API calls that would trigger cascade 403 errors.
- **Backend Service Offline / Unreachable**: If one of the downstream microservices (e.g. `analytics-service` or `reservation-service`) is temporarily unavailable or returning 503/504 via the gateway, the portal must display a scoped error banner for that specific tab or card rather than crashing the entire portal.
- **Zero Restaurants Registered**: If the platform has no restaurants registered yet, the portal should guide the manager to the restaurant registration workflow rather than presenting empty dropdowns or failing queries.
- **Clock Skew and Manager Back-Filling**: When managers book reservations in the past for walk-in reconciliation, the UI must clearly label the booking as a retroactive entry while allowing submission.
- **Keycloak Service Unreachable**: If the Keycloak authentication service is unreachable or encounters network timeout during OIDC initialization or login redirect, the portal MUST display a clear error alert ("Authentication service currently unavailable. Please verify Keycloak is running.") with a manual "Retry Connection" button, avoiding unhandled script exceptions or blank screens.
- **Destructive Action Confirmation**: Before executing destructive or non-reversible actions (specifically cancelling a reservation), the portal MUST display an explicit confirmation dialog detailing the reservation and warning that allocated tables will be released, dispatching the cancellation request only upon positive confirmation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The manager portal MUST restrict access to authenticated users possessing either `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`.
- **FR-002**: The manager portal MUST provide an authentication prompt for unauthenticated visitors that enables authentication with Keycloak using standard OpenID Connect (OIDC) Authorization Code Flow with PKCE via Keycloak login redirect. If Keycloak is unreachable during initialization, the portal MUST present a user-friendly error banner with a retry connection action.
- **FR-003**: The manager portal MUST display an explicit access denied notification to authenticated users who lack the required manager or administrator roles, offering a sign-out action.
- **FR-004**: The manager portal MUST display the current user identity (username/email and role badge) and provide an explicit sign-out action that clears stored tokens and terminates the session.
- **FR-005**: The manager portal MUST transmit the user's valid bearer token in the `Authorization` header on all management API requests.
- **FR-006**: The manager portal MUST resolve the analytics summary loading issue by retrieving live metrics from `/api/v1/analytics/summary` using the authenticated session and handling errors gracefully.
- **FR-007**: The manager portal MUST provide live business analytics displays including Total Reservations, Cancellations, No-Shows, Waiting List Entries, and Waiting List Conversion Rate, filtered by the active restaurant by default or aggregated globally when "All Restaurants" is selected.
- **FR-008**: The manager portal MUST support restaurant scoping via an interactive dropdown selector populated dynamically from the restaurant catalog, defaulting to the first available establishment with an explicit "All Restaurants (Platform Total)" option.
- **FR-009**: The manager portal MUST provide full reservation management capabilities: listing reservations by restaurant and date, filtering by status, updating reservation status (`ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`), and cancelling reservations. Before executing any reservation cancellation, the portal MUST require explicit manager confirmation via a confirmation modal dialog.
- **FR-010**: The manager portal MUST allow managers to create reservations, including past-time reservations for retroactive walk-in back-filling.
- **FR-011**: The manager portal MUST provide restaurant management capabilities: viewing restaurant profiles, registering new restaurants, adding dining tables (table number, capacity, zone), configuring table combinations, and modifying operating hours.
- **FR-012**: The manager portal MUST provide real-time table availability lookups and display active waiting list entries via a dedicated `GET /api/v1/waiting-list` endpoint in `waiting-list-service` (accessible to `ROLE_RESTAURANT_MANAGER` and `ROLE_ADMIN`).
- **FR-013**: Backend microservices MUST remain unaffected, EXCEPT for (1) adding the manager-facing waiting list query endpoint (`GET /api/v1/waiting-list`) to `waiting-list-service` and (2) configuring Keycloak/Gateway CORS and redirect settings to enable browser-based authentication.

### Key Entities *(include if feature involves data)*

- **Manager Session**: Represents an active authenticated session for a restaurant manager or administrator, containing user profile information, granted roles, and security tokens.
- **Restaurant Profile**: Represents an establishment under management, including its unique identifier, name, address, timezone, operating hours, and dining table inventory.
- **Reservation Record**: Represents a scheduled or historical dining booking, including guest details, party size, reservation start time, dining table assignment, and current status.
- **Operational Analytics Summary**: Aggregated metrics reflecting restaurant performance, including reservation counts, cancellation volumes, no-show tallies, waitlist entries, and conversion ratios.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of unauthenticated attempts to view manager operational data or controls are blocked at the portal entrance.
- **SC-002**: 100% of authenticated users with non-manager roles receive an access denied notice and cannot execute management actions.
- **SC-003**: Analytics summary metrics load successfully on first render without 404 or authorization failures for authenticated managers.
- **SC-004**: Managers can complete a reservation status update (e.g. marking a party arrived) in under 3 clicks from the reservation management view.
- **SC-005**: All management functions in the system (restaurants, tables, operating hours, reservations, availability, waiting list, analytics) are accessible from a single unified portal interface.
- **SC-006**: Portal UI handles API network or service failures gracefully with human-readable error messages and retry controls without unhandled JavaScript exceptions.

## Assumptions

- **Keycloak Realm and Client**: The Keycloak realm `rube-goldberg` with client `rube-goldberg-app` is available and configured to issue tokens containing realm roles (`ROLE_RESTAURANT_MANAGER`, `ROLE_ADMIN`, `ROLE_CUSTOMER`).
- **Browser Compatibility**: The manager portal is designed for modern desktop and tablet browsers supporting modern JavaScript standards and Bootstrap 5.
- **Backend Role Enforcement**: Backend microservices already enforce OAuth 2.0 Resource Server RBAC rules and will validate incoming bearer tokens forwarded by the portal.
- **Session Lifespan**: Access tokens have standard validity periods; the portal will handle expired tokens by prompting re-authentication or refreshing tokens.
