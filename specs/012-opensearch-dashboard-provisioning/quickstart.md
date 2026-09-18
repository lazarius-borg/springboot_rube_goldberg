# Quickstart Validation Guide: OpenSearch Dashboards Provisioning

**Feature**: `012-opensearch-dashboard-provisioning`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](./spec.md)

---

## 1. Prerequisites

- Docker and Docker Compose installed and running.
- Platform infrastructure started via `infrastructure/docker-compose.yml`.
- `curl` and `jq` for verifying REST API responses.

---

## 2. Validation Scenarios

### Scenario 1: Verify Automated Provisioning Lifecycle

1. Start the platform infrastructure:
   ```bash
   cd infrastructure
   docker compose up -d
   ```

2. Check the OpenSearch Dashboards container logs:
   ```bash
   docker compose logs opensearch-dashboards
   ```
   **Expected Outcome**: The container logs show the wrapper script starting Dashboards, waiting for it to become healthy, and successfully importing saved objects:
   ```text
   Waiting for OpenSearch Dashboards to become healthy...
   OpenSearch Dashboards is ready (state: green). Importing saved objects...
   Import successful: {"success":true,"successCount":11}
   OpenSearch Dashboards provisioning complete.
   ```

---

### Scenario 2: Verify Provisioned Saved Objects via REST API

1. Verify that both required index patterns (`otel-logs*` and `ss4o_traces-*`) are registered:
   ```bash
   curl -s -X GET "http://localhost:5601/api/saved_objects/_find?type=index-pattern" \
     -H "osd-xsrf: true" | jq '.saved_objects[].attributes.title'
   ```
   **Expected Outcome**: Returns:
   ```json
   "otel-logs*"
   "ss4o_traces-*"
   ```

2. Verify that all three dashboards are provisioned:
   ```bash
   curl -s -X GET "http://localhost:5601/api/saved_objects/_find?type=dashboard" \
     -H "osd-xsrf: true" | jq '.saved_objects[].attributes.title'
   ```
   **Expected Outcome**: Returns:
   ```json
   "Platform Observability Overview"
   "Application Logs Dashboard"
   "Distributed Traces Dashboard"
   ```

---

### Scenario 3: Verify Browser Landing Experience & Live Tail

1. Open your browser and navigate to:
   ```text
   http://localhost:5601
   ```
2. **Expected Outcome**:
   - The browser automatically navigates directly to the **Application Logs Dashboard** (`/app/dashboards#/view/application-logs-dashboard`) without requiring navigation clicks.
   - The top time picker displays **Last 15 minutes** with auto-refresh set to **10 seconds**.
   - As microservices emit logs, the Log Event Volume histogram, Severity Breakdown, and Log Stream table populate automatically.

3. Click the navigation menu &rarr; **Dashboards**:
   - Verify the three distinct dashboards:
     - `Platform Observability Overview`
     - `Application Logs Dashboard`
     - `Distributed Traces Dashboard`

---

### Scenario 4: Verify Idempotency & Zero Duplication

1. Restart the `opensearch-dashboards` container:
   ```bash
   docker compose restart opensearch-dashboards
   ```

2. Inspect the total dashboard count:
   ```bash
   curl -s -X GET "http://localhost:5601/api/saved_objects/_find?type=dashboard" \
     -H "osd-xsrf: true" | jq '.total'
   ```
   **Expected Outcome**: The total count remains exactly `3` (no duplicates created).
