# Technical Research: OpenTelemetry Centralized Log Collection and Distributed Tracing

**Feature**: `011-otel-logging-tracing`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](./spec.md)

---

## 1. OTLP Log Shipping Mechanism in Spring Boot

### Context
All 8 platform services (`gateway` and 7 microservices) need to emit structured logs and stream them over OTLP to the OpenTelemetry Collector (`http://otel-collector:4318/v1/logs`) while continuing to output standard readable logs to the container console for local `docker compose logs` inspection.

### Evaluated Alternatives
1. **Spring Boot Native OTLP Logging (Spring Boot 3.4+ / 4.x)**:
   - Built into Spring Boot Actuator and auto-configured when `io.opentelemetry:opentelemetry-exporter-otlp` is present.
   - Configured cleanly in `application.yml`:
     ```yaml
     management:
       otlp:
         logging:
           endpoint: ${OTEL_EXPORTER_OTLP_LOGS_ENDPOINT:http://localhost:4318/v1/logs}
     ```
   - Automatically bridges Logback events to the OpenTelemetry `SdkLoggerProvider` without requiring custom XML files.
   - Standard console output remains active by default, cleanly fulfilling the **Dual Logging Output** requirement.
2. **Custom `logback-spring.xml` with `OpenTelemetryAppender`**:
   - Manually define Logback XML configurations with `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`.
   - Requires maintaining 8 XML files or complex XML imports across all microservices.
3. **Container-level Log Scraping (FluentBit / Vector)**:
   - Scrape stdout logs and parse JSON via an extra sidecar container.
   - Adds operational overhead, requires Docker daemon socket access, and decouples log collection from in-process trace context.

### Decision
**Adopt Spring Boot Native OTLP Logging Support**.
- **Rationale**: Modern Spring Boot provides native, first-class OTLP logging auto-configuration when `io.opentelemetry:opentelemetry-exporter-otlp` and `spring-boot-starter-actuator` are present on the classpath. It bridges Logback directly into OpenTelemetry's log exporter, preserves correlation with Micrometer trace context, and keeps standard console logging intact without needing boilerplate XML files in every module.

---

## 2. Distributed Trace Context Propagation across HTTP and Kafka

### Context
When a request flows through the API Gateway, makes REST calls, and produces/consumes Kafka events, trace context must follow the transaction across every boundary without loss.

### Evaluated Alternatives
1. **W3C Trace Context via Micrometer Tracing + Spring Kafka Observation**:
   - HTTP: `micrometer-tracing-bridge-otel` automatically instruments `RestClient`, `WebClient`, `RestTemplate`, and Spring Cloud Gateway, injecting and extracting `traceparent` and `tracestate` headers per the W3C recommendation.
   - Kafka: Enabling `spring.kafka.template.observation-enabled: true` and `spring.kafka.listener.observation-enabled: true` enables automatic trace propagation in Kafka message headers.
2. **B3 Propagation (Zipkin format)**:
   - Uses `X-B3-TraceId` and `X-B3-SpanId`.
   - Non-standard in modern OpenTelemetry ecosystems; rejected in favor of W3C.

### Decision
**Adopt W3C Trace Context with Spring Kafka Observation Enabled**.
- **Rationale**: W3C Trace Context (`traceparent`) is the universally accepted standard for distributed tracing. Spring Boot and Spring Kafka natively support Observation-based context injection and extraction, ensuring zero manual header plumbing.

---

## 3. OpenSearch Exporter & Index Routing in OpenTelemetry Collector

### Context
The OpenTelemetry Collector Contrib (`otel/opentelemetry-collector-contrib:0.119.0`) is already configured with an `opensearch` exporter (`http://opensearch:9200`). We need to guarantee that application logs and distributed traces are routed to signal-dedicated indices (`otel-logs-*` and `otel-traces-*`) without mapping conflicts.

### Evaluated Alternatives
1. **Explicit Index Configuration in `otel-collector-config.yaml`**:
   - Specify `logs_index: "otel-logs"` and `traces_index: "otel-traces"` in the `opensearch` exporter configuration.
   - Generates clean, predictable daily/stream indices (`otel-logs` and `otel-traces`) easily queried via OpenSearch Dashboards (`http://localhost:5601`) and OpenSearch REST APIs (`http://localhost:9200/otel-logs/_search`).
2. **Default OpenTelemetry APM Index Pattern**:
   - Uses `otel-v1-apm-logs` and `otel-v1-apm-span`.
3. **Single Unified Index**:
   - Storing logs and traces together leads to field collisions (e.g. conflicting mappings for `attributes`, `name`, `status`). Rejected per Clarification 3.

### Decision
**Configure explicit signal-dedicated indices in `infrastructure/opentelemetry/otel-collector-config.yaml`**:
```yaml
exporters:
  opensearch:
    http:
      endpoint: "http://opensearch:9200"
    tls:
      insecure: true
    logs_index: "otel-logs"
    traces_index: "otel-traces"
```
- **Rationale**: Explicitly defining `logs_index` and `traces_index` guarantees index separation, avoids OpenSearch mapping errors, and provides intuitive index patterns (`otel-logs` and `otel-traces`) for developers and operators.

---

## 4. Sensitive Data Sanitization (Bearer Tokens & Credentials)

### Context
In microservice architectures, incoming requests contain `Authorization: Bearer <JWT>` headers. These tokens must never be exported in cleartext into OpenSearch log records or trace span attributes.

### Evaluated Alternatives
1. **Actuator & Observation Header Redaction Configuration**:
   - Spring Boot Actuator and Micrometer observation allow configuring header exclusion or sanitization for HTTP server and client observations.
   - For logs: Standardize logging patterns and advise against logging full raw HTTP headers in interceptors or log filters.
2. **OpenTelemetry Collector Redaction Processor (`redaction`)**:
   - Add a redaction processor in the collector pipeline to scrub token patterns via regex.
3. **In-Service Header Filtering**:
   - Ensure WebFlux / WebMvc security filters and Spring Cloud Gateway do not dump authorization headers to trace attributes.

### Decision
**Apply In-Process Header Sanitization & Collector Redaction Policy**.
- **Rationale**: Defense-in-depth: services do not attach `Authorization` headers to trace span attributes by default in Spring Boot's Observation API, and logging conventions ensure tokens are masked or omitted in application loggers.

---

## 5. Resilient Non-Blocking Telemetry Export

### Context
If the OpenTelemetry Collector container is temporarily restarting or unreachable, microservice request handling must not block, hang, or throw exceptions.

### Evaluated Alternatives
1. **Asynchronous Batch Exporter with Memory Bounded Queue**:
   - OpenTelemetry's OTLP exporter uses an internal in-memory ring buffer/batch processor.
   - Log records and spans are queued in memory and dispatched in background daemon threads.
   - When the queue fills up due to collector downtime, records are dropped non-blockingly, and the service continues serving HTTP requests without latency penalty.
2. **Synchronous Blocking Export**:
   - Blocks the calling request thread on network socket I/O.
   - Causes cascading service failure if collector is down. Rejected.

### Decision
**Standard Asynchronous Batch Export with Bounded Queue**.
- **Rationale**: Ensures zero degradation of customer-facing transactions during telemetry outages.
