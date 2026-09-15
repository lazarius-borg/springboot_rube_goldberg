# Tasks: Automated Grafana Provisioning & Operational Analytics Dashboard

**Feature**: `010-grafana-dashboard-analytics`  
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize Grafana declarative provisioning structure and Docker Compose bind mounts.

- [X] T001 Create Grafana datasource provisioning config in infrastructure/grafana/provisioning/datasources/prometheus.yml and dashboard provider config in infrastructure/grafana/provisioning/dashboards/dashboards.yml
- [X] T002 [P] Update Grafana service definition in infrastructure/docker-compose.yml to mount provisioning configurations and dashboard assets as read-only volumes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data models, entities, and telemetry infrastructure in `analytics-service` required by all operational analytics stories.

**⚠️ CRITICAL**: Must be completed before User Story implementation begins.

- [X] T003 Extend ReservationDailyMetricsEntity in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/domain/ReservationDailyMetricsEntity.java and create ReservationHourlyMetricsEntity in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/domain/ReservationHourlyMetricsEntity.java and repository in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/repository/ReservationHourlyMetricsRepository.java
- [X] T004 [P] Update AnalyticsSummary DTO and REST response schema in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/service/AnalyticsService.java and services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/api/AnalyticsController.java to include party size and cancellation breakdowns
- [X] T005 Configure Micrometer Prometheus MeterRegistry counter helpers for reservation demand, party sizes, and cancellations in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/service/AnalyticsService.java

**Checkpoint**: Foundation ready — User Story implementation can proceed in parallel or sequence.

---

## Phase 3: User Story 1 - Zero-Touch Automated Dashboard Provisioning (Priority: P1) 🎯 MVP

**Goal**: Provision Grafana datasource and platform overview dashboard automatically upon `docker compose up` with zero manual configuration.

**Independent Test**: Start containers with `docker compose -f infrastructure/docker-compose.yml up -d prometheus grafana` on a clean environment; log into `http://localhost:3000` (admin/admin) and verify the dashboard and Prometheus datasource are immediately active without import prompts.

- [X] T006 [P] [US1] Initialize base provisioned dashboard with UID rube-goldberg-platform and platform title in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json
- [X] T007 [US1] Configure dynamic restaurant_id template variable with Prometheus query and All option in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json
- [X] T008 [US1] Add platform reservation lifecycle throughput and status timeseries panel in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json
- [X] T009 [US1] Validate zero-touch automated provisioning and startup health via curl commands in specs/010-grafana-dashboard-analytics/quickstart.md

**Checkpoint**: User Story 1 (MVP) is fully functional — dashboard is automatically provisioned and visible on startup.

---

## Phase 4: User Story 2 - Granular Visit Time & Peak Demand Analytics (Priority: P2)

**Goal**: Capture, aggregate, and visualize reservation start times across day-of-week (Monday–Sunday) and hourly slots (00:00–23:00) with restaurant-level and platform-wide filtering.

**Independent Test**: Ingest reservation events with diverse start times; verify `reservation_demand_total` counter labels via `/actuator/prometheus` and verify that the Grafana heatmap/matrix panel renders hourly peaks correctly.

- [X] T010 [P] [US2] Write unit tests for day-of-week and hourly slot aggregation in services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/service/AnalyticsServiceUnitTest.java
- [X] T011 [US2] Implement visit time aggregation logic and reservation_demand_total Micrometer counter increments in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/service/AnalyticsService.java
- [X] T012 [US2] Update AnalyticsEventListener in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/events/AnalyticsEventListener.java to record day-of-week and hourly visit demand on ReservationCreatedEvent
- [X] T013 [US2] Add Popular Visit Times day-of-week and hourly matrix visualization panel in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json

**Checkpoint**: User Stories 1 AND 2 work independently — visit time peak demand heatmap is operational.

---

## Phase 5: User Story 3 - Party Size Distribution & Capacity Utilization Insights (Priority: P3)

**Goal**: Capture, aggregate, and visualize customer group sizes across discrete capacity buckets (1, 2, 3, 4, 5, 6, 7+ guests).

**Independent Test**: Create reservations with party sizes ranging from 1 to 10; verify `reservation_party_size_total` labels and `/api/v1/analytics/summary` party size breakdown, and verify the Party Size Distribution bar chart in Grafana.

