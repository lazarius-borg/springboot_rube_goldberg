# Feature Specification: OpenTelemetry Centralized Log Collection and Distributed Tracing

**Feature Branch**: `011-otel-logging-tracing`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "log collection and tracing - It should be exlained in the README that OTEL is used for logs collection and tracing, and how the services are configured to ship the logs and tracing data to OpenSearch via OTEL. Also, it appears that the micro-services don't have logging configured. If this observation is true, the logging for all microservices should be configured and the logs should be shipped to OpenSearch via OTEL."

## Clarifications

### Session 2026-09-15

- Q: When services stream structured logs to the OpenTelemetry Collector, should standard console logging remain active for local inspection? → A: Dual Logging Output (Option A) — Maintain formatted console logging on standard output for local terminal and container inspection while asynchronously streaming structured telemetry over OTLP to the OpenTelemetry Collector and OpenSearch.
- Q: Which distributed tracing propagation format should be enforced across synchronous REST calls and asynchronous Kafka event messages? → A: W3C Trace Context (Option A) — Enforce standard W3C headers (traceparent, tracestate) across all HTTP requests and Kafka record headers for universal OpenTelemetry compatibility.
- Q: How should application logs and distributed traces be organized into indices within OpenSearch? → A: Signal-Dedicated Indices (Option A) — Route application logs to dedicated log indices (e.g., `otel-logs-*` / standard OTel log index) and traces to dedicated trace span indices (e.g., `otel-traces-*` / standard OTel span index), avoiding schema conflicts and allowing independent lifecycle management.
- Q: How should sensitive data (such as authentication tokens and authorization headers) be handled in structured logs and trace spans? → A: Automatic Sanitization (Option A) — Automatically redact or omit Authorization headers, bearer tokens, and sensitive credential fields from log messages and trace span attributes before exporting to OpenSearch.
- Q: What distributed trace sampling rate should be configured across the platform services by default? → A: 100% Default Sampling (Option A) — Sample 100% of requests by default for comprehensive test and development visibility, while allowing the sampling probability to be adjusted via external environment configuration for production workloads.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Centralized Microservice Structured Logging & Log Ingestion (Priority: P1) 🎯 MVP

As a platform operator or site reliability engineer,
I want all platform microservices and the API gateway to automatically generate structured log records and transmit them to a centralized search engine via an OpenTelemetry telemetry collector,
So that I can monitor application behavior, analyze errors across all services from a single interface, and eliminate the need to inspect individual service container consoles or log files manually.

**Why this priority**:
Currently, microservices only emit default, unformatted console output without shipping log events to the centralized storage backend. Operators have no single pane of glass for real-time application diagnostics across the fleet. Centralizing log shipping is the foundational capability upon which all other observability features depend.

**Independent Test**:
Can be fully tested by triggering business actions (e.g., querying restaurant availability or creating a customer profile) and verifying that structured log events from both the API gateway and the targeted microservice appear in the centralized search store with standard structured attributes (timestamp, severity level, service name, message) within seconds of execution.

**Acceptance Scenarios**:
1. **Given** running platform services, **When** any microservice or the API gateway emits an application event or error, **Then** a structured log record is generated and streamed to the telemetry collector without requiring local log file scraping.
2. **Given** log events transmitted to the telemetry collector, **When** the collector processes the incoming telemetry stream, **Then** the log entries are indexed in the centralized search store and become queryable by service name, log level, and timestamp.
3. **Given** multiple concurrent requests hitting different microservices, **When** inspecting centralized logs, **Then** each service's log records are clearly differentiated by their service identification attribute.

---

### User Story 2 - Distributed Trace Propagation & Trace-Log Correlation (Priority: P2)

As a software developer or troubleshooting engineer,
I want distributed traces to be propagated across all inter-service network boundaries (synchronous HTTP and asynchronous messaging) and correlated with application log entries,
So that I can follow a single transaction end-to-end across multiple microservices and instantly navigate between trace spans and correlated log messages.

**Why this priority**:
In a microservices architecture, a single user interaction spans multiple distributed components. Without trace correlation, diagnosing failures or high-latency bottlenecks across asynchronous message brokers and cascading service calls requires tedious, error-prone manual guesswork.

**Independent Test**:
Can be tested by submitting a multi-service transaction (e.g., creating a reservation which spans gateway, customer service, reservation service, availability service, and notification service) and verifying that:
1. A single continuous distributed trace spans all participating services.
2. All log records emitted during the handling of that request contain the identical trace identifier and corresponding span identifiers.

