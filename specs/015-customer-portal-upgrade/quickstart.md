# Quickstart & Verification Guide: Customer Portal Upgrade

**Feature**: Customer Portal Upgrade (`015-customer-portal-upgrade`)
**Date**: 2026-09-17

This guide outlines end-to-end testing scenarios to validate the Customer Portal features and real-time Server-Sent Events integration.

---

## Prerequisites

1. **Docker Infrastructure & Microservices Running**:
   ```bash
   docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml ps
   ```
   Ensure `rube-gateway` (port 8080), `rube-keycloak` (port 8081), `rube-notification-service` (port 8088), Kafka, and Postgres containers are healthy.

2. **Test Credentials in Keycloak**:
   - Customer User: `alice` / `password` (assigned role `ROLE_CUSTOMER` or `CUSTOMER`)
   - Manager User: `manager` / `password` (role `ROLE_RESTAURANT_MANAGER`)
   - Admin User: `admin` / `password` (role `ROLE_ADMIN`)

---

## Scenario 1: Unauthenticated Gate & Keycloak Login

1. Open a browser and navigate to:
   ```text
   http://localhost:8080/ui/customer/index.html
   ```
2. **Expected Outcome**:
   - The customer dashboard is completely hidden.
   - An unauthenticated landing card is displayed with the title "Customer Dining Portal" and a prominent "Sign in with Keycloak" button.
3. Click "Sign in with Keycloak" and log in as `alice` / `password`.
4. **Expected Outcome**:
   - Redirects back to `http://localhost:8080/ui/customer/index.html`.
   - The sign-in gate disappears.
   - Header displays "Welcome, Alice (Customer)" alongside a "Sign Out" button.
   - The table search card, upcoming reservations table, and active waiting list queue become interactive.

---

## Scenario 2: Instant Booking on Available Dining Slot

1. In the **Search Table Availability** section:
   - Select a restaurant from the dropdown.
   - Pick a future date (e.g. tomorrow).
   - Select time `19:00`.
   - Set guests to `2`.
   - Click "Check Availability".
2. **Expected Outcome**:
   - The result panel shows "✅ Available! Open slots: 18:30, 19:00, 19:30".
   - An immediate "Book Confirmed Reservation" action appears with Alice's contact details pre-filled.
3. Click "Book Confirmed Reservation".
4. **Expected Outcome**:
   - Instant confirmation alert displaying the reservation UUID reference.
   - The new booking immediately appears under **Upcoming Reservations** showing status `CONFIRMED`.

---

## Scenario 3: Fully Booked Slot & Guided Waiting List Opt-In

1. Search for a dining slot where all tables for the party size are already booked.
2. Click "Check Availability".
3. **Expected Outcome**:
   - Result displays: "❌ Fully Booked for the requested time."
   - The direct booking button is NOT shown.
   - An opt-in card appears: "Join the Fair Waiting List".
   - The arrival window is pre-populated to `18:00` - `20:00` (±1 hour around 19:00).
4. Customer reviews the window, optionally adjusts times, and clicks "Join Waiting List".
5. **Expected Outcome**:
   - Notification confirms placement on the FIFO waiting queue.
   - The entry appears under **Active Waiting Lists** with status `WAITING`.

---

## Scenario 4: Self-Service Reservation Cancellation

1. Under **Upcoming Reservations**, locate an active booking.
2. Click the red "Cancel" button.
3. **Expected Outcome**:
   - A modal dialog appears asking: *"Are you sure you want to cancel your reservation for [Restaurant] on [Date] at [Time]?"*
4. Click "Confirm Cancellation".
5. **Expected Outcome**:
   - Status transitions to `CANCELLED`.
   - Released tables become immediately available in table inventory.

---

## Scenario 5: Real-Time Push & Waiting List Offer Countdown (SSE)

1. Open browser developer console (F12) -> Network tab -> EventStream or Console.
2. Observe the active connection to:
   ```text
   GET http://localhost:8080/api/v1/notifications/stream
   ```
3. Trigger a cancellation opening that matches Alice's waiting list entry.
4. **Expected Outcome**:
   - An instant `WAITING_LIST_OFFER` event is pushed via SSE to the browser without polling.
   - The waiting list entry flashes with a prominent notification callout.
   - An active 15-minute countdown timer begins ticking down (`14:59`, `14:58`...).
   - A green "Accept Offer" button is displayed.
5. Click "Accept Offer".
6. **Expected Outcome**:
   - Offer converts to a confirmed reservation.
   - The entry transitions to `CONVERTED` and moves to **Upcoming Reservations**.
