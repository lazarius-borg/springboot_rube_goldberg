# Quickstart Validation Guide: Platform Functionality & Usability Fixes

**Feature**: `016-platform-functionality-fixes`  
**Date**: 2026-09-17  
**Status**: Approved  

---

## 1. Prerequisites

1. Running Docker environment (`rube-postgres`, `rube-kafka`, `rube-keycloak`, `rube-gateway`, and core services).
2. Keycloak tokens:
   - Manager token: user `manager1` / `password` (`RESTAURANT_MANAGER` role)
   - Customer token: user `customer1` / `password` (`CUSTOMER` role)
3. Set environment variables:
   ```bash
   export GATEWAY_URL="http://localhost:8080"
   export RESTAURANT_ID="d13e9a12-887e-4054-9467-f31ea3a660d5"
   ```

---

## 2. Step-by-Step Validation Scenarios

### Scenario 1: Table Creation with Floor Zone
1. **Action**: Submit table creation request with zone:
   ```bash
   curl -s -X POST "$GATEWAY_URL/api/v1/restaurants/$RESTAURANT_ID/tables" \
     -H "Authorization: Bearer $MANAGER_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"tableNumber":"T-ROOF-1","capacity":4,"zone":"Rooftop"}'
   ```
2. **Verification**: HTTP 201 Created returned. Response body includes `"zone": "Rooftop"`. Listing tables (`GET /api/v1/restaurants/$RESTAURANT_ID/tables`) contains table with `zone: "Rooftop"`.

---

### Scenario 2: Table Combination Creation without 400 Bad Request
1. **Action**: Create combination omitting `name` and/or sending `combinedCapacity`:
   ```bash
   curl -s -X POST "$GATEWAY_URL/api/v1/restaurants/$RESTAURANT_ID/table-combinations" \
     -H "Authorization: Bearer $MANAGER_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"tableIds":["307325bf-b6d9-4ac2-9e28-9f7965e67689","532ba9fe-9d1a-42dc-a51b-b0c0d518e437"],"combinedCapacity":8}'
   ```
2. **Verification**: HTTP 201 Created. Response contains auto-generated name (e.g., `"Combo: T1 + T2"`) and `combinedCapacity: 8`.

---

### Scenario 3: Schedule Closed Days & Availability Block
1. **Action**: Configure Monday as closed:
   ```bash
   curl -s -X PUT "$GATEWAY_URL/api/v1/restaurants/$RESTAURANT_ID/opening-hours" \
     -H "Authorization: Bearer $MANAGER_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"schedules":[{"dayOfWeek":"MONDAY","isClosed":true}]}'
   ```
2. **Action**: Query availability for next Monday:
   ```bash
   curl -s "$GATEWAY_URL/api/v1/availability?restaurantId=$RESTAURANT_ID&date=2026-09-21&time=18:00:00&partySize=2"
   ```
3. **Verification**: `isAvailable: false`, `isClosed: true`, `reason: "RESTAURANT_CLOSED"`.

---

### Scenario 4: Table Edit & Safe Deletion
1. **Action**: Update table capacity and zone:
   ```bash
   curl -s -X PUT "$GATEWAY_URL/api/v1/restaurants/$RESTAURANT_ID/tables/$TABLE_ID" \
     -H "Authorization: Bearer $MANAGER_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"tableNumber":"T-ROOF-1","capacity":6,"zone":"VIP Rooftop"}'
   ```
2. **Verification**: HTTP 200 OK with updated capacity and zone.
3. **Action**: Attempt to delete an unbooked table:
   ```bash
   curl -s -X DELETE "$GATEWAY_URL/api/v1/restaurants/$RESTAURANT_ID/tables/$TABLE_ID" \
     -H "Authorization: Bearer $MANAGER_TOKEN"
   ```
4. **Verification**: HTTP 204 No Content. Table removed from active inventory.

---

### Scenario 5: Manager Reservation Overview Details
1. **Action**: List reservations for restaurant:
   ```bash
   curl -s "$GATEWAY_URL/api/v1/reservations?restaurantId=$RESTAURANT_ID&page=0&size=10" \
     -H "Authorization: Bearer $MANAGER_TOKEN"
   ```
2. **Verification**:
   - Confirmed reservations contain non-empty `allocatedTables` and `allocatedTableLabels` (e.g. `["T1"]`).
   - Cancelled reservations contain recorded `cancellationReason`.

---

### Scenario 6: Total Restaurant Capacity Enforcement
1. **Action**: Attempt to book simultaneous reservations that exceed total restaurant capacity:
2. **Verification**: System rejects booking with HTTP 409 Conflict once total restaurant capacity is exhausted, preventing overbooking.

---

### Scenario 7: Configurable Dining Duration & Customer Selection
1. **Action**: Book a reservation specifying 105 minutes duration:
   ```bash
   curl -s -X POST "$GATEWAY_URL/api/v1/reservations" \
     -H "Authorization: Bearer $CUSTOMER_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "restaurantId": "'"$RESTAURANT_ID"'",
       "partySize": 2,
       "startTime": "2026-09-20T19:00:00Z",
       "durationMinutes": 105
     }'
   ```
2. **Verification**: HTTP 201 Created. `startTime: "2026-09-20T19:00:00Z"`, `endTime: "2026-09-20T20:45:00Z"` (exactly 105 minutes).

---

### Scenario 8: Establishment Settings Immutability for Existing Bookings
1. **Action**: Manager updates restaurant default duration from 90 to 60 minutes.
2. **Verification**: Existing reservation booked for 105 minutes retains its original end time and duration.

---

### Scenario 9: Customer Waiting List Party Size Badge
1. **Action**: Customer queries active waiting list:
   ```bash
   curl -s "$GATEWAY_URL/api/v1/waiting-list?customerId=$CUSTOMER_ID" \
     -H "Authorization: Bearer $CUSTOMER_TOKEN"
   ```
2. **Verification**: Each entry contains `"partySize": 4`. In customer UI (`/ui/customer/`), the waitlist card renders `<span class="badge bg-light text-secondary border">4 guests</span>`.

---

### Scenario 10: Waiting List Match on Cancellation
1. **Action**: Customer enters waitlist for party size 4. A 2-person reservation is cancelled, but a 4-person table is free.
2. **Verification**: Waiting list candidate receives `OFFERED` status and real-time SSE offer event.
