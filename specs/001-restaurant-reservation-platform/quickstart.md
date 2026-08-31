# Quickstart & Verification Guide

This guide describes how to build, start, and verify the complete Spring Boot Rube Goldberg microservices application locally.

## 1. Prerequisites
- **Java**: OpenJDK 26+ (with Virtual Threads support).
- **Docker**: Docker Desktop / Docker Engine & Docker Compose (v2+).
- **Maven**: Maven Wrapper (`./mvnw`) included in repository root.

---

## 2. Build & Automated Tests

To execute the root Maven reactor build across all microservices, running unit tests, Spring Boot slice tests, and Testcontainers-based integration tests:

```bash
./mvnw clean verify
```

Expected result: All 8 submodules (`gateway`, `customer-service`, `restaurant-service`, `reservation-service`, `availability-service`, `waiting-list-service`, `notification-service`, `analytics-service`) compile and test successfully without error.

---

## 3. Starting the Local Distributed Environment

Start the full platform (infrastructure services + application services) using Docker Compose:

```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

### Infrastructure & UI Endpoints:
- **API Gateway (HTTP Entry Point)**: `http://localhost:8080`
- **Minimal Web UI**: `http://localhost:8080/ui`
- **Keycloak (OIDC Identity)**: `http://localhost:8081` (Admin: `admin` / `admin`)
- **Mailpit (Email Inbox)**: `http://localhost:8025`
- **Grafana (Metrics & Dashboards)**: `http://localhost:3000` (Admin: `admin` / `admin`)
- **OpenSearch Dashboards (Logs & Tracing)**: `http://localhost:5601`
- **Prometheus**: `http://localhost:9090`
- **Alertmanager**: `http://localhost:9093`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

---

## 4. End-to-End "Rube Goldberg" Walkthrough

Execute the canonical end-to-end verification sequence:

### Step A: Authenticate
Obtain an OIDC JWT token from Keycloak for the seeded demo customer:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token \
  -d "grant_type=password&client_id=rube-goldberg-app&username=customer1&password=password" | jq -r .access_token)
```

### Step B: Search Availability
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/availability?restaurantId=11111111-1111-1111-1111-111111111111&date=2026-09-01&time=19:00:00&partySize=4" | jq .
```
- **Expected Outcome**: Returns `isAvailable: true` and available table slots.

### Step C: Make a Guaranteed Reservation
```bash
curl -s -X POST http://localhost:8080/api/v1/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": "11111111-1111-1111-1111-111111111111",
    "partySize": 4,
    "startTime": "2026-09-01T19:00:00Z"
  }' | jq .
```
- **Expected Outcome**: HTTP 201 Created with status `CONFIRMED` and allocated table IDs.

### Step D: Observe Async Rube Goldberg Propagation
1. **Email Confirmation in Mailpit**:
   - Open `http://localhost:8025`
   - Verify that Mailpit received a new email titled *"Reservation Confirmed"* with details matching Step C.
2. **Event Metrics in Analytics**:
   - Query `http://localhost:8080/api/v1/analytics/summary`
   - Verify `totalReservations` increased.
3. **Distributed Trace in OpenSearch / Grafana**:
   - Navigate to OpenSearch Dashboards (`http://localhost:5601`) or Grafana Traces.
   - Search for the `traceId` included in the response header `X-Trace-Id`.
   - Observe the full distributed trace spanning `gateway` -> `reservation-service` -> PostgreSQL Outbox -> Kafka -> `notification-service` -> Mailpit.

### Step E: Waiting List & Fair FIFO Re-allocation
1. Fill up remaining tables for 19:00.
2. Join waiting list:
   ```bash
   curl -s -X POST http://localhost:8080/api/v1/waiting-list \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "restaurantId": "11111111-1111-1111-1111-111111111111",
       "targetDate": "2026-09-01",
       "earliestTime": "18:00:00",
       "latestTime": "20:00:00",
       "partySize": 4
     }' | jq .
   ```
3. Cancel the reservation from Step C:
   ```bash
   curl -s -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/reservations/{reservationId}
   ```
4. Check Mailpit (`http://localhost:8025`) for a *Waiting List Offer* notification containing the offer ID.
5. Accept the offer:
   ```bash
   curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/waiting-list/offers/{offerId}/accept
   ```
   - **Expected Outcome**: HTTP 200 with newly created confirmed reservation.
