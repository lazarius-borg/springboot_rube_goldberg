# Quickstart & Verification Guide: RBAC & Endpoint Authorization

**Feature**: `007-role-endpoint-access`  
**Target Services**: `customer-service` (8082), `restaurant-service` (8083), `availability-service` (8084), `reservation-service` (8085), `waiting-list-service` (8086), `analytics-service` (8087)

---

## 1. Automated Test Verification (Spring Slices & Security Tests)

Run the automated test suites validating unauthenticated rejection (401), unauthorized role rejection (403), and authorized access (200/201):

```bash
# Verify restaurant-service security filter chain
./mvnw -pl services/restaurant-service test -Dtest=RestaurantSecurityTest

# Verify reservation-service security filter chain
./mvnw -pl services/reservation-service test -Dtest=ReservationSecurityTest

# Verify waiting-list-service security filter chain
./mvnw -pl services/waiting-list-service test -Dtest=WaitingListSecurityTest

# Verify customer-service security filter chain
./mvnw -pl services/customer-service test -Dtest=CustomerSecurityTest

# Verify availability-service security filter chain
./mvnw -pl services/availability-service test -Dtest=AvailabilitySecurityTest

# Verify analytics-service security filter chain
./mvnw -pl services/analytics-service test -Dtest=AnalyticsSecurityTest

# Run all tests across the reactor
./mvnw clean test
```

---

## 2. Live Environment Verification (Docker Compose)

### Prerequisites
Start the infrastructure stack and application microservices:
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up -d
```

Ensure Keycloak is healthy on `http://localhost:8081`.

---

### Scenario A: Acquire Role Tokens

```bash
# 1. Customer Token (customer1 / password)
CUSTOMER_TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=customer1" \
  -d "password=password" | jq -r .access_token)

# 2. Restaurant Manager Token (manager1 / password)
MANAGER_TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=manager1" \
  -d "password=password" | jq -r .access_token)

# 3. Admin Token (admin1 / password)
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
  -d "client_id=rube-goldberg-app" \
  -d "grant_type=password" \
  -d "username=admin1" \
  -d "password=password" | jq -r .access_token)
```

---

### Scenario B: Verify Restaurant Management RBAC (`port: 8083`)

```bash
# Customer attempt -> Expected: 403 Forbidden
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8083/api/v1/restaurants \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Hacked Bistro", "address": "Nowhere", "timezone": "UTC"}'
# Output: 403

# Manager attempt -> Expected: 201 Created
REST_ID=$(curl -s -X POST http://localhost:8083/api/v1/restaurants \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "The Bistro", "address": "Amsterdam", "timezone": "Europe/Amsterdam", "defaultReservationDurationMinutes": 90, "cancellationWindowHours": 2}' \
  | jq -r .id)
echo "Created Restaurant ID: $REST_ID"

# Customer read catalog -> Expected: 200 OK
curl -s -o /dev/null -w "%{http_code}\n" -X GET "http://localhost:8083/api/v1/restaurants/$REST_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
# Output: 200
```

---

### Scenario C: Verify Reservation Booking RBAC (`port: 8085`)

```bash
# Manager attempt to book reservation -> Expected: 403 Forbidden
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8085/api/v1/reservations \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"restaurantId\": \"$REST_ID\", \"partySize\": 2, \"startTime\": \"2026-09-01T19:00:00Z\"}"
# Output: 403

# Customer attempt to book reservation -> Expected: 201 Created
RES_ID=$(curl -s -X POST http://localhost:8085/api/v1/reservations \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"restaurantId\": \"$REST_ID\", \"partySize\": 2, \"startTime\": \"2026-09-01T19:00:00Z\"}" \
  | jq -r .id)
echo "Confirmed Reservation: $RES_ID"

# Customer inspect reservation -> Expected: 200 OK
curl -s -o /dev/null -w "%{http_code}\n" -X GET "http://localhost:8085/api/v1/reservations/$RES_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
# Output: 200

# Customer list restaurant-wide reservations -> Expected: 403 Forbidden
curl -s -o /dev/null -w "%{http_code}\n" -X GET "http://localhost:8085/api/v1/reservations?restaurantId=$REST_ID" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
# Output: 403

# Manager list restaurant-wide reservations -> Expected: 200 OK
curl -s -o /dev/null -w "%{http_code}\n" -X GET "http://localhost:8085/api/v1/reservations?restaurantId=$REST_ID" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
# Output: 200
```

---

### Scenario D: Verify Analytics Service RBAC (`port: 8087`)

```bash
# Customer attempt to view analytics -> Expected: 403 Forbidden
curl -s -o /dev/null -w "%{http_code}\n" -X GET "http://localhost:8087/api/v1/analytics/summary" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
# Output: 403

# Manager attempt to view analytics -> Expected: 200 OK
curl -s -o /dev/null -w "%{http_code}\n" -X GET "http://localhost:8087/api/v1/analytics/summary" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
# Output: 200
```