- [X] T014 [P] [US3] Write unit tests for party size bucket classification (1..6, 7+) in services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/service/AnalyticsServiceUnitTest.java
- [X] T015 [US3] Implement party size bucketing logic, persistent daily bucket increments, and reservation_party_size_total counter updates in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/service/AnalyticsService.java
- [X] T016 [US3] Update AnalyticsControllerWebMvcTest in services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/api/AnalyticsControllerWebMvcTest.java to assert partySizeDistribution payload
- [X] T017 [US3] Add Party Size Distribution bar chart panel in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json

**Checkpoint**: User Stories 1, 2, AND 3 work independently — table capacity utilization insights are available.

---

## Phase 6: User Story 4 - Cancellation Frequency & Operational Risk Tracking (Priority: P4)

**Goal**: Track cancellations categorized by initiator/reason (Customer Request, No-Show / Late Cancel, Restaurant Initiated) alongside the overall cancellation rate percentage.

**Independent Test**: Cancel reservations with different reasons; verify `reservation_cancellations_total` labels, verify cancellation rate percentage calculation with zero-division resilience, and check Grafana cancellation stat and breakdown panels.

- [X] T018 [P] [US4] Write unit tests for cancellation category mapping and rate percentage calculation in services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/service/AnalyticsServiceUnitTest.java
- [X] T019 [US4] Implement cancellation category mapping, persistent counter increments, reservation_cancellations_total metric, and cancellation rate logic in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/service/AnalyticsService.java
- [X] T020 [US4] Update AnalyticsEventListener in services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/events/AnalyticsEventListener.java to extract cancellation reasons and trigger categorized recording on ReservationCancelledEvent
- [X] T021 [US4] Update AnalyticsControllerWebMvcTest in services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/api/AnalyticsControllerWebMvcTest.java to assert cancellationsByCategory and cancellationRatePercentage
- [X] T022 [US4] Add Cancellation Category breakdown and Cancellation Rate percentage panels in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json

**Checkpoint**: All 4 User Stories are functional and verifiable independently.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Operational resilience, waiting-list conversion panel, documentation, and end-to-end regression validation.

- [X] T023 [P] Add Waiting List Entry-to-Offer Conversion Rate gauge panel in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json
- [X] T024 [P] Implement cold-start zero-division safeguards and empty-dataset display defaults across queries in infrastructure/grafana/dashboards/rube-goldberg-dashboard.json and services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/service/AnalyticsService.java
- [X] T025 Run full Maven reactor test verification via ./mvnw clean test -pl services/analytics-service
- [X] T026 Execute end-to-end verification scenario in specs/010-grafana-dashboard-analytics/quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: No dependencies — start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user stories.
- **Phase 3 (US1 - MVP)**: Depends on Phase 1 & 2 — can be verified immediately.
- **Phase 4 (US2 - Visit Times)**: Depends on Phase 2 & T006.
- **Phase 5 (US3 - Party Size)**: Depends on Phase 2 & T006.
- **Phase 6 (US4 - Cancellations)**: Depends on Phase 2 & T006.
- **Phase 7 (Polish)**: Depends on completion of User Stories 1 through 4.

### Parallel Opportunities
- **Setup**: `T002` can run in parallel with `T001`.
- **Foundational**: `T004` can run in parallel with `T003`.
- **User Stories**: Once Phase 2 is complete, US1 (`T006`-`T009`), US2 (`T010`-`T013`), US3 (`T014`-`T017`), and US4 (`T018`-`T022`) can proceed in parallel across independent developers or service/dashboard workstreams.
- **Unit Tests**: `T010`, `T014`, `T018` can be written in parallel before implementation.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup: provisioning files & compose mounts).
2. Complete Phase 2 (Foundational: entities & DTO updates).
3. Complete Phase 3 (User Story 1: provision base dashboard with `$restaurant_id` variable and throughput panel).
4. **Validate MVP**: Launch containers with `docker compose up -d prometheus grafana` and confirm zero-touch dashboard access.

### Incremental Delivery
1. **Increment 1 (MVP)**: Automated provisioning operational.
2. **Increment 2 (US2)**: Popular visit times day-of-week and hourly heatmap operational.
3. **Increment 3 (US3)**: Discrete party size distribution metrics and bar chart operational.
4. **Increment 4 (US4)**: 3-tier cancellation tracking and rate metrics operational.
5. **Increment 5 (Polish)**: Waiting list conversion gauge, empty-state resilience, and end-to-end validation.
