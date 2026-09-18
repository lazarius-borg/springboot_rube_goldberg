# Quickstart: Table Combination Management & Capacity Validation

**Feature**: `017-table-combinations`  
**Date**: 2026-09-18

This guide provides end-to-end validation scenarios for testing table combination management, capacity invariants, and availability coordination.

---

## 1. Prerequisites

- Platform running locally (via `docker compose up` or microservices launched) or running unit/integration test suites.
- Valid restaurant created with tables in at least two different zones (e.g., "Main Dining Room" with T1 cap 4, T2 cap 4; "Patio" with P1 cap 4).
- Manager credentials or authorized JWT bearer token.

---

## 2. Validation Scenarios

### Scenario A: Same-Zone Combination Creation & Default Capacity
1. Select tables `T1` (cap 4) and `T2` (cap 4) in zone "Main Dining Room".
2. Submit `POST /api/v1/restaurants/{id}/table-combinations` with `{ "tableIds": ["<id-T1>", "<id-T2>"] }`.
3. **Verify**:
   - HTTP 201 Created.
   - Returned combination has `name: "Combo: T1 + T2"`, `zone: "Main Dining Room"`, `combinedCapacity: 8`.
   - Overview list (`GET /api/v1/restaurants/{id}/table-combinations`) lists the new combination.

### Scenario B: Cross-Zone Rejection
1. Select `T1` (zone "Main Dining Room") and `P1` (zone "Patio").
2. Submit `POST /api/v1/restaurants/{id}/table-combinations` with `{ "tableIds": ["<id-T1>", "<id-P1>"] }`.
3. **Verify**:
   - HTTP 400 Bad Request.
   - Error indicates combined tables must reside in the same floor zone.

### Scenario C: Duplicate Detection (Order-Insensitive)
1. With existing combination `T1 + T2`, submit creation request with `{ "tableIds": ["<id-T2>", "<id-T1>"] }`.
2. **Verify**:
   - HTTP 400 Bad Request.
   - Error indicates duplicate table combination.
3. Submit creation request with `{ "tableIds": ["<id-T1>", "<id-T3>"] }` (partial overlap in same zone).
4. **Verify**:
   - HTTP 201 Created (partial overlaps are permitted).

### Scenario D: Custom Capacity Validation
1. Submit combination creation for `T1` (cap 4) and `T2` (cap 4) with `combinedCapacity: 6` (custom lower capacity).
2. **Verify**: HTTP 201 Created with capacity 6.
3. Submit combination creation for `T1` (cap 4) and `T2` (cap 4) with `combinedCapacity: 10` (exceeds sum of 8).
4. **Verify**: HTTP 400 Bad Request (capacity cannot exceed sum).

### Scenario E: Total Restaurant Capacity Invariant
1. Inspect availability or restaurant stats before adding combinations: total physical capacity is $N = \sum \text{cap}(T_i)$.
2. Add several table combinations.
3. Inspect `GET /api/v1/availability/restaurants/{id}?date=...&time=...&partySize=...`.
4. **Verify**: Total restaurant capacity remains exactly $N$.

### Scenario F: Deletion & Availability Coordination
1. Delete combination `T1 + T2` via `DELETE /api/v1/restaurants/{id}/table-combinations/{combId}`.
2. **Verify**:
   - HTTP 204 No Content.
   - Combination no longer appears in `GET /api/v1/restaurants/{id}/table-combinations`.
   - Any prior reservations holding `T1` or `T2` remain completely unaffected and valid.

---

## 3. Automated Test Execution

Run the relevant unit and web slice tests:

```bash
# Restaurant Service (Combination CRUD, validations, and domain events)
mvn test -pl services/restaurant-service

# Availability Service (Timeslot capacity calculations and combination mutual exclusion)
mvn test -pl services/availability-service

# Reservation Service (Table allocation engine & combinations)
mvn test -pl services/reservation-service
```
