# Implementation Tasks: OpenTelemetry Centralized Log Collection and Distributed Tracing

**Feature**: `011-otel-logging-tracing`  
**Date**: 2026-09-15  
**Plan Reference**: [plan.md](./plan.md) | **Spec Reference**: [spec.md](./spec.md)

---

## Phase 1: Setup (Shared Infrastructure & Collector Configuration)

**Purpose**: Establish telemetry ingestion topology in OpenTelemetry Collector and shared dependency baseline in Maven reactor.

- [X] T001 Configure OpenTelemetry Collector with signal-dedicated OpenSearch indices (`logs_index: "otel-logs"` and `traces_index: "otel-traces"`) in `infrastructure/opentelemetry/otel-collector-config.yaml`
- [X] T002 [P] Verify and ensure OpenTelemetry and Micrometer tracing dependencies (`opentelemetry-exporter-otlp`, `micrometer-tracing-bridge-otel`) are managed in root `pom.xml`

---

## Phase 2: Foundational (Blocking Telemetry Configuration Across All Services)

**Purpose**: Standardize OTLP logging, distributed tracing, W3C propagation, and Kafka observation properties across all platform services.

**⚠️ CRITICAL**: Must be completed before implementing individual user stories.

- [X] T003 [P] Configure standard OTLP logging, tracing, and dual output in `gateway/src/main/resources/application.yml`
- [X] T004 [P] Configure standard OTLP logging, tracing, and Kafka observation in `services/customer-service/src/main/resources/application.yml`
- [X] T005 [P] Configure standard OTLP logging, tracing, and Kafka observation in `services/restaurant-service/src/main/resources/application.yml`
- [X] T006 [P] Configure standard OTLP logging, tracing, and Kafka observation in `services/reservation-service/src/main/resources/application.yml`
- [X] T007 [P] Configure standard OTLP logging, tracing, and Kafka observation in `services/availability-service/src/main/resources/application.yml`
- [X] T008 [P] Configure standard OTLP logging, tracing, and Kafka observation in `services/waiting-list-service/src/main/resources/application.yml`
- [X] T009 [P] Configure standard OTLP logging, tracing, and Kafka observation in `services/analytics-service/src/main/resources/application.yml`
- [X] T010 [P] Configure standard OTLP logging, tracing, and Kafka observation in `services/notification-service/src/main/resources/application.yml`

**Checkpoint**: Core telemetry configuration established across all 8 platform services.

---

## Phase 3: User Story 1 - Centralized Microservice Structured Logging & Ingestion (Priority: P1) 🎯 MVP

**Goal**: Ensure all platform services produce structured log events with required fields (timestamp, severity, serviceName, logger, thread, message) and stream them via OTLP to OpenSearch while maintaining console output.

**Independent Test**: Trigger an API request to any service and verify formatted console logs in terminal and structured log documents in OpenSearch (`http://localhost:9200/otel-logs/_search`).

