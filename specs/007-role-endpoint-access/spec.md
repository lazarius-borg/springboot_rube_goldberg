# Feature Specification: Role-Based Access Control (RBAC) & Endpoint Authorization

**Feature Branch**: `007-role-endpoint-access`  
**Created**: 2026-09-14  
**Status**: Draft  
**Input**: User description: "role to endpoint matching - Although the endpoints require an authenticated user, any authenticated user can use any endpoint. There are 3 defined roles in Keycloak, ADMIN, RESTAURANT_MANAGER and CUSTOMER. It should clear in the documentation (README) what are the responsibilites of each role, and also, to limit access to endpoints to roles. For example, a user with CUSTOMER role can not access the endpoinmt for registering a restoraunt, to give but a one example, or a user with ADMIN or RESTAURANT_MANAGER role can not make a reservation unless that user has also a CUSTOMER role associated with it. Having multiple roles doesn't make much sense unless there is a proper RBAC assoicated with the endpoints. The README MUST be updaetd to reflect the changes, and also the 'Interactive End-to-End Walkthrough' should use the actual ports assigned to the services in the step by step examples. The rest of the functionality MUST not be affected."

---

## Clarifications

### Session 2026-09-14

- Q: Should `analytics-service` be secured as an OAuth2 Resource Server requiring `RESTAURANT_MANAGER` or `ADMIN` role for the `/api/v1/analytics/summary` endpoint? → A: Secure `analytics-service` with OAuth2 Resource Server requiring `RESTAURANT_MANAGER` and `ADMIN` role for `/api/v1/analytics/**`.
- Q: For individual reservation endpoints (`GET /api/v1/reservations/{id}` and `DELETE /api/v1/reservations/{id}`), should access be granted to `CUSTOMER` as well as `RESTAURANT_MANAGER` and `ADMIN`? → A: Permit `CUSTOMER`, `RESTAURANT_MANAGER`, and `ADMIN` to view and cancel individual reservations (`GET /api/v1/reservations/{id}` and `DELETE /api/v1/reservations/{id}`).
- Q: Who should be permitted to query restaurant catalog listings and real-time table availability (`GET /api/v1/restaurants`, `GET /api/v1/restaurants/{id}`, `GET /api/v1/restaurants/{id}/opening-hours`, and `GET /api/v1/availability`)? → A: Permit all authenticated users holding any valid role (`CUSTOMER`, `RESTAURANT_MANAGER`, or `ADMIN`) to read restaurant information and query availability.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Restrict Restaurant Management Operations to Authorized Managers and Administrators (Priority: P1)

As a restaurant owner or platform administrator,  
I want administrative endpoints (such as registering establishments, adding tables, defining table combinations, configuring schedules, updating reservation arrival/completion statuses, and viewing operational analytics) to be strictly limited to users possessing `RESTAURANT_MANAGER` or `ADMIN` roles,  
so that customers and unauthorized callers cannot alter restaurant inventory, manipulate business schedules, or view internal operational performance.

**Why this priority**: Preventing unauthorized modification of dining inventory and business policies is foundational to platform security and operational integrity.

**Independent Test**: Can be tested by attempting to register a restaurant or add tables using a token belonging to `customer1` (role `CUSTOMER`), verifying that the system rejects the operation with an HTTP 403 Forbidden response, while the same requests succeed with HTTP 201 Created when presented with a token from `manager1` (`RESTAURANT_MANAGER`) or `admin1` (`ADMIN`).

**Acceptance Scenarios**:

1. **Given** an authenticated user with only the `CUSTOMER` role, **When** the user submits a request to register a restaurant (`POST /api/v1/restaurants`), **Then** the request is rejected with HTTP 403 Forbidden.
2. **Given** an authenticated user with only the `CUSTOMER` role, **When** the user attempts to add a table, define table combinations, or configure opening hours on a restaurant, **Then** the service rejects the request with HTTP 403 Forbidden.
3. **Given** an authenticated user with the `RESTAURANT_MANAGER` or `ADMIN` role, **When** the user submits a valid restaurant registration, table creation, combination definition, or schedule update, **Then** the request is accepted and processed successfully.
4. **Given** an authenticated user with the `RESTAURANT_MANAGER` or `ADMIN` role, **When** the user queries restaurant reservation lists (`GET /api/v1/reservations?restaurantId=...`) or updates reservation lifecycle status (`PATCH /api/v1/reservations/{id}/status`), **Then** the request is authorized and processed.

---

### User Story 2 - Restrict Dining Operations to Authorized Customers (Priority: P1)

As a dining customer,  
I want customer-specific workflows (such as creating table reservations, joining fair FIFO waiting lists, claiming cancellation offers, and accessing customer profiles) to require the `CUSTOMER` role,  
so that operations represent legitimate customer identities and users possessing solely administrative or manager credentials cannot book dining slots without being explicitly granted the `CUSTOMER` role.

**Why this priority**: Booking tables, managing waitlist positions, and modifying customer profile data represent customer actions; enforcing the `CUSTOMER` role ensures proper separation of duties and prevents role confusion across the reservation lifecycle.

