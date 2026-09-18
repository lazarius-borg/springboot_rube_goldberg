# Technical Research: Active OpenTelemetry Telemetry Export for Spring Boot 4.1.1

**Feature**: `020-active-telemetry-export`  
**Date**: 2026-09-18  

---

## 1. Context & Problem Statement

In feature `011-otel-logging-tracing` and `012-opensearch-dashboard-provisioning`, OpenSearch Dashboards and the OpenTelemetry Collector were configured. However, runtime evaluation showed `docs.count = 0` in both `otel-logs` and `ss4o_traces-default-namespace`.

### Root Cause Analysis
1. **Spring Boot 4.1.1 Autoconfiguration Evolution**:
   - In Spring Boot 3.4.x, Actuator Autoconfigure included `OtlpLoggingAutoConfiguration` and `OtlpTracingAutoConfiguration`.
   - In Spring Boot 4.1.1, `spring-boot-actuator-autoconfigure-4.1.1.jar` no longer packages those classes.
   - The properties `management.otlp.logging.endpoint` and `management.otlp.tracing.endpoint` exist in the Spring `Environment` but without active bean definitions, no network client connects or sends data to `http://otel-collector:4318`.
2. **Missing Active Export Components**:
   - `opentelemetry-exporter-otlp` 1.62.0 is present on the classpath, which provides:
     - `io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter`
     - `io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter`
   - `micrometer-tracing-bridge-otel` 1.7.1 is present on the classpath, providing the Micrometer-to-OTel bridge.
   - However, no Spring Bean connects Logback to the OpenTelemetry Logs SDK or publishes Micrometer spans to the OTLP span exporter.

---

## 2. Technical Decisions

### Decision 1: Shared Observability Auto-Configuration Module in `common/` or Service Configurations
- **Option A**: Implement an idiomatic Spring Boot `@Configuration` class in a shared module or within each service configuration package (e.g. `nl.invokedynamic.demo.common.telemetry` or service-level config) defining:
  1. `OtlpHttpSpanExporter` and `OtlpHttpLogRecordExporter` beans reading from `management.otlp.tracing.endpoint` and `management.otlp.logging.endpoint`.
  2. An OpenTelemetry `SdkTracerProvider` with `BatchSpanProcessor(spanExporter)`.
  3. An OpenTelemetry `SdkLoggerProvider` with `BatchLogRecordProcessor(logExporter)`.
  4. An OpenTelemetry Logback Appender (`ch.qos.logback.core.Appender<ILoggingEvent>`) attached programmatically or via standard Logback XML to forward log records with traceId/spanId into OpenTelemetry.
- **Option B**: Rely on external javaagent (`-javaagent:opentelemetry-javaagent.jar`).
- **Decision**: **Option A** (Idiomatic Spring Boot beans and Logback bridge).
- **Rationale**: Keeps builds completely self-contained within the Maven reactor without requiring external binary downloads or JVM agent argument overhead in Jib containers. Complies with Constitution Principle III (Modern Spring Boot Feature Showcase) and Principle II (Maven Reactor encapsulation).

### Decision 2: Logback OpenTelemetry Bridge
- **Decision**: Programmatically register a lightweight `OpenTelemetryAppender` or provide standard `logback-spring.xml` in each service that delegates logging events (`ILoggingEvent`) to `OpenTelemetry.getLogsBridge().loggerBuilder(...)`.
- **Rationale**:
  - Captures exact logger name, thread, timestamp, message, exception stack traces, and severity.
  - Automatically reads `MDC.get("traceId")` and `MDC.get("spanId")` from Micrometer tracing context.
  - Dual output: Standard console `ConsoleAppender` remains active and unaffected.
  - OTLP emission is asynchronous and non-blocking via `BatchLogRecordProcessor`.

### Decision 3: Tracing Span Export Bridge
- **Decision**: Define a Spring `@Bean` returning `io.opentelemetry.sdk.trace.SpanProcessor` wrapping `OtlpHttpSpanExporter`.
- **Rationale**:
  - `micrometer-tracing-bridge-otel` detects `SpanProcessor` and `OtlpHttpSpanExporter` beans in the Spring ApplicationContext and dispatches all finished spans to them automatically.
  - Preserves W3C Trace Context propagation across HTTP and Kafka boundaries.

### Decision 4: OpenSearch Trace Ingestion & Index Alignment
- **Decision**:
  - The OpenTelemetry Collector's `opensearch` exporter automatically writes traces to `ss4o_traces-{dataset}-{namespace}` (defaulting to `ss4o_traces-default-namespace`), matching the `ss4o_traces-*` index pattern in OpenSearch Dashboards.
  - Logs are routed to `otel-logs`, matching the `otel-logs*` index pattern in OpenSearch Dashboards.
  - Probes to `/actuator/health/**` are suppressed from trace export.

---

## 3. Best Practices & Safety
1. **Collector Unavailability**: `BatchLogRecordProcessor` and `BatchSpanProcessor` maintain a bounded queue (default 2048 items) and drop records non-blockingly when full if the collector is down, fulfilling clarification Q2.
2. **Sanitization**: Authorization headers and credentials continue to be excluded from span tags and log bodies.
