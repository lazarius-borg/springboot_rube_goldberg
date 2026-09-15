# Quickstart Validation Guide: OpenTelemetry Logging & Tracing

**Feature**: `011-otel-logging-tracing`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](./spec.md)

---

## 1. Prerequisites

- Docker and Docker Compose installed and running.
- Java 26 and Maven (or `./mvnw`).
- `curl` and `jq` for executing REST commands and parsing responses.

---

## 2. Platform Startup

1. **Start Platform Infrastructure & Observability Stack**:
   ```bash
   cd infrastructure
   docker compose up -d
   ```
   Verify that `rube-otel-collector`, `rube-opensearch`, and `rube-opensearch-dashboards` are running:
   ```bash
   docker compose ps
   ```

2. **Launch Platform Services**:
   Launch API Gateway and microservices (either via Docker Compose or locally):
   ```bash
   # In separate terminals or backgrounded:
   ./mvnw clean spring-boot:run -pl gateway
   ./mvnw clean spring-boot:run -pl services/customer-service
   ./mvnw clean spring-boot:run -pl services/restaurant-service
   ./mvnw clean spring-boot:run -pl services/reservation-service
   ./mvnw clean spring-boot:run -pl services/availability-service
   ./mvnw clean spring-boot:run -pl services/waiting-list-service
   ./mvnw clean spring-boot:run -pl services/analytics-service
   ./mvnw clean spring-boot:run -pl services/notification-service
   ```

---

## 3. End-to-End Validation Scenarios

### Scenario 1: Verify Dual Logging Output
1. Check standard console output for any service:
   ```bash
   # Console logs are clearly formatted and visible in terminal
   ```
2. Trigger an HTTP request to any service:
   ```bash
   curl -s http://localhost:8080/actuator/health | jq
   ```
3. Verify that the log message also streamed to the OpenTelemetry Collector:
   ```bash
   docker compose logs --tail=20 otel-collector
   ```

### Scenario 2: Verify OpenSearch Log Ingestion
Query OpenSearch directly to confirm structured logs are ingested under `otel-logs`:
```bash
curl -s -X POST "http://localhost:9200/otel-logs/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": { "match_all": {} },
    "size": 5
  }' | jq '.hits.hits[]._source | {timestamp, serviceName, severity, message, traceId}'
```
**Expected Outcome**: Returns structured JSON documents matching [data-model.md](./data-model.md#1-structured-log-event-model) with populated `serviceName`, `severity`, `message`, and timestamp.

### Scenario 3: Verify Distributed Trace Context & Correlation
1. Execute an authenticated reservation booking request through API Gateway:
   ```bash
   # Retrieve Keycloak token (or use dev credentials)
   TOKEN=$(curl -s -X POST "http://localhost:8081/realms/rube-goldberg/protocol/openid-connect/token" \
     -d "client_id=customer-client" \
     -d "client_secret=customer-secret" \
     -d "username=alice" \
     -d "password=alice" \
     -d "grant_type=password" | jq -r .access_token)

   # Create reservation
   RESP=$(curl -s -X POST "http://localhost:8080/api/v1/reservations" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": 1,
       "restaurantId": 1,
       "reservationTime": "2026-10-01T19:00:00Z",
       "partySize": 2
     }')
   ```

2. Extract the `TraceId` from the response or OpenSearch:
   ```bash
   TRACE_ID=$(curl -s -X POST "http://localhost:9200/ss4o_traces-*/_search" \
     -H "Content-Type: application/json" \
     -d '{"query": {"match": {"name": "POST /api/v1/reservations"}}, "size": 1}' \
     | jq -r '.hits.hits[0]._source.traceId')
   echo "Captured Trace ID: $TRACE_ID"
   ```

3. Correlate logs across all microservices using the `TraceId`:
   ```bash
   curl -s -X POST "http://localhost:9200/otel-logs/_search" \
     -H "Content-Type: application/json" \
     -d "{
       \"query\": { \"term\": { \"traceId\": \"$TRACE_ID\" } },
       \"sort\": [{ \"timestamp\": { \"order\": \"asc\" } }]
     }" | jq '.hits.hits[]._source | "\(.timestamp) [\(.serviceName)] \(.severity): \(.message)"'
   ```
   **Expected Outcome**: A chronological timeline of log messages from API Gateway, `reservation-service`, `availability-service`, and `notification-service` all sharing the exact same `traceId`.

### Scenario 4: OpenSearch Dashboards Visual Verification
1. Open your browser to [http://localhost:5601](http://localhost:5601).
2. Navigate to **Discover** -> Create Index Pattern `otel-logs*` with timestamp field `@timestamp` / `timestamp`.
3. View the live stream of correlated microservice logs.
4. Navigate to **Trace Analytics** to view the end-to-end distributed service map and span latency waterfall.
