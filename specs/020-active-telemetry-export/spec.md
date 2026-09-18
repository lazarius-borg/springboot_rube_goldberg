# Feature Specification: Active Telemetry Export for Logs and Traces

**Feature Branch**: `020-active-telemetry-export`

**Created**: 2026-09-18

**Status**: Draft

**Input**: User description: "Configure active OTLP log and trace export across all microservices to OpenSearch via OpenTelemetry Collector"

---

## Clarifications

### Session 2026-09-18

- Q: How should the platform services configure log filtering and minimal severity thresholds for OTLP export to OpenSearch? → A: Export all levels including DEBUG to OpenSearch by default across all services (Option C).
- Q: How should the platform services handle OTLP export failures if the OpenTelemetry Collector becomes permanently unreachable during operation? → A: Drop telemetry records when the internal in-memory buffer is full, logging an intermittent rate-limited warning and continuing normal business transactions (Option A).
- Q: How should the platform handle telemetry emission for high-frequency infrastructure health and readiness probes (such as `/actuator/health` and `/actuator/health/readiness`)? → A: Suppress tracing for `/actuator/health/**` probes and omit regular periodic health check logs, capturing health events only on degraded/failure states (Option A).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Live Centralized Log Ingestion & Dashboard Visibility (Priority: P1) 🎯 MVP

As a software engineer, site reliability engineer, or platform operator,  
I want all platform services (`gateway` and all 7 microservices) to actively stream application log records to the centralized search engine over standard OpenTelemetry protocols,  
So that I can immediately inspect, filter, and analyze live structured logs and error diagnostics in the pre-provisioned OpenSearch "Application Logs Dashboard" without empty-state indicators or manual log file scraping.

**Why this priority**: Observability dashboards currently show zero log records (`docs.count = 0`). Centralized logging is the primary diagnostic capability required for diagnosing runtime issues across microservice boundaries.

**Independent Test**: Trigger user or system operations across the API Gateway (e.g., retrieving restaurant details or customer profiles), navigate to the OpenSearch "Application Logs Dashboard" at `http://localhost:5601`, and verify that log volume, severity distribution, service breakdowns, and log stream entries appear with non-zero counts and real-time updates.

**Acceptance Scenarios**:

1. **Given** running platform services, **When** any microservice or the API Gateway logs an application event or warning/error, **Then** the event is delivered over standard OTLP protocols to the OpenTelemetry Collector and indexed into `otel-logs`.
2. **Given** log events indexed into the log store, **When** an operator views the Application Logs Dashboard, **Then** visual panels show log event volume over time, counts broken down by severity level, counts by service name, and an interactive log table populated with matching rows.
3. **Given** active log streaming, **When** developers inspect container standard output locally, **Then** human-readable console logging continues to function normally alongside remote log delivery.

---

### User Story 2 - End-to-End Distributed Trace Correlation & Span Inspection (Priority: P2)

As a software engineer troubleshooting inter-service latency or transaction lifecycles,  
I want distributed trace spans to be actively recorded, propagated across HTTP and asynchronous Kafka boundaries, and delivered to the centralized search engine,  
So that I can follow requests end-to-end across multiple microservices and inspect span latencies, call hierarchies, and correlated logs in the "Distributed Traces Dashboard".

**Why this priority**: Distributed tracing provides end-to-end operational visibility into cascading multi-service requests. Without active trace delivery, developers cannot isolate distributed bottlenecks or correlate traces with logs across service hops.

**Independent Test**: Execute a multi-service business interaction (e.g. creating a reservation or checking availability through the Gateway), open the OpenSearch "Distributed Traces Dashboard" and "Platform Observability Overview", and confirm that transaction span throughput, operation names, latencies, and service names render with live non-zero data.

**Acceptance Scenarios**:

1. **Given** an incoming HTTP request received at the API Gateway, **When** the request is forwarded downstream to microservices or publishes events to Kafka, **Then** the distributed trace context (W3C standard) is preserved across all network hops.
2. **Given** active distributed trace sampling, **When** spans complete, **Then** they are transmitted over standard OTLP protocols to the OpenTelemetry Collector and indexed into the trace data stream (`ss4o_traces-*`).
3. **Given** a trace with a specific Trace ID, **When** viewing the corresponding log records, **Then** all log entries emitted by all participating services contain the exact same Trace ID and valid Span ID in their structured metadata.

