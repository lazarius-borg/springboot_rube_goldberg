# Technical Research & Architecture Decisions: Manager Portal Upgrade

**Feature**: `014-manager-portal-upgrade`  
**Date**: 2026-09-16  
**Status**: Completed

---

## 1. Authentication & Security: Keycloak OIDC with PKCE

### Decision
Implement authentication in the frontend manager portal using Keycloak's standard OpenID Connect (OIDC) Authorization Code Flow with Proof Key for Code Exchange (PKCE) via the official `keycloak-js` adapter (version 26.1.0 to match the Keycloak container version).

### Rationale
1. **Industry Standard for SPAs**: Authorization Code Flow with PKCE (RFC 7636) is the OAuth 2.0 / OIDC security standard for browser-based single-page applications without a backend client secret.
2. **Existing Infrastructure Alignment**: Keycloak realm `rube-goldberg` with public client `rube-goldberg-app` is already provisioned with `standardFlowEnabled: true`, `publicClient: true`, and wildcard redirect URIs (`redirectUris: ["*"]`, `webOrigins: ["*"]`).
3. **Role Enforcement**: `keycloak.tokenParsed.realm_access.roles` provides client-side role inspection to gate the manager UI. If the authenticated user lacks `ROLE_RESTAURANT_MANAGER` or `ROLE_ADMIN`, access is immediately denied and non-privileged API requests are prevented.
4. **Token Management**: The adapter automatically handles token expiration, code exchange, and preemptive token refreshes (`keycloak.updateToken(30)`).

### Alternatives Considered
- **Direct Grant (Resource Owner Password Credentials)**: Deprecated in OAuth 2.1, requires custom credential form, exposes user passwords to client application script. Rejected in favor of standard redirect with PKCE.
- **Backend-for-Frontend (BFF) Session Cookies**: Requires stateful session management or Spring Cloud Gateway token relay filters with Redis sessions. Rejected to keep backend changes minimal and preserve microservice statelessness.

---

## 2. API Communication & Token Forwarding

### Decision
All outgoing API requests to backend microservices (via the API Gateway at port 8080) must be wrapped with a centralized HTTP client helper that:
1. Calls `keycloak.updateToken(10)` to ensure the access token is valid (refreshing if within 10 seconds of expiration).
2. Attaches `Authorization: Bearer <token>` header to all requests.
3. Handles HTTP 401/403 responses by triggering re-authentication or displaying a permissions error banner.
4. Parses RFC 7807 `ProblemDetail` error payloads into human-readable notifications.

### Rationale
Eliminates code duplication across management views, guarantees valid tokens on every HTTP call, and handles token expiration gracefully without losing page state.

---

## 3. Resolving the `/api/v1/analytics/summary` 404 / Unauthorized Issue

### Decision
1. Update `loadAnalytics(restaurantId)` to send the authenticated Bearer token and append `?restaurantId=<uuid>` if a specific restaurant is selected.
2. When "All Restaurants" is selected, query `/api/v1/analytics/summary` without the `restaurantId` query parameter to retrieve platform-wide aggregated KPIs.
3. Replace silent error suppression (`catch (ignored) {}`) with explicit UI status handling: loading spinners, metric value updates, and scoped retry banners on failure.

### Rationale
In `analytics-service`, `AnalyticsController` defines `@GetMapping("/summary")` under `/api/v1/analytics`. The route is configured in Gateway (`Path=/api/v1/analytics/**`). Previously, calls failed because:
- The request lacked an `Authorization` header, triggering a 401 Unauthorized from Spring Security (misinterpreted or surfaced as route failure).
- The frontend code suppressed all errors silently.
Attaching valid manager tokens and adding robust error handling restores real-time KPI metrics.

---

## 4. Restaurant Scoping & Context Management

### Decision
1. On initial portal load after authentication, fetch all restaurants via `GET /api/v1/restaurants`.
2. Populate an interactive `<select>` dropdown in the portal header with options:
   - Specific restaurants: `<Restaurant Name> (<Address>)`
   - Platform Total: `All Restaurants (Platform Total)`
3. Default the selection to the first registered restaurant.
4. Whenever the dropdown selection changes:
   - Refresh analytics summary for the selected restaurant (or global if "All Restaurants" is chosen).
   - Refresh reservations list for the selected restaurant.
   - Refresh table inventory for the selected restaurant.
   - Refresh waiting list queue for the selected restaurant.

### Rationale
Meets user preference for dynamic dropdown scoping while supporting both establishment-level and platform-wide visibility.

---

## 5. Waiting List Oversight Backend Integration

### Decision
Add a manager-facing query endpoint to `waiting-list-service`:
- **Endpoint**: `GET /api/v1/waiting-list`
- **Security**: `@PreAuthorize("hasAnyRole('RESTAURANT_MANAGER', 'ADMIN')")` / `requestMatchers(HttpMethod.GET, "/api/v1/waiting-list/**").hasAnyRole("RESTAURANT_MANAGER", "ADMIN")`
- **Parameters**: `restaurantId` (UUID, required), `targetDate` (LocalDate, optional), `status` (String, optional, defaults to `"WAITING"`)
- **Response**: List of `WaitingListEntryDto` records sorted chronologically by queue placement (`createdAt` ASC).

### Rationale
Per the user's explicit decision in clarification, this targeted endpoint provides true queue visibility for on-shift managers without altering any existing business rules or state transition flows.

---

## 6. Manager Portal UI Architecture & Modules

### Decision
Organize the manager portal (`ui/src/manager/` and `gateway/src/main/resources/static/ui/manager/`) into a responsive tabbed interface using Bootstrap 5:
1. **Navbar**: Brand title, Restaurant Dropdown Selector, User Identity Badge (`Bob (Manager)`), and Sign Out button.
2. **Access Gate Screen**: Rendered when unauthenticated (Keycloak Login button) or unauthorized (role mismatch banner).
3. **Tab 1: Live Analytics**: Total Reservations, Cancellations, No-Shows, Waiting List Volume, and Conversion Rate % cards with auto-refresh every 10s and manual refresh button.
4. **Tab 2: Reservation Management**: Date picker, status filter, tabular list of reservations, status transition buttons (`Arrive`, `Complete`, `No-Show`, `Cancel`), and "New Reservation / Back-fill" modal form.
5. **Tab 3: Restaurant Floor & Tables**: Registered venue details, opening hours editor, add table form, table list with capacities and zones, table combination configuration.
6. **Tab 4: Availability & Waitlist Queue**: Real-time table availability check tool (supports past checks for managers) and live tabular view of guests on the waiting list queue.

### Rationale
Keeps all operational workflows accessible in a single coherent SPA without external page reloads, aligning with modern dashboard patterns.