**Independent Test**: Can be tested by attempting to create a table reservation (`POST /api/v1/reservations`) or join the waiting list (`POST /api/v1/waiting-list`) using a token that only has `RESTAURANT_MANAGER` or `ADMIN` roles (e.g. `manager1` or `admin1`), verifying that the request is rejected with HTTP 403 Forbidden, and succeeds with HTTP 201 Created only when using a token containing the `CUSTOMER` role (e.g. `customer1` or a multi-role user).

**Acceptance Scenarios**:

1. **Given** an authenticated user with only the `RESTAURANT_MANAGER` or `ADMIN` role, **When** the user attempts to create a guaranteed reservation (`POST /api/v1/reservations`), **Then** the service rejects the request with HTTP 403 Forbidden.
2. **Given** an authenticated user with only the `RESTAURANT_MANAGER` or `ADMIN` role, **When** the user attempts to join a waiting list (`POST /api/v1/waiting-list`) or accept a waiting list offer (`POST /api/v1/waiting-list/offers/{offerId}/accept`), **Then** the service rejects the request with HTTP 403 Forbidden.
3. **Given** an authenticated user possessing the `CUSTOMER` role (including users with multiple roles where `CUSTOMER` is present), **When** the user submits a reservation request, joins a waiting list, or claims an offer, **Then** the request is authorized and processed.
4. **Given** an authenticated user with the `CUSTOMER` role, **When** the user accesses or updates their personal profile (`GET /api/v1/customers/me`, `PUT /api/v1/customers/me`), **Then** the operation is authorized.

---

### User Story 3 - Role Responsibilities Documentation & Port-Accurate Walkthrough (Priority: P2)

As a developer, tester, or platform operator,  
I want the platform documentation (`README.md`) to clearly describe the responsibilities and permission matrices of each Keycloak role (`CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`), and update the "Interactive End-to-End Walkthrough" to use actual assigned microservice ports and appropriate role-authenticated requests,  
so that anyone inspecting or testing the system can reliably reproduce end-to-end workflows directly against individual services without guessing ports or encountering unexpected permission failures.

**Why this priority**: Clear documentation and accurate walkthrough examples align platform expectations, avoid confusion between Gateway and direct service endpoints, and demonstrate working RBAC in practice.

**Independent Test**: Can be tested by following the updated "Interactive End-to-End Walkthrough" steps in `README.md` verbatim using command-line requests targeted directly at each microservice's dedicated port (e.g. 8083 for restaurant, 8084 for availability, 8085 for reservations, 8086 for waiting list, 8087 for analytics) with corresponding role tokens, confirming every step executes cleanly as documented.

**Acceptance Scenarios**:

1. **Given** the `README.md` file, **When** reviewing the security section, **Then** a dedicated Role-Based Access Control (RBAC) matrix outlines the exact privileges and responsibilities of `CUSTOMER`, `RESTAURANT_MANAGER`, and `ADMIN`.
2. **Given** the "Interactive End-to-End Walkthrough" in `README.md`, **When** executing the step-by-step commands, **Then** each step explicitly targets the service's designated port (Restaurant: `8083`, Availability: `8084`, Reservation: `8085`, Waiting List: `8086`, Analytics: `8087`) rather than the shared gateway port `8080`.
3. **Given** steps in the walkthrough requiring specific privileges (e.g. restaurant registration vs table booking), **When** following the instructions, **Then** the examples illustrate acquiring and passing the appropriate token (`manager1` for management operations, `customer1` for booking operations).

---

### Edge Cases