---

### User Story 3 - Unified Platform Observability & Automated Build Quality (Priority: P3)

As a platform engineer or continuous integration maintainer,  
I want telemetry export to operate non-blockingly and integrate seamlessly with multi-module Maven reactor builds and container packaging,  
So that telemetry transmission never impairs transaction processing, causes container startup failures, or disrupts automated test suites.

**Why this priority**: Reliability is paramount. If the telemetry collector or search backend experiences temporary latency or downtime, user transactions must remain unaffected, and all automated builds must pass cleanly.

**Independent Test**: Run the full Maven reactor test suite (`./mvnw clean test`) and container packaging builds (`./mvnw package jib:dockerBuild`), confirming 100% pass rates and verified telemetry delivery.

**Acceptance Scenarios**:

1. **Given** the OpenTelemetry Collector is temporarily unreachable or slow, **When** customer requests arrive, **Then** services buffer or drop telemetry non-blockingly without hanging, throwing unhandled exceptions, or delaying HTTP responses.
2. **Given** all 10 modules in the repository, **When** running `./mvnw clean test`, **Then** 100% of unit, slice, and integration tests succeed cleanly.
3. **Given** container packaging with Jib, **When** container images are built, **Then** images package and start successfully with active telemetry capabilities enabled.

---

### Edge Cases

