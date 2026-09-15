# Research & Architectural Decisions: Automated Grafana Provisioning & Operational Analytics

**Feature**: `010-grafana-dashboard-analytics`  
**Date**: 2026-09-15  
**Spec**: [spec.md](./spec.md)

---

## 1. Automated Grafana Provisioning in Docker Compose

### Context & Goal
Currently, starting the platform with `docker-compose.yml` launches Grafana (`grafana/grafana:11.5.1`) with zero mounted provisioning files. Operators previously had to manually configure the Prometheus datasource and import dashboard JSON files. The goal is zero-touch initialization on `docker compose up`.

### Decision
Use Grafana's native file-based provisioning system (`/etc/grafana/provisioning/`) with read-only bind mounts in `infrastructure/docker-compose.yml`:
1. **Datasources Provisioning**:
   - Source: `infrastructure/grafana/provisioning/datasources/prometheus.yml`
   - Target mount: `/etc/grafana/provisioning/datasources/prometheus.yml:ro`
   - Config: Defines `Prometheus` as the default datasource pointing to `http://prometheus:9090` with proxy access mode and editable flag.
2. **Dashboards Provider Provisioning**:
   - Source: `infrastructure/grafana/provisioning/dashboards/dashboards.yml`
   - Target mount: `/etc/grafana/provisioning/dashboards/dashboards.yml:ro`
   - Config: Registers a provider of type `file` pointing to `/var/lib/grafana/dashboards` with `updateIntervalSeconds: 10`, `allowUiUpdates: true`.
3. **Dashboard JSON Definitions**:
   - Source: `infrastructure/grafana/dashboards/rube-goldberg-dashboard.json`
   - Target mount: `/var/lib/grafana/dashboards/rube-goldberg-dashboard.json:ro`

### Rationale
- **Zero-touch**: Grafana automatically discovers datasources and dashboards on startup without needing init scripts, curl commands, or Grafana HTTP API tokens.
- **Idempotency**: Survives container restarts without generating duplicate dashboards or failing on re-initialization.
- **Maintainability**: Declarative YAML and JSON files version-controlled directly in git under `infrastructure/grafana/`.

### Alternatives Considered
- **Grafana API initialization script / curl entrypoint wrapper**: Fragile, requires polling for Grafana port 3000 health, managing API keys or basic auth credentials in shell scripts. Rejected in favor of standard declarative provisioning.
- **Docker volume data persistence (`grafana-storage`)**: Retains dashboard state between runs, but does NOT solve the cold-start first-boot import requirement from git sources.

---

## 2. Granular Operational Telemetry & Metric Design

### Context & Goal
Operators need granular visibility into:
1. **Popular Visit Times**: Day-of-week (Monday–Sunday) and hourly (00:00–23:00) demand matrix.
2. **Party Size Distribution**: Discrete group size buckets (1, 2, 3, 4, 5, 6, 7+ guests).
3. **Cancellation Metrics**: Overall cancellation rate and category breakdown (`CUSTOMER_REQUEST`, `NO_SHOW_LATE_CANCEL`, `RESTAURANT_INITIATED`).
4. **Waiting List Conversion**: Offers created vs accepted efficiency.

### Decision
Implement a dual-layer reporting architecture in `analytics-service`:
1. **Real-time Telemetry via Micrometer Meters (`MeterRegistry`)**:
   - Exposed on `/actuator/prometheus` and scraped via the existing OTel collector / Prometheus pipeline.
   - Bounded label dimensions:
     - `reservation_demand_total{restaurant_id, day_of_week, hour}` (Counter)
     - `reservation_party_size_total{restaurant_id, bucket}` where `bucket` in `["1", "2", "3", "4", "5", "6", "7+"]` (Counter)
     - `reservation_cancellations_total{restaurant_id, reason_category}` where `reason_category` in `["CUSTOMER_REQUEST", "NO_SHOW_LATE_CANCEL", "RESTAURANT_INITIATED"]` (Counter)
     - `reservation_status_total{restaurant_id, status}` where `status` in `["CREATED", "CONFIRMED", "COMPLETED", "CANCELLED"]` (Counter)
2. **Persistent Historical Aggregates in PostgreSQL**:
   - Update `analytics-service` domain models (`ReservationDailyMetricsEntity` and new granular metrics entities or columns) to persist hourly slots, party size counts, and cancellation categories.
   - Extend `AnalyticsService` and `AnalyticsController` `/api/v1/analytics/summary` to return granular distributions via REST for external integrations and programmatic reporting.

### Rationale
- Prometheus excels at real-time time-series aggregation, heatmaps, and Grafana panel rendering.
- Bounding party sizes to 1–6 and 7+ and days to 7 days x 24 hours keeps metric label cardinality strictly bounded (< 200 series per restaurant).
- Storing aggregates in PostgreSQL guarantees durability across Prometheus retention/restart cycles and powers REST queries.

### Alternatives Considered
- **Direct Grafana-to-PostgreSQL datasource**: Requires exposing PostgreSQL credentials to Grafana, writing complex SQL queries in dashboard JSON, and adding database connection pooling overhead to Grafana. Rejected because Prometheus is already the established, standardized telemetry datasource in this platform.
- **High-cardinality raw timestamp metrics**: Emitting exact minute or second metrics would cause Prometheus label explosion. Rejected in favor of day-of-week and hour slot labels.

---

## 3. Grafana Multi-Restaurant Filtering Variable

### Context & Goal
The dashboard must allow operators to view system-wide platform aggregates or isolate a single restaurant.

### Decision
Configure a dynamic Dashboard Template Variable `$restaurant_id` in `rube-goldberg-dashboard.json`:
- **Variable Type**: `query` (or `custom` with default fallback).
- **Query Definition**: `label_values(reservation_status_total, restaurant_id)`
- **Include All option**: Enabled (`true`), with custom all value `.*` or regex matching in PromQL:
  `sum(reservation_demand_total{restaurant_id=~"$restaurant_id"}) by (day_of_week, hour)`
- **Default Selection**: `All`.

### Rationale
- Standard Grafana practice for multi-tenant and multi-entity dashboards.
- A single dashboard serves both corporate/platform operators (viewing "All") and individual store managers (selecting their specific `restaurant_id`).

### Alternatives Considered
- **Separate dashboards per restaurant**: Unmaintainable and does not scale as restaurants are added dynamically.
- **Fixed system-wide view only**: Fails user story requirement for restaurant-specific operational visibility.

---

## 4. Cancellation Classification Taxonomy

### Context & Goal
Clarification Session 2026-09-15 established that cancellations must be categorized into 3 actionable categories.

### Decision
Map incoming event reasons from `ReservationCancelledEvent` (and potential timeout/no-show triggers) to a canonical 3-tier enum:
1. `CUSTOMER_REQUEST`: Explicit customer cancellation prior to scheduled service.
2. `NO_SHOW_LATE_CANCEL`: Customer failed to arrive or cancelled after cutoff window.
3. `RESTAURANT_INITIATED`: Table unavailable, kitchen emergency, or administrative void.

### Rationale
- Maps cleanly from existing domain events.
- Provides actionable distinction between voluntary customer churn, reliability issues (no-shows), and supply-side operational cancellations.