**Acceptance Scenarios**:
1. **Given** an incoming request at the API gateway, **When** the request travels downstream to microservices and triggers asynchronous event notifications, **Then** the unique trace identifier is preserved across all synchronous REST calls and asynchronous message queues.
2. **Given** a traced transaction running across multiple microservices, **When** inspecting the generated log records in the centralized log search interface, **Then** every log record contains the matching trace identifier and active span identifier.
3. **Given** an engineer inspecting a specific trace span, **When** querying the centralized log store with the trace identifier, **Then** all log entries produced across all involved microservices for that transaction are retrieved in chronological order.

---

### User Story 3 - Resilient Telemetry Streaming & Error Preservation (Priority: P3)

As a platform operator,
I want log and trace telemetry streaming to handle multi-line exceptions faithfully and withstand temporary telemetry collector unavailability without impacting user-facing service availability,
So that application failures can be accurately diagnosed from complete stack traces and telemetry export never degrades business transaction processing.

**Why this priority**:
Exception stack traces often get fragmented into dozens of detached single-line records if not handled properly. Furthermore, if the telemetry collector or search backend experiences a brief outage, application business logic must not block or crash.

**Independent Test**:
Can be tested by triggering an application error that throws a nested exception stack trace while observing the search store, and separately simulating a collector disconnection while executing user transactions.

**Acceptance Scenarios**:
1. **Given** an unhandled exception or error condition in a microservice, **When** the error is logged, **Then** the entire stack trace is preserved within a single structured log record rather than split across multiple detached log lines.
2. **Given** a temporary outage or network disruption between a microservice and the telemetry collector, **When** customer requests continue arriving, **Then** business operations complete successfully without hanging, blocking, or failing due to telemetry delivery errors.
3. **Given** restored connectivity to the telemetry collector, **When** the service resumes normal operation, **Then** subsequent telemetry events are transmitted seamlessly.

---

### User Story 4 - Comprehensive Observability Architecture & Operations Documentation (Priority: P4)

As a platform engineer or onboarding developer,
I want the project README to comprehensively explain how OpenTelemetry is utilized for log collection and distributed tracing, how services ship telemetry to the centralized search store, and how to verify and query the data,
So that I can understand the telemetry architecture, configure new or existing services consistently, and inspect logs and traces effectively during development and maintenance.

**Why this priority**:
Clear, accurate documentation ensures system maintainability, enables team members to onboard rapidly, and documents the operational workflow for querying and debugging telemetry data.

**Independent Test**:
Can be tested by reviewing the updated README to ensure all architectural flow diagrams, configuration instructions, telemetry endpoints, and step-by-step query verification guides are clear, complete, and reproducible on a clean setup.

**Acceptance Scenarios**:
1. **Given** the repository documentation (`README.md`), **When** an engineer reads the observability section, **Then** they find a clear architectural explanation of the OpenTelemetry pipeline showing the data flow from microservices to the telemetry collector and into the centralized search store.
2. **Given** an engineer configuring a service, **When** consulting the documentation, **Then** they find explicit instructions on the required configuration properties, telemetry endpoints, log appender conventions, and trace sampling settings.
3. **Given** an engineer seeking to verify telemetry, **When** following the documentation's verification instructions, **Then** they can successfully verify log ingestion and trace search in the centralized store using documented search queries or dashboard interfaces.

---

### Edge Cases

- **Telemetry Collector Unreachable**: What happens when a microservice starts before the telemetry collector is ready, or when the collector is restarted? Microservices MUST buffer or drop telemetry non-blockingly and continue serving core business requests without memory exhaustion or request timeout penalties.
- **Asynchronous Message Boundaries**: How does distributed tracing handle message broker interactions (event publication, queue routing, and asynchronous consumer processing)? Trace context MUST be injected into message metadata upon publish and extracted upon consumption so asynchronous background tasks link back to the parent trace.
- **Multi-Line Exception Formatting**: How does the system handle massive nested exception stack traces? Multi-line exceptions MUST be encapsulated into a single structured log event payload so that search queries do not return fragmented, orphan stack trace lines.
- **Sensitive Data in Logs**: How does the system prevent sensitive credentials or tokens (such as bearer tokens or database passwords) from leaking into centralized telemetry? Loggers MUST NOT log raw authorization headers or sensitive operational secrets.
- **High Concurrency Log Volume**: How does the logging pipeline behave under heavy traffic load? Asynchronous log processing MUST prevent I/O blocking on request worker threads.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: All platform services (the API Gateway and all microservices) MUST support dual logging output: maintaining human-readable console logging on standard output for terminal inspection while simultaneously and asynchronously exporting structured log events.
- **FR-002**: All platform services MUST automatically stream application log events over standard OpenTelemetry protocols (OTLP) to the centralized telemetry collector, populating structured attributes (timestamp, severity level, service name, thread, logger, message, trace identifier, and span identifier).
- **FR-003**: The telemetry collector MUST receive, validate, and export structured telemetry records into the centralized search store (OpenSearch) utilizing signal-dedicated indices (`otel-logs-*` for log records and `otel-traces-*` for trace spans), ensuring both signal types are indexed and searchable independently without mapping conflicts.
- **FR-004**: The system MUST generate and propagate distributed trace context using the standard W3C Trace Context format (`traceparent`, `tracestate`) across all synchronous HTTP communications and asynchronous messaging channels (Kafka record headers) across all services.
- **FR-005**: All log entries emitted during the execution of a traced operation MUST automatically correlate with the active trace context by including the corresponding Trace ID and Span ID in the structured log payload.
- **FR-006**: Multi-line exception messages and stack traces MUST be captured as a single cohesive log entity rather than fragmented across multiple individual log records.
- **FR-007**: Telemetry transmission MUST operate asynchronously relative to request processing, such that telemetry transmission delays or collector unavailability do not block or degrade customer-facing transaction throughput or availability.
- **FR-008**: The project documentation (`README.md`) MUST document:
  - The end-to-end OpenTelemetry log collection and distributed tracing architecture.
  - The telemetry pipeline data flow (Services -> OpenTelemetry Collector -> OpenSearch).
  - Microservice configuration standards for shipping logs and traces via OpenTelemetry.
  - Practical verification and query instructions for inspecting logs and correlated traces in the centralized search store.