- **Collector Disconnection or Startup Lag**: When the OpenTelemetry Collector container starts slower than the microservices or experiences a network restart, services must queue telemetry in a bounded in-memory buffer (maximum 2048 items per queue, total heap memory bounded to `< 32MB`) without causing OutOfMemoryError or crashing application threads. In-memory enqueue operations must complete in `< 5ms`. If the buffer is exceeded, excess records are dropped (`drop-newest` policy) with an intermittent rate-limited warning log (max once every 60 seconds) to safeguard business request latency.
- **Partial Collector Failures**: Traces and logs use independent HTTP exporter channels. If the logs endpoint (`/v1/logs`) experiences downtime (e.g. HTTP 5xx) while the traces endpoint (`/v1/traces`) is healthy (or vice versa), each pipeline must fail and drop records independently without impacting the sibling telemetry stream or business transactions.
- **Graceful Shutdown Flush**: During JVM or Spring ApplicationContext shutdown, `SdkTracerProvider` and `SdkLoggerProvider` execute an asynchronous flush with a hard timeout of `5000ms` (5 seconds) to drain in-memory queues before container termination.
- **Oversized Payloads & Truncation**: Log messages exceeding 32KB and exception stack traces exceeding 64KB must be automatically truncated with a `[TRUNCATED]` suffix to protect application memory and prevent OpenSearch bulk indexing rejection.
- **HTTP 4xx vs 5xx Telemetry Behavior**: Client errors (HTTP 4xx) must record spans with status `OK` or `UNSET` and log at `WARN` or `INFO` level. Server errors (HTTP 5xx) must record spans with status `ERROR` and log at `ERROR` level with stack trace details.
- **Sensitive Data Redaction**: Log records and trace attributes must never include sensitive authentication credentials, Bearer tokens, or passwords.
- **Unauthenticated / Permitted Endpoints & Probes**: Static assets, swagger documentation, and public API calls must generate valid telemetry without authentication errors. Periodic infrastructure health probes (`/actuator/health/**`) must be suppressed from generating routine trace spans and informational logs to avoid flooding OpenSearch storage, emitting events only when a degraded or failure state is detected (HTTP status code != 200 or Actuator health status != `UP`).
- **Trace Context Propagation Failure**: If an external request enters the Gateway without standard trace headers, a new root trace ID must be generated and propagated downstream so no request goes untraced.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The platform MUST actively export structured application log records over standard OpenTelemetry Protocol (OTLP) to the OpenTelemetry Collector across the API Gateway and all 7 microservices (`customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `analytics-service`, `notification-service`).
- **FR-002**: The logging subsystem across all platform services MUST provide dual output: human-readable console logging on stdout for local container inspection, and structured telemetry export over OTLP including all log severity levels (`DEBUG`, `INFO`, `WARN`, `ERROR`) by default.
- **FR-003**: Every exported log record MUST populate standard structured attributes: timestamp, service name, severity level, logger name, thread name, message, and correlated `traceId` and `spanId` when executing within a traced request context.
- **FR-004**: The tracing subsystem across all platform services MUST actively export finished distributed trace spans over standard OTLP to the OpenTelemetry Collector.
- **FR-005**: All platform services MUST enforce 100% distributed trace sampling by default in development and container profiles, with configurable external overrides.
- **FR-006**: Distributed trace context MUST be propagated across all synchronous HTTP and asynchronous Kafka boundaries using standard W3C Trace Context headers (`traceparent`).
- **FR-007**: When executing within an active transaction span, log entries MUST automatically correlate with the trace context by injecting `traceId` and `spanId` into the diagnostic context and exported log payload.
- **FR-008**: User and API interactions traversing the API Gateway MUST result in non-zero document counts in both the `otel-logs` and `ss4o_traces-*` OpenSearch indices.
- **FR-009**: The pre-provisioned OpenSearch Dashboards (Application Logs Dashboard, Distributed Traces Dashboard, and Platform Observability Overview) at `http://localhost:5601` MUST render real-time visualizations and log/trace records without manual configuration.
- **FR-010**: All Maven reactor builds (`./mvnw clean test`) across all modules and container builds (`./mvnw package jib:dockerBuild`) MUST pass cleanly with 100% test success rate.

---

### Key Entities

- **Structured Log Record**: Telemetry event emitted by a service containing a timestamp, originating service name, severity level (`DEBUG`, `INFO`, `WARN`, `ERROR`), logger, message body, and optional distributed trace correlation identifiers (`traceId`, `spanId`).
- **Distributed Trace Span**: A timed unit of work representing an operation within a transaction, characterized by a Trace ID, Span ID, parent Span ID, operation name, start/end timestamps, duration, status, and contextual attributes.
- **Telemetry Collector**: An intermediary service (`otel-collector`) that ingests OTLP logs and traces over HTTP/gRPC, batches records, and exports them to their designated OpenSearch indices (`otel-logs` and `ss4o_traces-*`).
- **Observability Dashboard**: Pre-configured visualization suite in OpenSearch Dashboards rendering real-time aggregated metrics, log streams, and transaction latency distributions.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of platform services (`gateway` and 7 microservices) actively stream logs and traces over OTLP upon handling application requests.
- **SC-002**: Within 5 seconds of executing business requests through the API Gateway, matching log entries appear in `otel-logs` and matching spans appear in `ss4o_traces-*` with non-zero document counts.
- **SC-003**: 100% of provisioned panels on the "Application Logs Dashboard" and "Distributed Traces Dashboard" render populated graphical and tabular data during active usage.
- **SC-004**: 100% of inter-service REST requests and Kafka event messages preserve the originating W3C trace identifier, achieving end-to-end trace correlation across all involved services.
- **SC-005**: 100% pass rate on full Maven reactor test suite (`./mvnw clean test`) and zero compilation or container packaging errors.
- **SC-006**: In-process telemetry export introduces `< 2%` baseline CPU overhead under standard operating conditions (<1000 requests/sec), with HTTP connect timeouts configured at `<= 1000ms` and read timeouts at `<= 3000ms`.

---

## Assumptions

- OpenSearch 2.19.0, OpenSearch Dashboards 2.19.0, and OpenTelemetry Collector Contrib 0.119.0 are running and accessible via Docker Compose networks.
- OpenSearch Dashboards index patterns (`otel-logs*`, `ss4o_traces-*`) and dashboard objects were provisioned in feature 012 and are fully operational.
- Spring Boot 4.1.1 and Java 26 are the baseline application runtime environment.
- In containerized environments, the OpenTelemetry Collector is reachable at `http://otel-collector:4318`, while in local development it is reachable at `http://localhost:4318`.
- Automatic redaction of sensitive credentials (e.g., Bearer tokens) prevents secret leakage in telemetry records.
