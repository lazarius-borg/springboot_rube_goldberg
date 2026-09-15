# Implementation Plan: Automated Grafana Provisioning & Operational Analytics

**Branch**: `010-grafana-dashboard-analytics` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/010-grafana-dashboard-analytics/spec.md`

---

## Summary

This feature eliminates all manual Grafana setup by introducing zero-touch datasource and dashboard provisioning in Docker Compose and enriches the platform dashboard with granular operational analytics for restaurant operators:
1. **Zero-Touch Provisioning**: Mount declarative datasource (`prometheus.yml`) and dashboard provider configs (`dashboards.yml`) into `/etc/grafana/provisioning/` so that Grafana launches with the Prometheus telemetry connection and platform dashboard pre-loaded.
2. **Granular Operational Telemetry**: Extend `analytics-service` to ingest reservation and waiting-list events and emit bounded Micrometer Prometheus metrics for:
   - Peak Visit Times: 7-day (Mon–Sun) × 24-hour matrix.
   - Party Size Distribution: Discrete buckets (1, 2, 3, 4, 5, 6, 7+).
   - Cancellation Metrics: Breakdown across 3 categories (`CUSTOMER_REQUEST`, `NO_SHOW_LATE_CANCEL`, `RESTAURANT_INITIATED`) plus overall cancellation rate.
   - Waiting list conversion efficiency.
3. **Interactive Multi-Tenant Dashboard**: Upgrade `rube-goldberg-dashboard.json` with dedicated visual panels (heatmaps, bar charts, gauges) and a dynamic `$restaurant_id` template variable supporting both individual restaurant filtering and platform-wide aggregation.

---

## Technical Context

**Language/Version**: Java 26, Spring Boot 4.1.x, Grafana 11.5.1, Prometheus v3.x  
**Primary Dependencies**: Spring Boot Actuator, Micrometer Prometheus Registry (`micrometer-registry-prometheus`), Spring Kafka, Spring Data JPA, Jackson  
**Storage**: PostgreSQL (JPA entities for persistent aggregates), Prometheus TSDB (time-series metrics)  
**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test (`@WebMvcTest`, `@SpringBootTest`)  
**Target Platform**: Linux containers / Docker Compose environment on local/server deployments  
**Project Type**: Microservices Maven submodule (`analytics-service`) + declarative infrastructure configuration (`infrastructure/grafana/`)  
**Performance Goals**: First-time dashboard access < 5 seconds; panel refresh < 2 seconds; REST analytics queries < 200 milliseconds  
**Constraints**: Bounded Prometheus label cardinality (< 200 time-series per restaurant); 100% automated startup without manual UI imports  
**Scale/Scope**: 1 microservice (`services/analytics-service`), 1 Docker Compose config (`infrastructure/docker-compose.yml`), 3 Grafana provisioning/dashboard assets  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evaluation |
|---|---|---|
| **I. Strict Specification Adherence** | **PASS** | Directly satisfies FR-001 through FR-009, user stories P1–P4, and clarification session decisions without undocumented extensions. |
| **II. Maven Reactor & Microservices Architecture** | **PASS** | Changes are contained within `services/analytics-service` and root `infrastructure/` without introducing rogue dependencies or breaking module boundaries. |
| **III. Modern Spring Boot Feature Showcase** | **PASS** | Leverages Spring Boot Actuator, Micrometer metrics with tags, Spring Data repositories, and Kafka event listeners. |
| **IV. Agentic AI-Aided Development & Traceability** | **PASS** | Spec, research, data model, contracts, and quickstart documentation maintain full bidirectional traceability. |
| **V. Comprehensive Testing & Quality Gates** | **PASS** | Unit tests and slice tests will validate event ingestion, metric counting, and REST schemas; full reactor verify will be maintained. |

---

## Project Structure

### Documentation (this feature)

```text
specs/010-grafana-dashboard-analytics/
├── spec.md              # Feature specification & clarifications
├── plan.md              # Implementation plan (this file)
├── research.md          # Technical research & architectural decisions
├── data-model.md        # Dimensional & persistent data schemas
├── quickstart.md        # End-to-end verification guide
├── contracts/           # API and dashboard layout contracts
│   ├── analytics-telemetry-contract.md
│   └── grafana-dashboard-contract.md
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository touchpoints)

```text
infrastructure/
├── docker-compose.yml                              # Volume mounts for Grafana provisioning
└── grafana/
    ├── dashboards/
    │   └── rube-goldberg-dashboard.json             # Updated dashboard with 5 operational panels
    └── provisioning/
        ├── dashboards/
        │   └── dashboards.yml                      # Dashboard provider pointing to /var/lib/grafana/dashboards
        └── datasources/
            └── prometheus.yml                      # Default datasource pointing to http://prometheus:9090

services/analytics-service/
├── src/main/java/nl/invokedynamic/demo/analytics/
│   ├── api/
│   │   └── AnalyticsController.java                # Updated summary DTO with party & cancel breakdowns
│   ├── domain/
│   │   ├── ReservationDailyMetricsEntity.java      # Extended with party size buckets & cancel categories
│   │   └── ReservationHourlyMetricsEntity.java     # New hourly demand entity
│   ├── events/
│   │   └── AnalyticsEventListener.java             # Event listener updating Micrometer & DB
│   ├── repository/
│   │   └── ReservationHourlyMetricsRepository.java # New JPA repository
│   └── service/
│       └── AnalyticsService.java                   # Business logic and Micrometer meter updates
└── src/test/java/nl/invokedynamic/demo/analytics/
    ├── api/
    │   └── AnalyticsControllerWebMvcTest.java      # Controller slice tests for summary payload
    └── service/
        └── AnalyticsServiceUnitTest.java           # Unit tests for metric increments and aggregations
```

**Structure Decision**: Standard Maven reactor submodule structure (`services/analytics-service`) combined with root declarative infrastructure configurations (`infrastructure/grafana/`).

---

## Complexity Tracking

> **Constitution Check clean pass: No violations or special justifications required.**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *None* | N/A | N/A |
