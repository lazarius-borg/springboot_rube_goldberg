# Quickstart Validation Guide: Input Values Constraints and Validation

**Feature**: `008-input-validation-constraints` | **Date**: 2026-09-14

This guide details how to verify that input value constraints and validation mechanisms function properly across the microservice suite.

---

## 1. Prerequisites

1. Ensure the platform infrastructure (PostgreSQL, Kafka, Redis, Keycloak) is running:
   ```bash
   docker compose up -d
   ```
2. Build the Maven reactor to ensure all modules compile cleanly:
   ```bash
   JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current ./mvnw clean test
   ```

---

## 2. Validation Scenarios

### Scenario 1: Reject Out-of-Bounds Reservation Party Size and Duration
Attempting to book a reservation with a party size of 0 (below min 1) or duration of 10,000 minutes (above max 480 minutes) must fail immediately with an RFC 7807 `ProblemDetail`.

```bash
# Obtain Customer Token
CUSTOMER_TOKEN=$(curl -s -X POST "http://localhost:8080/realms/restaurant-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=demo-client&username=customer1&password=password" | jq -r .access_token)

# Submit out-of-bounds reservation request
curl -s -X POST "http://localhost:8084/api/v1/reservations" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": "00000000-0000-0000-0000-000000000001",
    "partySize": 0,
    "durationMinutes": 10000
  }' | jq .
```
**Expected Outcome**:
- Status: `400 Bad Request`
- Response body contains `invalidParams` identifying `partySize` (must be at least 1) and `durationMinutes` (cannot exceed 480).

---

### Scenario 2: Enforce Type-Safe Status Enumeration
Attempting to update a reservation status with an arbitrary string must be rejected:

```bash
# Obtain Manager Token
MANAGER_TOKEN=$(curl -s -X POST "http://localhost:8080/realms/restaurant-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=demo-client&username=manager1&password=password" | jq -r .access_token)

# Submit invalid status string
curl -s -X PATCH "http://localhost:8084/api/v1/reservations/<RESERVATION_ID>/status" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "INVALID_STATE"}' | jq .
```
**Expected Outcome**:
- Status: `400 Bad Request`
- Detail indicates invalid status value and lists allowed values (`CONFIRMED`, `ARRIVED`, `COMPLETED`, `NO_SHOW`, `CANCELLED`).

---

### Scenario 3: Reject Unbounded Restaurant Configuration
Attempting to register a restaurant with an unbounded booking horizon (e.g. 1,000,000 days):

```bash
curl -s -X POST "http://localhost:8083/api/v1/restaurants" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Extreme Bistro",
    "address": "123 Main St",
    "timezone": "Europe/Amsterdam",
    "maxBookingHorizonDays": 1000000
  }' | jq .
```
**Expected Outcome**:
- Status: `400 Bad Request`
- Response lists `maxBookingHorizonDays` as exceeding the 365-day maximum bound.

---

### Scenario 4: Authoritative Cancellation Policy Verification
Verify that cancellation deadlines are calculated against the reservation's snapshotted cancellation window:

```bash
# Cancel reservation past its snapshotted window
curl -s -X DELETE "http://localhost:8084/api/v1/reservations/<EXPIRED_RESERVATION_ID>" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq .
```
**Expected Outcome**:
- Status: `409 Conflict`
- Detail: "Cancellation deadline has passed (minimum 2 hours notice required)"

---

### Scenario 5: Automated Test Suite Execution
Execute all unit and WebMvc slice tests across the reactor:

```bash
JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current ./mvnw test
```
**Expected Outcome**:
- 10 modules tested, 0 failures, 0 errors.