- [X] T011 [P] [US1] Create unit test verifying structured logging and OTLP exporter configuration in `gateway/src/test/java/nl/invokedynamic/demo/gateway/GatewayLoggingTest.java`
- [X] T012 [P] [US1] Create unit test verifying structured logging and OTLP exporter configuration in `services/customer-service/src/test/java/nl/invokedynamic/demo/customer/CustomerServiceLoggingTest.java`
- [X] T013 [P] [US1] Create unit test verifying structured logging and OTLP exporter configuration in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/ReservationServiceLoggingTest.java`
- [X] T014 [US1] Verify and ensure dependencies for OTLP logging export and Logback bridging exist in all 8 service modules (`gateway/pom.xml` and `services/*/pom.xml`)
- [X] T015 [US1] Enrich business log statements with structured context markers in `services/reservation-service/src/main/java/nl/invokedynamic/demo/reservation/service/ReservationService.java` and `services/availability-service/src/main/java/nl/invokedynamic/demo/availability/service/AvailabilityService.java`

**Checkpoint**: User Story 1 is functional — all services emit structured logs to console and OTLP collector.

---

## Phase 4: User Story 2 - Distributed Trace Propagation & Trace-Log Correlation (Priority: P2)

**Goal**: Propagate W3C trace context (`traceparent`, `tracestate`) across HTTP calls and Kafka event boundaries, with Trace ID and Span ID automatically correlated in log records.

**Independent Test**: Create a reservation through API Gateway and verify all participating services (`reservation-service`, `availability-service`, `notification-service`) share the identical `traceId` in `otel-traces` and `otel-logs`.

- [X] T016 [P] [US2] Create integration test validating W3C tracecontext propagation and trace-log correlation in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/tracing/DistributedTracingIntegrationTest.java`
- [X] T017 [P] [US2] Create integration test verifying Kafka observation traceparent header propagation in `services/analytics-service/src/test/java/nl/invokedynamic/demo/analytics/tracing/KafkaTracingPropagationTest.java`
- [X] T018 [US2] Configure WebClient / RestClient observation and W3C trace propagation in `gateway/src/main/resources/application.yml`
- [X] T019 [US2] Enable Kafka template and listener observation (`observation-enabled: true`) across event-driven services (`services/reservation-service/src/main/resources/application.yml`, `services/availability-service/src/main/resources/application.yml`, `services/waiting-list-service/src/main/resources/application.yml`, `services/analytics-service/src/main/resources/application.yml`, `services/notification-service/src/main/resources/application.yml`)

**Checkpoint**: User Stories 1 and 2 functional — end-to-end distributed traces correlate across REST and Kafka.

---

## Phase 5: User Story 3 - Resilient Telemetry Streaming & Error Preservation (Priority: P3)

**Goal**: Preserve multi-line exception stack traces as single structured log entities, sanitize sensitive Authorization tokens, and prevent collector downtime from blocking user transactions.

**Independent Test**: Trigger an error scenario with an invalid request or unhandled exception, verify the full stack trace is intact in OpenSearch without single-line splitting, and verify Authorization headers are masked.

- [X] T020 [P] [US3] Configure header redaction and token sanitization properties in `gateway/src/main/resources/application.yml` and microservice configurations to prevent Authorization bearer tokens from leaking into trace attributes
- [X] T021 [P] [US3] Create unit test asserting `Authorization` header is redacted from trace spans and logs in `services/customer-service/src/test/java/nl/invokedynamic/demo/customer/tracing/TelemetrySanitizationTest.java`
- [X] T022 [US3] Implement non-blocking queue overflow verification test during simulated collector disconnect in `services/reservation-service/src/test/java/nl/invokedynamic/demo/reservation/tracing/CollectorResiliencyTest.java`

**Checkpoint**: Telemetry export is resilient, non-blocking, and sanitizes sensitive credentials.

---

## Phase 6: User Story 4 - Comprehensive Observability Architecture & Operations Documentation (Priority: P4)

**Goal**: Document the complete OpenTelemetry logging and tracing architecture in `README.md`, including data flows to OpenSearch, service configuration guidelines, and query instructions.

**Independent Test**: Review `README.md` to confirm an engineer can follow instructions and query logs and traces in OpenSearch within 10 minutes.

- [X] T023 [US4] Add Observability Architecture section to `README.md` with Mermaid pipeline diagram (Services -> OpenTelemetry Collector -> OpenSearch)
- [X] T024 [US4] Add Service Logging & Tracing Configuration Guide to `README.md` documenting `application.yml` properties, W3C Trace Context, and local vs docker profiles
- [X] T025 [US4] Add OpenSearch Query & Verification Guide to `README.md` detailing how to search `otel-logs`, inspect `otel-traces`, correlate by `traceId`, and use OpenSearch Dashboards (port 5601)

**Checkpoint**: Complete user-facing documentation published in `README.md`.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Quality gate verification, checklist audit, and regression testing across the reactor.

- [X] T026 [P] Review and evaluate all 29 items in `specs/011-otel-logging-tracing/checklists/observability.md`
- [X] T027 Run full Maven reactor test verification via `./mvnw clean test` across all 10 modules
- [X] T028 Validate end-to-end scenarios against `specs/011-otel-logging-tracing/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user story implementation.
- **User Story 1 (Phase 3)**: Depends on Foundational completion.
- **User Story 2 (Phase 4)**: Depends on US1 (requires working logging & tracing infrastructure).
- **User Story 3 (Phase 5)**: Depends on US1 and US2 (validates error logging and trace attributes).
- **User Story 4 (Phase 6)**: Depends on US1–US3 (documents actual implemented architecture and query commands).
- **Polish (Phase 7)**: Depends on all prior phases.

### Parallel Opportunities

- In Phase 1: `T002` can run parallel to `T001`.
- In Phase 2: Configuration tasks `T003` through `T010` touch distinct service `application.yml` files and can all run concurrently.
- In Phase 3: Test tasks `T011`, `T012`, `T013` can run in parallel.
- In Phase 4: Integration test tasks `T016` and `T017` can run in parallel.
- In Phase 5: Redaction task `T020` and test `T021` can run in parallel.
- In Phase 6: Documentation tasks `T023`, `T024`, `T025` can be developed collaboratively.

---

## Implementation Strategy

### MVP Scope (User Story 1 Only)
1. Complete Phase 1 (Setup: Collector config & dependencies).
2. Complete Phase 2 (Foundational: Service `application.yml` updates).
3. Complete Phase 3 (User Story 1: Structured logging & OTLP export).
4. **VALIDATE MVP**: Verify logs stream to both console and OpenSearch (`otel-logs`).

### Incremental Delivery Path
- **Increment 1 (MVP)**: Centralized logging via OTLP to OpenSearch (`otel-logs`).
- **Increment 2**: Distributed tracing and Kafka W3C propagation (`otel-traces`).
- **Increment 3**: Resiliency, non-blocking buffering, and token sanitization.
- **Increment 4**: Full developer documentation in `README.md`.
- **Final**: Full reactor test suite verification.
