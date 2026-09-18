# Quickstart Validation Guide: Prevent Past-Time Temporal Requests (with Manager Back-Filling)

**Feature**: `013-prevent-past-time-requests`
**Date**: 2026-09-16

---

## 1. Automated Test Suites

Verify temporal validation across the three microservices using Maven:

```bash
# Test availability-service temporal validation (customer rejection + manager back-fill)
./mvnw -pl services/availability-service test -Dtest=*Validation*,*Controller*

# Test reservation-service temporal validation (customer rejection + manager back-fill)
./mvnw -pl services/reservation-service test -Dtest=*Validation*,*Controller*

# Test waiting-list-service temporal validation (universal rejection)
./mvnw -pl services/waiting-list-service test -Dtest=*Validation*,*Controller*
```

---

## 2. End-to-End Runtime Validation (via Docker Compose / Gateway)

### Prerequisites
Ensure the environment is running:
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d
```

### Obtain Keycloak JWT Tokens
```bash
# Customer token (Alice - customer1)
CUSTOMER_TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password" | jq -r .access_token)

# Restaurant Manager token (Bob - manager1)
MANAGER_TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=manager1" \
  -d "password=password" | jq -r .access_token)
```

---

### Scenario 1: Availability Check in the Past (`availability-service`)

#### 1.1 Customer Checking in the Past (Must Be Rejected)
```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "http://localhost:8080/api/v1/availability?restaurantId=11111111-1111-1111-1111-111111111111&date=2020-01-01&time=19:00:00&partySize=2"
```
**Expected Outcome**:
- **HTTP Status**: `400`
- **Response**: RFC 7807 ProblemDetail containing `invalidParams` identifying that the requested date/time is in the past.

#### 1.2 Restaurant Manager Checking in the Past (Back-Filling Allowed)
```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  "http://localhost:8080/api/v1/availability?restaurantId=11111111-1111-1111-1111-111111111111&date=2020-01-01&time=19:00:00&partySize=2"
```
**Expected Outcome**:
- **HTTP Status**: `200`
- **Response**: Table availability returned.

---

### Scenario 2: Reservation Booking in the Past (`reservation-service`)

#### 2.1 Customer Booking in the Past (Must Be Rejected)
```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "http://localhost:8080/api/v1/reservations" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": "11111111-1111-1111-1111-111111111111",
    "partySize": 2,
    "startTime": "2020-01-01T19:00:00Z"
  }'
```
**Expected Outcome**:
- **HTTP Status**: `400`
- **Detail**: `"Reservation start time cannot be in the past"`

#### 2.2 Restaurant Manager Booking in the Past (Back-Filling Allowed)
```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "http://localhost:8080/api/v1/reservations" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": "11111111-1111-1111-1111-111111111111",
    "partySize": 2,
    "startTime": "2020-01-01T19:00:00Z"
  }'
```
**Expected Outcome**:
- **HTTP Status**: `201`
- **Response**: Reservation created and confirmed.

---

### Scenario 3: Joining Waiting List in the Past (`waiting-list-service`)

#### 3.1 Customer or Manager Requesting Past Target Date (Universal Rejection)
```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "http://localhost:8080/api/v1/waiting-list" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": "11111111-1111-1111-1111-111111111111",
    "customerEmail": "alice@example.com",
    "targetDate": "2020-01-01",
    "earliestTime": "18:00:00",
    "latestTime": "21:00:00",
    "partySize": 2
  }'
```
**Expected Outcome**:
- **HTTP Status**: `400`
- **Detail**: `"Target date must not be in the past"`
