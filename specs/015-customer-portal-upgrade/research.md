# Technical Research: Customer Portal Upgrade

**Feature**: Customer Portal Upgrade (`015-customer-portal-upgrade`)
**Date**: 2026-09-17

## Overview & Scope

The Customer Portal Upgrade introduces an intuitive, authenticated, real-time single-page web interface for restaurant guests to discover table availability, create instant bookings, join fair FIFO waiting lists upon full capacity, inspect active reservations, cancel bookings, and receive push notifications via Server-Sent Events (SSE).

---

## Technical Decisions

### 1. Frontend Architecture & Asset Hosting

- **Decision**: Implement the Customer Portal as a lightweight Single Page Application (HTML5, Bootstrap 5.3, Vanilla ES6+ JavaScript) served by Spring Cloud Gateway from `classpath:/static/ui/customer/`.
- **Rationale**:
  - Eliminates the operational overhead of a separate Node.js frontend runtime or complex build pipeline while maintaining microservice architecture principles.
  - Matches the successful pattern implemented in `014-manager-portal-upgrade` (`ui/src/manager/` bundled into `gateway`).
  - Native browser APIs (`fetch`, `EventSource`, DOM manipulation) avoid large framework bundles and maintain sub-second loading performance.
- **Alternatives Considered**:
  - *React/Vue SPA*: Rejected due to added build complexity, bundling overhead, and divergence from existing static gateway assets.
  - *Server-Side Thymeleaf*: Rejected because real-time dynamic UI transitions (live countdown timers, dynamic availability switching, and SSE listeners) are better handled via client-side DOM manipulation.

### 2. Authentication & Role-Based Access Control

- **Decision**: Integrate Keycloak OIDC with Authorization Code Flow + PKCE (`check-sso` mode) using a bundled, self-hosted `keycloak.js` client adapter.
- **Role Verification**:
  - Inspect JWT claims (`realm_access.roles` and `resource_access`).
  - Accept `CUSTOMER` or `ROLE_CUSTOMER` as authorized customer roles; also allow `ADMIN` for operational previews.
  - Users without customer/admin privileges see a clear "Unauthorized / Missing Role" gate with a sign-out button.
- **Token Management**:
  - Proactive refresh before each authenticated API call (`keycloak.updateToken(10)`).
  - Include Bearer token in the `Authorization` header of all protected requests (`/api/v1/customers/me`, `/api/v1/reservations`, `/api/v1/waiting-list`, etc.).
- **Alternatives Considered**:
  - *Gateway-level OAuth2 login with session cookies*: Rejected to keep the gateway stateless and avoid distributed session stores (Redis) for UI navigation.

### 3. Server-Sent Events (SSE) Real-Time Architecture

- **Decision**: Host the Server-Sent Events (SSE) streaming endpoint in `notification-service` (`GET /api/v1/notifications/stream`), exposing it via Spring Cloud Gateway.
- **Service Placement Rationale**:
  - `notification-service` is already the platform's central messaging hub, consuming all Kafka event topics:
    - `reservation.events`: `ReservationCreatedEvent`, `ReservationCancelledEvent`
    - `waiting-list.events`: `WaitingListOfferCreatedEvent`
  - Bridging Kafka events to connected browser sessions via Spring MVC `SseEmitter` keeps domain services (`reservation-service` and `waiting-list-service`) completely decoupled from real-time client connection lifecycles.
- **Client Connection & Reconnection**:
  - Customer portal connects to `/api/v1/notifications/stream?token=<jwt_or_bearer>`.
  - The client listens for `WAITING_LIST_OFFER`, `RESERVATION_CONFIRMED`, and `RESERVATION_CANCELLED` events.
  - On disconnection, the client attempts exponential-backoff reconnection and falls back to periodic status polling.
- **Alternatives Considered**:
  - *WebSockets*: Rejected due to added protocol complexity (STOMP brokers, bidirectional framing) when requirements only call for server-to-client push.
  - *Polling Only*: Rejected per explicit user architectural decision to use Server-Sent Events for instant offer delivery.

### 4. Guided Table Search, Direct Booking & Waiting List Transition

- **Decision**: Provide a unified, adaptive search card that dynamically toggles between "Instant Booking" and "Join Waiting List".
- **Interaction Flow**:
  1. Customer selects a restaurant from a dynamically loaded dropdown, dining date, desired time, and party size.
  2. Submits to `GET /api/v1/availability?restaurantId=...&date=...&time=...&partySize=...`.
  3. **If available (`data.isAvailable === true`)**:
     - Render available slots.
     - Present an immediate "Book Table" button with customer identity pre-filled from JWT token.
     - On click: POST to `/api/v1/reservations`.
  4. **If unavailable (`data.isAvailable === false`)**:
     - Render "Fully Booked" visual banner.
     - Present an opt-in "Join Waiting List" prompt pre-populated with:
       - Selected restaurant, target date, party size.
       - Arrival time window spanning ±1 hour around requested time (e.g. 18:00–20:00 for a 19:00 search) with inline time controls.
     - Customer is placed on the waiting list only upon clicking "Confirm Join Waiting List".
- **Alternatives Considered**:
  - *Separate disconnected tabs for search vs waiting list*: Rejected as disjointed; users shouldn't have to re-enter restaurant, date, and party size when a slot is full.

### 5. Managing Reservations & Waiting List Offers

- **Decision**:
  - Customer's active bookings and waiting list entries are displayed in responsive cards/tables.
  - Cancel button on active reservations triggers a confirmation modal before sending `DELETE /api/v1/reservations/{id}`.
  - Waiting list entries in `OFFERED` status render a high-priority callout with an active 15-minute countdown timer (derived from `expiresAt`) and a direct "Accept Offer" button sending `POST /api/v1/waiting-list/offers/{offerId}/accept`.
  - Customers can voluntarily leave the waiting list via a "Leave Waiting List" action.

---

## Constitution & Governance Alignment

- **Strict Specification Adherence (Principle I)**: All requirements from `spec.md` and user clarifications (SSE, Keycloak auth, conditional waiting list) are directly honored.
- **Maven Reactor Architecture (Principle II)**: Preserves existing submodule boundaries; `notification-service` is extended cleanly with Spring Web / Security endpoints.
- **Modern Spring Boot Standards (Principle III)**: Uses Spring MVC `SseEmitter`, declarative security, Jackson DTOs, and Spring Cloud Gateway routes.
- **Testing & Quality Gates (Principle V)**: Unit and slice tests will be added for the new SSE endpoint and security config in `notification-service`.
