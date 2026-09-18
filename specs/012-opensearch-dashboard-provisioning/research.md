# Technical Research: OpenSearch Dashboards Provisioning

**Feature**: `012-opensearch-dashboard-provisioning`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](./spec.md)

---

## 1. Automated Provisioning Mechanism in Docker Compose

### Context
OpenSearch Dashboards must launch with pre-configured index patterns (`otel-logs*`, `ss4o_traces-*`), visualizations, and three complete dashboards without requiring any manual setup clicks or UI file imports by developers.

### Evaluated Alternatives
1. **Volume-Mounted Entrypoint Wrapper Script on `opensearch-dashboards`**:
   - Mount a provisioning directory containing `entrypoint-wrapper.sh` and `dashboards.ndjson` into the existing `opensearch-dashboards` container via `docker-compose.yml`.
   - The wrapper script executes the standard `./opensearch-dashboards-docker-entrypoint.sh opensearch-dashboards` in the background.
   - Concurrently, a background provisioning loop uses the container's built-in `/usr/bin/curl` to poll `http://localhost:5601/api/status` until status is `green` or `yellow`.
   - Once ready, it executes a single idempotent `POST /api/saved_objects/_import?overwrite=true` with header `osd-xsrf: true` and logs completion.
   - The script forwards OS signals (SIGTERM/SIGINT) to the main Dashboards process and awaits its completion.
2. **Ephemeral Init Container (`curlimages/curl` with Provisioning Script)**:
   - Run an extra helper container (`rube-opensearch-dashboards-provisioner`) in `docker-compose.yml`.
   - Requires defining and managing an extra container in `docker-compose.yml` that appears in `Exited (0)` state.
3. **Custom Dockerfile Image**:
   - Build a custom `rube-opensearch-dashboards` Docker image with assets pre-baked.
   - Requires custom image building on developer machines (`docker compose build`), adding build latency and maintenance overhead.
4. **OpenSearch REST Direct Ingestion into `.kibana` Index**:
   - Ingest raw JSON documents directly into OpenSearch index `.kibana` via Elasticsearch/OpenSearch indexing APIs before Dashboards starts.
   - Fragile: bypassing the OpenSearch Dashboards Saved Objects API can cause migration version mismatches, missing index aliases, and mapping corruption.

### Decision
**Adopt Option 1: Volume-Mounted Entrypoint Wrapper Script on `opensearch-dashboards`**.
- **Rationale**: Keeps the Docker Compose setup clean with zero extra containers, takes advantage of the `/usr/bin/curl` binary already present in `opensearchproject/opensearch-dashboards:2.19.0`, operates via a simple volume mount (`./opensearch-dashboards:/usr/share/opensearch-dashboards/provisioning:ro`), and provides fully automatic, idempotent provisioning on boot.

---

## 2. Saved Objects Serialization Format

### Context
Dashboards, visualizations, searches, and index patterns must be serialized into an exportable, version-controlled format that OpenSearch Dashboards can ingest natively.

### Evaluated Alternatives
1. **Newline Delimited JSON (`.ndjson`)**:
   - Official native export/import format supported by OpenSearch Dashboards (`POST /api/saved_objects/_import`).
   - Supports atomic multi-object imports containing index patterns, visualizations, searches, and dashboards with cross-object reference resolution.
2. **Individual JSON REST Payloads**:
   - Make individual `POST /api/saved_objects/{type}/{id}` calls for each object.
   - Requires complex dependency ordering and script orchestration.

### Decision
**Adopt Option 1: Single `.ndjson` Provisioning Manifest (`infrastructure/opensearch-dashboards/dashboards.ndjson`)**.
- **Rationale**: Standard OpenSearch Dashboards export format. Automatically resolves object references and imports all 3 dashboards, visualizations, and index patterns in a single atomic request.

---

## 3. Pre-configuring Default Landing Route & Time Range

### Context
Per `FR-009` and `FR-010`, OpenSearch Dashboards must land developers directly on the "Application Logs Dashboard" when navigating to `http://localhost:5601`, with a default time range of "Last 15 minutes" and a 10-second auto-refresh interval.

### Evaluated Alternatives
1. **Config Object (`uiSettings`) in Saved Objects Import**:
   - Include a `config` saved object in `dashboards.ndjson` setting:
     - `defaultRoute: "/app/dashboards#/view/application-logs-dashboard"`
     - `timepicker:timeDefaults: "{ \"from\": \"now-15m\", \"to\": \"now\" }"`
     - `timepicker:refreshIntervalDefaults: "{ \"pause\": false, \"value\": 10000 }"`
   - OpenSearch Dashboards applies these settings immediately upon import without requiring environment variables or config file restarts.
2. **Configuration File Injection (`opensearch_dashboards.yml`)**:
   - Mount a custom `opensearch_dashboards.yml` into the container setting `opensearch_dashboards.defaultAppId: "dashboards"`.
   - Does not allow deep-linking directly to a specific dashboard view; only routes to the generic dashboards app.

### Decision
**Adopt Option 1: Provision `config` Saved Object via `dashboards.ndjson`**.
- **Rationale**: Directly enforces the exact dashboard target (`application-logs-dashboard`) and sets the 15-minute time window and 10-second auto-refresh as default global preferences.

---

## 4. Idempotency & Failure Recovery

### Context
The provisioning task must handle restarts, rapid rebuilds, and out-of-order container availability cleanly without duplication or error.

### Strategy
1. **OpenSearch Dashboards Readiness Probe**: The wrapper script running inside the `opensearch-dashboards` container polls `http://localhost:5601/api/status` using `/usr/bin/curl` until HTTP 200 with state `green` or `yellow` (2-second sleep between retries, up to 30 attempts).
2. **Atomic Overwrite**: The import call uses `?overwrite=true`. If the dashboard objects already exist from a previous launch, OpenSearch Dashboards updates them in-place with zero duplicate IDs.
3. **Container Lifecycle & Process Supervision**: The wrapper script runs the standard entrypoint `./opensearch-dashboards-docker-entrypoint.sh opensearch-dashboards &` as the primary process, imports objects in a background subshell once healthy, and traps/waits on the primary process PID. Zero extra containers or overhead required.
