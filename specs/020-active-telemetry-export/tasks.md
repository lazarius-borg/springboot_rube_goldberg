# Tasks: Active Telemetry Export for Logs and Traces

**Feature**: `020-active-telemetry-export`  
**Input**: [spec.md](./spec.md) | [plan.md](./plan.md) | [data-model.md](./data-model.md) | [telemetry-pipeline-contract.md](./contracts/telemetry-pipeline-contract.md) | [quickstart.md](./quickstart.md)  
**Status**: Ready for Implementation  

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project dependency verification and collector infrastructure validation.

- [X] T001 Verify OpenTelemetry and Micrometer dependencies (`opentelemetry-exporter-otlp:1.62.0`, `micrometer-tracing-bridge-otel:1.7.1`) in `pom.xml`, `gateway/pom.xml`, and `services/*/pom.xml`
- [X] T002 [P] Verify OpenTelemetry Collector pipeline configuration for HTTP receiver on port 4318, batch processor, and OpenSearch exporters in `infrastructure/opentelemetry/otel-collector-config.yaml`
- [X] T003 [P] Verify OpenSearch Dashboards index patterns (`otel-logs*` and `ss4o_traces-*`) and saved dashboard objects in `infrastructure/opensearch/dashboards/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core telemetry infrastructure and appender bridge components that MUST be in place before user story delivery.

**⚠️ CRITICAL**: Foundational telemetry pipeline components must be defined before service-level wiring begins.

- [X] T004 Create shared telemetry model and bridge constants (`MAX_QUEUE_SIZE = 2048`, `FLUSH_INTERVAL_MS = 1000`, `DROP_ON_FULL`) in `common/event-contracts/src/main/java/nl/invokedynamic/demo/events/TelemetryConstants.java` and declare `event-contracts` dependency in `gateway/pom.xml`
- [X] T005 [P] Implement reusable OpenTelemetry Logback Appender bridge component forwarding `ILoggingEvent` (mapping `timestamp`, `serviceName`, `severity`, `logger`, `thread`, `message`, and MDC `traceId`/`spanId`) in `gateway/src/main/java/nl/invokedynamic/demo/gateway/telemetry/OpenTelemetryLogbackAppender.java`
- [X] T006 [P] Implement health check URI filter predicate suppressing traces and routine logs for `/actuator/health/**` in `gateway/src/main/java/nl/invokedynamic/demo/gateway/telemetry/HealthProbeFilter.java`

**Checkpoint**: Foundation ready - user story implementation can now begin across gateway and backend microservices.

---

## Phase 3: User Story 1 - Live Centralized Log Ingestion & Dashboard Visibility (Priority: P1) 🎯 MVP

**Goal**: All platform services (`gateway` and 7 microservices) actively stream structured application log records over standard OTLP HTTP (`http://otel-collector:4318/v1/logs`) into `otel-logs`, populating the Application Logs Dashboard.

**Independent Test**: Send test requests through the API Gateway, query OpenSearch `GET /otel-logs/_count` (must be `> 0`), and open OpenSearch Dashboards at `http://localhost:5601/app/dashboards#/view/application-logs-dashboard` to confirm volume, severity breakdown, and log stream entries render in real time.

### Tests for User Story 1 ⚠️

- [X] T007 [P] [US1] Unit test for OTLP Logback Appender and SdkLoggerProvider registration in `gateway/src/test/java/nl/invokedynamic/demo/gateway/config/TelemetryConfigTest.java`
- [X] T008 [P] [US1] Unit test for OTLP Logback Appender event dispatch and severity mapping in `services/customer-service/src/test/java/nl/invokedynamic/demo/customer/config/TelemetryConfigTest.java`

### Implementation for User Story 1

- [X] T009 [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `gateway/src/main/java/nl/invokedynamic/demo/gateway/config/TelemetryConfig.java`
- [X] T010 [P] [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/TelemetryConfig.java`
- [X] T011 [P] [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/TelemetryConfig.java`
- [X] T012 [P] [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/TelemetryConfig.java`
- [X] T013 [P] [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/TelemetryConfig.java`
- [X] T014 [P] [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/TelemetryConfig.java`
- [X] T015 [P] [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/TelemetryConfig.java`
- [X] T016 [P] [US1] Implement active OTLP Logback appender and `OtlpHttpLogRecordExporter` bean with dual stdout console output in `services/notification-service/src/main/java/nl/invokedynamic/demo/notification/config/TelemetryConfig.java`
- [X] T017 [US1] Verify live OTLP log delivery into `otel-logs` and validate Application Logs Dashboard visualization per `specs/020-active-telemetry-export/quickstart.md`

**Checkpoint**: At this point, User Story 1 (MVP) is fully functional: logs from all 8 services stream to OpenSearch and populate the dashboard.

---

## Phase 4: User Story 2 - End-to-End Distributed Trace Correlation & Span Inspection (Priority: P2)

**Goal**: Trace spans are recorded across synchronous REST and asynchronous Kafka hops with W3C Trace Context propagation, exported over OTLP HTTP (`http://otel-collector:4318/v1/traces`) into `ss4o_traces-*`, and correlated with logs via injected `traceId` and `spanId`.

**Independent Test**: Execute multi-service interaction (e.g. `curl http://localhost:8080/api/v1/restaurants`), inspect OpenSearch `GET /ss4o_traces-default-namespace/_count` (`> 0`), and open Distributed Traces Dashboard at `http://localhost:5601` to confirm spans, latencies, and correlated log records render.

### Tests for User Story 2 ⚠️

- [X] T018 [P] [US2] Integration test for W3C `traceparent` context propagation and span export in `gateway/src/test/java/nl/invokedynamic/demo/gateway/tracing/TracePropagationIntegrationTest.java`
- [X] T019 [P] [US2] Integration test for Micrometer span export and MDC trace correlation in `services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/tracing/TraceLogCorrelationIntegrationTest.java`

### Implementation for User Story 2

- [X] T020 [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `gateway/src/main/java/nl/invokedynamic/demo/gateway/config/TelemetryConfig.java`
- [X] T021 [P] [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `services/customer-service/src/main/java/nl/invokedynamic/demo/customer/config/TelemetryConfig.java`
- [X] T022 [P] [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `services/restaurant-service/src/main/java/nl/invokedynamic/demo/restaurant/config/TelemetryConfig.java`
- [X] T023 [P] [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/TelemetryConfig.java`
- [X] T024 [P] [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/config/TelemetryConfig.java`
- [X] T025 [P] [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `services/waiting-list-service/src/main/java/nl/invokedynamic/demo/waitinglist/config/TelemetryConfig.java`
- [X] T026 [P] [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `services/analytics-service/src/main/java/nl/invokedynamic/demo/analytics/config/TelemetryConfig.java`
- [X] T027 [P] [US2] Implement `OtlpHttpSpanExporter` and `BatchSpanProcessor` beans with W3C propagation and MDC injection in `services/notification-service/src/main/java/nl/invokedynamic/demo/notification/config/TelemetryConfig.java`
- [X] T028 [US2] Verify end-to-end distributed trace ingestion into `ss4o_traces-default-namespace` and validate Distributed Traces Dashboard per `specs/020-active-telemetry-export/quickstart.md`

**Checkpoint**: At this point, User Stories 1 AND 2 are functional: logs and distributed traces correlate seamlessly across all platform services.

---

## Phase 5: User Story 3 - Unified Platform Observability & Automated Build Quality (Priority: P3)

**Goal**: Telemetry pipeline operates non-blockingly with bounded queues (`2048 records`), suppresses routine `/actuator/health/**` probes, and passes 100% of Maven reactor builds and Jib container packaging.

**Independent Test**: Simulate collector unavailability, verify requests complete without delay or thread starvation, run `./mvnw clean test` across all 10 modules, and build container images via `./mvnw package jib:dockerBuild`.

### Tests for User Story 3 ⚠️

- [X] T029 [P] [US3] Unit test for buffer capacity bounds (`2048 records`) and non-blocking drop behavior under collector outage in `gateway/src/test/java/nl/invokedynamic/demo/gateway/config/TelemetryResilienceTest.java`
- [X] T030 [P] [US3] Integration test verifying suppression of routine `/actuator/health/**` trace spans and info logs in `services/restaurant-service/src/test/java/nl/invokedynamic/demo/restaurant/config/HealthProbeSuppressionTest.java`

### Implementation for User Story 3

- [X] T031 [US3] Configure bounded batch queue (`maxQueueSize = 2048`), drop policy, and rate-limited warning log across `gateway` and all 7 services in `gateway/src/main/java/nl/invokedynamic/demo/gateway/config/TelemetryConfig.java` and `services/*/src/main/java/nl/invokedynamic/demo/*/config/TelemetryConfig.java`
- [X] T032 [US3] Apply health probe suppression filter excluding routine `/actuator/health/**` traces and info logs across `gateway` and all 7 services in `gateway/src/main/java/nl/invokedynamic/demo/gateway/config/TelemetryConfig.java` and `services/*/src/main/java/nl/invokedynamic/demo/*/config/TelemetryConfig.java`
- [X] T033 [US3] Execute full Maven reactor build and test verification using `./mvnw clean test` across all 10 modules per Constitution Principle V
- [X] T034 [US3] Package container images with `./mvnw package -DskipTests jib:dockerBuild` and verify container launch in `infrastructure/docker-compose.apps.yml`

**Checkpoint**: All user stories complete with non-blocking resilience and verified Maven reactor build quality.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation updates, checklist sign-off, and final end-to-end acceptance validation.

- [X] T035 [P] Update observability architecture documentation and configuration properties in `docs/observability.md`
- [X] T036 Review all checklist items in `specs/020-active-telemetry-export/checklists/resilience.md` and `specs/020-active-telemetry-export/checklists/requirements.md`
- [X] T037 Execute full quickstart verification scenario per `specs/020-active-telemetry-export/quickstart.md` confirming non-zero document counts in `otel-logs` and `ss4o_traces-default-namespace`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories.
- **User Stories (Phase 3+)**:
  - User Story 1 (P1): Depends on Foundational phase. Delivers MVP log export.
  - User Story 2 (P2): Depends on Foundational phase. Extends pipeline to trace spans and correlation.
  - User Story 3 (P3): Depends on US1 and US2. Enforces resilience, health probe suppression, and build verification.
- **Polish (Phase 6)**: Depends on all user stories being completed.

### Within Each User Story

- Unit/integration tests written and validated first.
- Reusable components/beans before service configuration wiring.
- Gateway configuration followed by parallel microservice configurations.
- Checkpoint verification before advancing to next story.

### Parallel Opportunities

- **Phase 1**: T002 and T003 can execute in parallel.
- **Phase 2**: T005 and T006 can execute in parallel once T004 is defined.
- **Phase 3 (US1)**: T007 and T008 tests in parallel; T010 through T016 service configs in parallel across all 7 backend services.
- **Phase 4 (US2)**: T018 and T019 tests in parallel; T021 through T027 service configs in parallel across all 7 backend services.
- **Phase 5 (US3)**: T029 and T030 tests in parallel.
- **Phase 6**: T035 can run in parallel with T036.

---

## Parallel Example: User Story 1

```bash
# Launch backend service telemetry log configurations in parallel:
Task: "Implement active OTLP Logback appender in services/customer-service/.../TelemetryConfig.java"
Task: "Implement active OTLP Logback appender in services/restaurant-service/.../TelemetryConfig.java"
Task: "Implement active OTLP Logback appender in services/availability-service/.../TelemetryConfig.java"
Task: "Implement active OTLP Logback appender in services/reservation-service/.../TelemetryConfig.java"
Task: "Implement active OTLP Logback appender in services/waiting-list-service/.../TelemetryConfig.java"
Task: "Implement active OTLP Logback appender in services/analytics-service/.../TelemetryConfig.java"
Task: "Implement active OTLP Logback appender in services/notification-service/.../TelemetryConfig.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Implement User Story 1 (Phase 3) for Gateway and all 7 services.
3. Validate `otel-logs` index in OpenSearch and verify the Application Logs Dashboard at `http://localhost:5601`.
4. Deploy/demonstrate working log export (MVP achieved!).

### Incremental Delivery

1. Setup + Foundational → Pipeline infrastructure ready.
2. User Story 1 (P1) → Live centralized logging visible in OpenSearch Dashboards (MVP).
3. User Story 2 (P2) → Distributed trace spans and MDC log correlation visible in OpenSearch Dashboards.
4. User Story 3 (P3) → Non-blocking queue bounds, health probe suppression, and full reactor test verification.
5. Polish → Docs, checklist sign-off, quickstart verification.

---

## Notes

- `[P]` tasks target different files with no dependencies on incomplete tasks.
- `[Story]` label (`[US1]`, `[US2]`, `[US3]`) maps tasks to specific user stories for end-to-end traceability.
- Always use `./mvnw` instead of `mvn` per project instructions.
- Java 26 baseline with Spring Boot 4.1.1.
