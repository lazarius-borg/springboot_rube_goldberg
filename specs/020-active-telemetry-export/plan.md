# Implementation Plan: Active Telemetry Export for Logs and Traces

**Branch**: `020-active-telemetry-export` | **Date**: 2026-09-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/020-active-telemetry-export/spec.md`

---

## Summary

Activate real-time OpenTelemetry log and trace streaming across all 8 platform services (`gateway` and 7 microservices) by implementing idiomatic Spring Boot 4.1.1 telemetry configuration beans and an OpenTelemetry Logback appender bridge. This directly connects Logback events and Micrometer spans to the OpenTelemetry Collector (`http://otel-collector:4318`), populating `otel-logs` and `ss4o_traces-*` indices in OpenSearch with live telemetry and eliminating empty dashboards.

---

## Technical Context

**Language/Version**: Java 26, Spring Boot 4.1.1, OpenTelemetry SDK 1.62.0, Micrometer Tracing 1.7.1  
**Primary Dependencies**: `io.opentelemetry:opentelemetry-exporter-otlp`, `io.micrometer:micrometer-tracing-bridge-otel`, `ch.qos.logback:logback-classic`, Spring Boot Actuator  
**Storage**: OpenSearch 2.19.0 (`otel-logs` index and `ss4o_traces-default-namespace` data stream)  
**Testing**: JUnit 5, Mockito, Spring Boot Test, Spring Cloud Gateway Test  
**Target Platform**: Linux containers (Docker Compose) / JVM on macOS/Linux  
**Project Type**: Multi-module Maven reactor microservice system  
**Performance Goals**: Sub-5ms asynchronous in-memory buffering; zero latency penalty on business transactions; connect timeout 1000ms; read timeout 3000ms; <2% baseline CPU overhead; bounded batch export (max 2048 items, <32MB heap allocation)  
**Constraints**: Dual output (human-readable console stdout preserved + OTLP export); 100% trace sampling in development; graceful shutdown 5000ms flush timeout; strict `./mvnw` usage  
**Scale/Scope**: 8 services (`gateway`, `customer-service`, `restaurant-service`, `availability-service`, `reservation-service`, `waiting-list-service`, `analytics-service`, `notification-service`), `common/event-contracts`  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Requirement | Compliance Status | Notes |
| :--- | :--- | :---: | :--- |
| **I. Strict Specification Adherence** | Follow approved feature specification without unauthorized deviations. | **PASS** | Plan implements FR-001 through FR-010, SC-001 through SC-005, and Clarifications Q1–Q3. |
| **II. Maven Reactor & Microservices** | Maintain clean submodule boundaries in Maven reactor. | **PASS** | Telemetry configuration utilizes standard dependency management in root POM and submodules. |
| **III. Modern Spring Boot Showcase** | Use idiomatic Spring Boot conventions. | **PASS** | Uses standard Spring Boot `@Configuration` beans with OpenTelemetry SDK and Logback appender integration. |
| **IV. Agentic Traceability** | Optimize structure for deterministic pair programming and documentation. | **PASS** | Plan provides clear design artifacts (research, data model, contracts, quickstart). |
| **V. Comprehensive Quality Gates** | All tests and builds must pass cleanly. | **PASS** | Verified with `./mvnw clean test` across all 10 modules and Jib container packaging. |

---

## Project Structure

### Documentation (this feature)

```text
specs/020-active-telemetry-export/
├── spec.md              # Feature specification & clarifications
├── plan.md              # Implementation plan (this file)
├── research.md          # Technical research & decisions
├── data-model.md        # Telemetry schemas & data models
├── quickstart.md        # Step-by-step verification guide
├── contracts/
│   └── telemetry-pipeline-contract.md # OTLP pipeline contracts
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code Layout

```text
springboot_rube_goldberg/
├── pom.xml                                   # Root Maven reactor POM
├── common/
│   └── event-contracts/                      # Shared event models
├── gateway/                                  # API Gateway
│   └── src/main/java/nl/invokedynamic/demo/gateway/config/
│       └── TelemetryConfig.java              # Active OTel exporter & Logback bridge
├── services/
│   ├── customer-service/
│   ├── restaurant-service/
│   ├── availability-service/
│   ├── reservation-service/
│   ├── waiting-list-service/
│   ├── analytics-service/
│   └── notification-service/
│       └── src/main/java/nl/invokedynamic/demo/*/config/
│           └── TelemetryConfig.java          # Active OTel exporter & Logback bridge
└── infrastructure/
    └── opentelemetry/
        └── otel-collector-config.yaml        # Collector pipeline routing
```

---

## Complexity Tracking

*No violations detected. Standard Spring Boot and OpenTelemetry SDK mechanisms applied.*