- **User with Multiple Assigned Roles**: A user account assigned both `CUSTOMER` and `RESTAURANT_MANAGER` roles must be permitted to both manage restaurants and create dining reservations.
- **Unauthenticated Requests**: Any request lacking a valid authentication token must continue to receive HTTP 401 Unauthorized, distinguishing unauthenticated callers from authenticated callers lacking privileges (HTTP 403 Forbidden).
- **Public Read Endpoints**: Publicly discoverable read operations such as listing restaurants (`GET /api/v1/restaurants`), viewing restaurant opening hours (`GET /api/v1/restaurants/{id}/opening-hours`), and querying real-time table availability (`GET /api/v1/availability`) must remain accessible to any authenticated user regardless of role (or unauthenticated where specified for public catalog browsing).
- **Interactive API Documentation (Swagger UI)**: Swagger UI and OpenAPI documentation endpoints (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`) must remain publicly accessible without requiring authentication tokens or roles.
- **Actuator Health and Metrics**: Standard health probes (`/actuator/health`) must remain accessible according to existing monitoring configuration without breaking container readiness probes.
- **Default-Deny for Unlisted Endpoints**: Any endpoint not explicitly matched by a public `permitAll` rule MUST require valid authentication and role-based authorization by default; unauthenticated requests receive HTTP 401 Unauthorized and unauthorized requests receive HTTP 403 Forbidden.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Services MUST enforce Role-Based Access Control (RBAC) on all protected business API endpoints using role claims conveyed in authenticated identity tokens.
- **FR-002**: Endpoints for creating restaurants, modifying dining tables, defining table combinations, and updating opening hour schedules MUST be restricted exclusively to callers possessing the `RESTAURANT_MANAGER` or `ADMIN` role.
- **FR-003**: Endpoints for creating dining reservations (`POST /api/v1/reservations`), joining waiting lists (`POST /api/v1/waiting-list`), claiming waiting list offers (`POST /api/v1/waiting-list/offers/{offerId}/accept`), and accessing customer profile data (`/api/v1/customers/me`) MUST be restricted exclusively to callers possessing the `CUSTOMER` role.
- **FR-004**: Endpoints for listing restaurant-wide reservations (`GET /api/v1/reservations?restaurantId=...`) and updating reservation lifecycle statuses (`PATCH /api/v1/reservations/{id}/status`) MUST be restricted to callers possessing the `RESTAURANT_MANAGER` or `ADMIN` role.
- **FR-005**: Authenticated callers attempting to invoke an endpoint without the required role MUST receive an HTTP 403 Forbidden response.
- **FR-006**: Existing token validation capabilities (including dual-issuer verification for external `localhost:8081` and internal `keycloak:8080` addresses across Spring profiles) and cryptographic signature checks MUST remain fully intact.
- **FR-007**: The project documentation (`README.md`) MUST provide a comprehensive role matrix clearly articulating the responsibilities and allowed operations for `CUSTOMER`, `RESTAURANT_MANAGER`, and `ADMIN`.
- **FR-008**: The "Interactive End-to-End Walkthrough" in `README.md` MUST be updated to target the assigned direct microservice ports (Restaurant Service `8083`, Availability Service `8084`, Reservation Service `8085`, Waiting List Service `8086`, Analytics Service `8087`) and demonstrate authentication using role-appropriate credentials (`manager1` vs `customer1`).
- **FR-009**: `analytics-service` MUST be secured as an OAuth2 Resource Server restricting access to `/api/v1/analytics/**` to callers possessing the `RESTAURANT_MANAGER` or `ADMIN` role.
- **FR-010**: Endpoints for retrieving individual reservation details (`GET /api/v1/reservations/{id}`) and cancelling a reservation (`DELETE /api/v1/reservations/{id}`) MUST be accessible to callers possessing `CUSTOMER`, `RESTAURANT_MANAGER`, or `ADMIN` roles.
- **FR-011**: Endpoints for querying restaurant catalog information (`GET /api/v1/restaurants`, `GET /api/v1/restaurants/{id}`, `GET /api/v1/restaurants/{id}/opening-hours`) and real-time table availability (`GET /api/v1/availability`) MUST be accessible to any authenticated caller possessing at least one valid platform role (`CUSTOMER`, `RESTAURANT_MANAGER`, or `ADMIN`).
- **FR-012**: Services MUST enforce a default-deny authorization policy such that any unlisted, newly introduced, or unmapped endpoints require explicit authentication and authorized role membership rather than failing open.

---

### Key Entities *(include if feature involves data)*

- **Role**: A named authorization permission defined within the security realm (`CUSTOMER`, `RESTAURANT_MANAGER`, `ADMIN`) assigned to user identities and encoded into issued identity tokens.
- **User Identity**: A security principal representing an actor in the system (`customer1`, `manager1`, `admin1`), identified by a unique subject identifier and associated with one or more realm roles.
- **Protected Endpoint**: A REST API resource path requiring both valid authentication and specific role authorization to execute.
- **Access Privilege**: The mapping establishing whether a given role is permitted, forbidden, or unrestricted for each specific HTTP method and resource path.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of attempts by `customer1` to invoke administrative endpoints (`POST /api/v1/restaurants`, table setup, status updates) result in HTTP 403 Forbidden.
- **SC-002**: 100% of attempts by users possessing only `RESTAURANT_MANAGER` or `ADMIN` to create reservations (`POST /api/v1/reservations`) or join waitlists result in HTTP 403 Forbidden unless the `CUSTOMER` role is also assigned.
- **SC-003**: 100% of steps in the updated "Interactive End-to-End Walkthrough" in `README.md` execute successfully using their designated microservice port and corresponding role token.
- **SC-004**: Zero functional regressions in event publishing, asynchronous processing, table allocation algorithms, or dual-issuer token decoding.

---

## Assumptions

- Keycloak realm configuration (`infrastructure/keycloak/realm-export.json`) already provides the roles `CUSTOMER`, `RESTAURANT_MANAGER`, and `ADMIN` assigned to `customer1`, `manager1`, and `admin1` respectively.
- Role claims are conveyed in standard Keycloak token structures (`realm_access.roles`) and can be mapped into Spring Security granted authorities.
- Swagger UI interactive exploration will continue to allow users to authorize with either `customer1`, `manager1`, or `admin1` tokens, with Swagger executing requests according to the user's role permissions.
- Direct port access to microservices (`8082`–`8087`) is supported in both local IDE runs and standard Docker Compose port mappings (`ports:` section of `docker-compose.apps.yml`).
