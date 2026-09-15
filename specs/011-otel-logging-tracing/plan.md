# Implementation Plan: OpenTelemetry Centralized Log Collection and Distributed Tracing

**Branch**: `011-otel-logging-tracing` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/011-otel-logging-tracing/spec.md`

---

## Summary

This feature establishes end-to-end observability across the Spring Boot Rube Goldberg platform by configuring structured logging, OTLP log shipping, and distributed tracing correlation across all 8 platform services (`gateway` and 7 microservices), aggregating data into OpenSearch via the OpenTelemetry Collector, and providing comprehensive documentation in `README.md`:
1. **Dual Logging Output & Structured OTLP Shipping**: Enable Spring Boot native OTLP logging and OpenTelemetry logging export across all services. Retain human-readable console logging on stdout while streaming structured log records asynchronously over OTLP to `http://otel-collector:4318/v1/logs`.
2. **Distributed Tracing & W3C Trace Context**: Configure Micrometer Tracing with OpenTelemetry bridge (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`) across all services. Enable Spring Kafka Observation to propagate W3C `traceparent` across Kafka event topics, ensuring complete trace-log correlation.
3. **Signal-Dedicated OpenSearch Index Routing**: Configure `infrastructure/opentelemetry/otel-collector-config.yaml` to route logs to `otel-logs` and traces to `otel-traces` in OpenSearch, avoiding schema conflicts.
4. **Resiliency & Sensitive Data Sanitization**: Enforce asynchronous non-blocking telemetry export and automatic sanitization of `Authorization` headers and bearer tokens.
5. **Architectural & Operational Documentation**: Update `README.md` with an in-depth observability guide explaining the OTEL pipeline architecture, data flows to OpenSearch, configuration reference, and query instructions.

---

## Technical Context

**Language/Version**: Java 26, Spring Boot 4.1.1, OpenTelemetry Collector Contrib 0.119.0, OpenSearch 2.19.0, OpenSearch Dashboards 2.19.0  
**Primary Dependencies**: `io.opentelemetry:opentelemetry-exporter-otlp`, `io.micrometer:micrometer-tracing-bridge-otel`, `org.springframework.boot:spring-boot-starter-actuator`, Spring Kafka Observation  
**Storage**: OpenSearch 2.19.0 (`otel-logs` and `otel-traces` indices)  
**Testing**: JUnit 5, AssertJ, Spring Boot Test (`@SpringBootTest`, `@WebMvcTest`), Spring Kafka Test  
**Target Platform**: Linux containers / Docker Compose environment on local/server deployments  
**Project Type**: Multi-module Maven Reactor microservices platform  
**Performance Goals**: Log export latency < 1s; zero impact on core transaction latency; non-blocking memory buffering during collector disconnects  
**Constraints**: Dual logging output (console + OTLP); W3C Trace Context propagation across HTTP and Kafka; automatic sanitization of Authorization headers; 100% default trace sampling with externalized tuning  
**Scale/Scope**: 8 services (`gateway`, `customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `analytics-service`, `notification-service`), `infrastructure/opentelemetry/`, `README.md`  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evaluation |
|---|---|---|
| **I. Strict Specification Adherence** | **PASS** | Implements all requirements (FR-001 through FR-010), user stories P1–P4, and clarification decisions without undocumented deviations. |
| **II. Maven Reactor & Microservices Architecture** | **PASS** | Respects reactor hierarchy; dependencies are managed in root POM and submodules; service boundaries remain cleanly encapsulated. |
| **III. Modern Spring Boot Feature Showcase** | **PASS** | Demonstrates modern Spring Boot Actuator OTLP logging, Micrometer Tracing bridge, Spring Kafka observation, and profile-based externalized configuration. |
| **IV. Agentic AI-Aided Development & Traceability** | **PASS** | Spec, plan, research, data model, contracts, and quickstart maintain strict bidirectional traceability. |
| **V. Comprehensive Testing & Quality Gates** | **PASS** | Unit tests and slice tests will verify log emission, trace context propagation, and non-blocking resiliency; clean Maven reactor verification is preserved. |

---

## Project Structure

### Documentation (this feature)

```text
specs/011-otel-logging-tracing/
├── spec.md              # Feature specification & clarifications
├── plan.md              # Implementation plan (this file)
├── research.md          # Technical research & architectural decisions
├── data-model.md        # Log & trace schemas and OpenSearch index mappings
├── quickstart.md        # End-to-end verification and query guide
├── contracts/           # Telemetry pipeline and OpenSearch query contracts
│   ├── telemetry-pipeline-contract.md
│   └── opensearch-telemetry-contract.md
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository touchpoints)

```text
infrastructure/
└── opentelemetry/
    └── otel-collector-config.yaml                       # Dedicated indices: otel-logs & otel-traces

README.md                                               # Observability architecture & OpenSearch query guide

pom.xml                                                 # Root dependency management for OTEL/tracing

gateway/
└── src/main/resources/application.yml                  # OTLP logging, tracing & W3C config

services/customer-service/
└── src/main/resources/application.yml                  # OTLP logging, tracing & W3C config

services/restaurant-service/
└── src/main/resources/application.yml                  # OTLP logging, tracing & W3C config

services/availability-service/
└── src/main/resources/application.yml                  # OTLP logging, tracing & Kafka observation config

services/reservation-service/
└── src/main/resources/application.yml                  # OTLP logging, tracing & Kafka observation config

services/waiting-list-service/
└── src/main/resources/application.yml                  # OTLP logging, tracing & Kafka observation config

services/analytics-service/
└── src/main/resources/application.yml                  # OTLP logging, tracing & Kafka observation config

services/notification-service/
└── src/main/resources/application.yml                  # OTLP logging, tracing & Kafka observation config
```

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations detected. Standard Spring Boot 4.x Actuator and OpenTelemetry ecosystem conventions utilized.*
