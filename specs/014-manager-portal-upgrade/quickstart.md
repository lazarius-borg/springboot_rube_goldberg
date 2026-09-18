# Quickstart & Verification Guide: Manager Portal Upgrade

**Feature**: `014-manager-portal-upgrade`  
**Date**: 2026-09-16  
**Status**: Ready for Verification

---

## 1. Prerequisites & Environment Setup

Ensure the infrastructure containers and microservices are running:

```bash
# Start infrastructure services (Keycloak, Postgres, Redis, Kafka)
docker compose -f infrastructure/docker-compose.yml up -d

# Start backend microservices and API Gateway
docker compose -f infrastructure/docker-compose.apps.yml up -d
```

### Keycloak Credentials (Realm: `rube-goldberg`, Client: `rube-goldberg-app`)
| Role | Username | Password | Expected Portal Outcome |
|------|----------|----------|-------------------------|
| **Restaurant Manager** | `manager1` | `password` | Unlocks full manager operations dashboard |
| **Platform Administrator** | `admin1` | `password` | Unlocks full manager & admin operations |
| **Regular Customer** | `customer1` | `password` | Access Denied banner (insufficient role) |

---

## 2. Verification Scenarios

### Scenario 1: Unauthenticated Gatekeeper
1. Open a clean browser window / incognito tab and navigate to:
   `http://localhost:8080/ui/manager/index.html`
2. **Expected Outcome**:
   - Management controls and analytics cards are **hidden**.
   - A secure login prompt is displayed: *"Restaurant Manager Authentication Required"*.
   - A prominent *"Sign in with Keycloak"* button is visible.
   - No unauthorized background API requests are dispatched.

---

### Scenario 2: Role Authorization Enforcement (Customer Attempt)
1. On the login screen, click *"Sign in with Keycloak"*.
2. You will be redirected to the Keycloak login screen (`http://localhost:8081/realms/rube-goldberg/...`).
3. Enter credentials:
   - Username: `customer1`
   - Password: `password`
4. Submit the login form. Keycloak redirects back to `http://localhost:8080/ui/manager/index.html`.
5. **Expected Outcome**:
   - The portal evaluates `tokenParsed.realm_access.roles`.
   - Access is **denied**.
   - An alert is displayed: *"Access Denied: You are signed in as customer1 (ROLE_CUSTOMER), but Restaurant Manager or Admin privileges are required."*
   - A *"Sign Out"* button is provided to clear the session.

---

### Scenario 3: Manager Authentication & Analytics Restoration
1. Click *"Sign Out"*, then click *"Sign in with Keycloak"*.
2. Enter manager credentials:
   - Username: `manager1`
   - Password: `password`
3. Submit the form. Keycloak redirects back to the portal.
4. **Expected Outcome**:
   - The portal recognizes `ROLE_RESTAURANT_MANAGER`.
   - The manager dashboard unlocks with user badge: *"Bob Manager (RESTAURANT_MANAGER)"*.
   - The restaurant selector dropdown appears populated with registered restaurants and the *"All Restaurants (Platform Total)"* option.
   - The Live Analytics section renders without errors (no 404 on `/api/v1/analytics/summary`).
   - The KPI cards display live totals: Total Reservations, Cancellations, No-Shows, Waiting List Entries, and Conversion Rate.

---

### Scenario 4: Restaurant Scoping & Global Aggregates
1. In the navbar restaurant dropdown, select a specific restaurant.
2. **Expected Outcome**:
   - Analytics metrics refresh to reflect numbers for that establishment.
   - The Reservations tab filters to bookings for the chosen restaurant.
   - The Floor & Tables tab displays tables belonging to that restaurant.
3. Switch the dropdown to *"All Restaurants (Platform Total)"*.
4. **Expected Outcome**:
   - Analytics summary updates to show platform-wide aggregate counts.

---

### Scenario 5: Reservation Lifecycle Management & Back-Filling
1. Navigate to the **Reservations** tab.
2. View existing bookings for the selected restaurant.
3. For a reservation in `CONFIRMED` status, click *"Arrived"*.
   - **Expected Outcome**: Status badge changes to `ARRIVED` (via `PATCH /api/v1/reservations/{id}/status`).
4. Click *"Complete"*.
   - **Expected Outcome**: Status badge changes to `COMPLETED`.
5. Click *"New Reservation / Back-Fill"*.
6. Enter guest details with a past dining time for today (e.g., 2 hours ago).
7. Submit the booking.
   - **Expected Outcome**: The retroactive reservation is confirmed and displayed with a `CONFIRMED` badge under manager back-filling privileges.

---

### Scenario 6: Restaurant Floor & Table Management
1. Navigate to the **Floor & Tables** tab.
2. Inspect the current dining table inventory and operating hours.
3. Fill in the "Add Table" form: Table Number `T99`, Capacity `6`, Zone `Main Dining`. Click *"Add Table"*.
4. **Expected Outcome**:
   - Table is created (via `POST /api/v1/restaurants/{id}/tables`) and appears immediately in the floor table list.

---

### Scenario 7: Waiting List Oversight & Query Endpoint
1. Navigate to the **Waiting List** tab.
2. **Expected Outcome**:
   - The portal queries `GET /api/v1/waiting-list?restaurantId=<uuid>`.
   - Active waitlist entries are displayed in a table with guest email, target date, requested time window, party size, and chronological queue placement.

---

### Scenario 8: Sign-Out Verification
1. Click the *"Sign Out"* button in the navbar.
2. **Expected Outcome**:
   - Keycloak session is terminated.
   - Local tokens are purged.
   - Portal returns to the unauthenticated login prompt.
