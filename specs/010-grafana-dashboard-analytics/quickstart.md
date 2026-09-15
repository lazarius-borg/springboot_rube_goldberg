# Quickstart & Verification Guide: Automated Grafana Provisioning & Operational Analytics

**Feature**: `010-grafana-dashboard-analytics`  
**Date**: 2026-09-15  
**Spec**: [spec.md](./spec.md) | **Contracts**: [contracts/](./contracts/)

---

## 1. Prerequisites

- Docker and Docker Compose v2.x installed.
- Maven 3.9+ and Java 26 SDK installed.
- Ports `3000` (Grafana), `9090` (Prometheus), `8080` (Gateway), and `8087` (Analytics Service) available.

---

## 2. Verification Step 1: Automated Grafana Zero-Touch Provisioning

### Action
Start the monitoring and core infrastructure stack:
```bash
docker compose -f infrastructure/docker-compose.yml up -d prometheus grafana
```

### Verification Checks
1. **Datasource Auto-Provisioning**:
   ```bash
   curl -s -u admin:admin http://localhost:3000/api/datasources | grep -q '"name":"Prometheus"' && echo "PASS: Prometheus datasource provisioned"
   ```
   *Expected Output*: `PASS: Prometheus datasource provisioned`

2. **Dashboard Auto-Provisioning**:
   ```bash
   curl -s -u admin:admin http://localhost:3000/api/search?query=Rube | grep -q '"uid":"rube-goldberg-platform"' && echo "PASS: Dashboard provisioned"
   ```
   *Expected Output*: `PASS: Dashboard provisioned`

3. **Browser Inspection**:
   - Navigate to `http://localhost:3000` (login `admin` / `admin`).
   - Open Dashboards -> `Spring Boot Rube Goldberg Platform Overview`.
   - Verify the dashboard loads immediately without any manual import prompts.

---

## 3. Verification Step 2: Empty-State & Cold Start Resilience

### Action
View the dashboard before creating any reservations.

### Expected Outcome
- All panels render cleanly within 2 seconds.
- No "Datasource error", "N/A broken widgets", or crash notifications.
- Counts display `0` and empty charts show "No data" cleanly.

---

## 4. Verification Step 3: Granular Operational Telemetry Ingestion

### Action
Trigger sample reservation events via the test suite or direct Kafka/Gateway calls:
```bash
./mvnw test -pl services/analytics-service -Dtest=AnalyticsEventListenerTest
```

### Verification Checks
1. **Actuator Prometheus Endpoint Metrics**:
   ```bash
   curl -s http://localhost:8087/actuator/prometheus | grep -E "reservation_demand_total|reservation_party_size_total|reservation_cancellations_total"
   ```
   *Expected Output*:
   - `reservation_demand_total{day_of_week="...",hour="...",restaurant_id="..."} 1.0`
   - `reservation_party_size_total{party_bucket="2",restaurant_id="..."} 1.0`
   - `reservation_cancellations_total{category="CUSTOMER_REQUEST",restaurant_id="..."} 1.0`

2. **REST Summary API**:
   ```bash
   curl -s http://localhost:8087/api/v1/analytics/summary
   ```
   *Expected Output*:
   - Valid JSON response containing `partySizeDistribution`, `cancellationsByCategory`, and `cancellationRatePercentage`.

3. **Grafana Visualizations**:
   - Refresh the Grafana dashboard at `http://localhost:3000`.
   - Verify the "Popular Visit Times" matrix, "Party Size Distribution" chart, and "Cancellations by Category" panels dynamically reflect the test data.
   - Verify the `$restaurant_id` dropdown allows filtering to the specific restaurant and viewing "All".

---

## 5. Cleanup
```bash
docker compose -f infrastructure/docker-compose.yml down
```