- **FR-009**: The telemetry logging and distributed tracing exporters MUST automatically sanitize telemetry data by redacting or omitting `Authorization` headers, bearer tokens, passwords, and sensitive credential fields prior to export to the OpenTelemetry Collector and OpenSearch.
- **FR-010**: The distributed tracing system MUST default to 100% trace sampling across all services to guarantee complete visibility during testing and local execution, while supporting externalized configuration to adjust sampling probability for high-throughput environments.

### Key Entities *(include if feature involves data)*

- **Structured Log Record**: The standardized log event data model emitted by services and ingested into the search store, containing:
  - `timestamp`: ISO-8601 date and time of the event.
  - `service_name`: Identifier of the microservice producing the log.
  - `level`: Severity level (e.g., TRACE, DEBUG, INFO, WARN, ERROR).
  - `message`: The descriptive log message.
  - `logger`: Originating class/component identifier.
  - `thread`: Executing thread name.
  - `trace_id`: Unique identifier of the associated distributed trace (when within a trace context).
  - `span_id`: Identifier of the specific operation span (when within a trace context).
  - `exception`: Full multi-line stack trace details (when an exception is logged).
- **Distributed Trace Context**: The contextual telemetry metadata propagated across distributed operations:
  - `trace_id`: Globally unique 128-bit identifier tying all related operations together.
  - `span_id`: 64-bit identifier representing a specific unit of work in a service.
  - `trace_flags`: Sampling and trace configuration flags.
- **Telemetry Pipeline Configuration**: Operational parameters defining collector endpoints, protocol bindings (HTTP/gRPC), buffer thresholds, retry policies, and destination search index mappings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of platform services (the API gateway and all 7 microservices) produce structured log events that are automatically transmitted to and indexed in the centralized search store.
- **SC-002**: Emitted log events appear and are searchable in the centralized search store within 5 seconds of creation under normal operational loads.
- **SC-003**: 100% of end-to-end distributed business workflows (e.g., reservation creation traversing gateway, reservation, availability, and notification services) maintain a unified trace identifier across both HTTP and messaging boundaries.
- **SC-004**: 100% of log statements emitted within a traced request context contain valid, matching trace and span identifiers, enabling immediate bidirectional lookup between traces and logs.
- **SC-005**: 0% of multi-line error stack traces are fragmented into orphan single-line log entries in the centralized search store.
- **SC-006**: Service availability and request response times remain unaffected by telemetry streaming, experiencing zero request failures or hangs during simulated collector connection dropouts.
- **SC-007**: A newly onboarded developer can follow the updated `README.md` to trace a transaction and query its correlated logs in the search store within 10 minutes without assistance.

## Assumptions

- The platform infrastructure contains an OpenTelemetry Collector service capable of receiving telemetry data and exporting it to an OpenSearch cluster.
- OpenSearch is deployed and accessible to the OpenTelemetry Collector for storing trace and log indices.
- All services run on Java 26 and Spring Boot 4.1.x, supporting standard OpenTelemetry instrumentation and logging appenders.
- Default log level is configured to `INFO` for standard operational logs, with appropriate framework noise suppression.
- Microservices communicate internally over Docker network hostnames (e.g., `otel-collector` for the OpenTelemetry Collector and `opensearch` for search storage).
- Log shipping is performed via direct network protocol export (OTLP) to the collector, avoiding file-based sidecar scraping.
