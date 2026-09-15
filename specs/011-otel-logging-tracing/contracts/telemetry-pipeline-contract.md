# Interface Contract: Telemetry Pipeline & Service Configuration

**Feature**: `011-otel-logging-tracing`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](../spec.md)

---

## 1. Service-to-Collector OTLP Export Contract

All platform services (`gateway` and all 7 microservices) communicate with the OpenTelemetry Collector via standard OpenTelemetry Protocol (OTLP) over HTTP/Protobuf.

### Protocol Endpoints
- **Logs Endpoint**: `http://otel-collector:4318/v1/logs` (Container) / `http://localhost:4318/v1/logs` (Local)
- **Traces Endpoint**: `http://otel-collector:4318/v1/traces` (Container) / `http://localhost:4318/v1/traces` (Local)
- **Content Type**: `application/x-protobuf` or `application/json`

### Standard Spring Boot Configuration Contract (`application.yml`)
Every service defines the following standardized configuration block:

```yaml
management:
  tracing:
    sampling:
      probability: ${MANAGEMENT_TRACING_SAMPLING_PROBABILITY:1.0}
    propagation:
      type: W3C
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}
    logging:
      endpoint: ${OTEL_EXPORTER_OTLP_LOGS_ENDPOINT:http://localhost:4318/v1/logs}

spring:
  kafka:
    template:
      observation-enabled: true
    listener:
      observation-enabled: true

---
spring:
  config:
    activate:
      on-profile: docker
management:
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://otel-collector:4318/v1/traces}
    logging:
      endpoint: ${OTEL_EXPORTER_OTLP_LOGS_ENDPOINT:http://otel-collector:4318/v1/logs}
```

---

## 2. Distributed Tracing Propagation Contract

Distributed trace context MUST be propagated across all network hops using the W3C standard headers.

### HTTP Request Headers
```http
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
tracestate: [optional vendor state]
```
- `00`: Version
- `4bf92f3577b34da6a3ce929d0e0e4736`: 128-bit Trace ID
- `00f067aa0ba902b7`: 64-bit Parent Span ID
- `01`: Trace flags (01 = sampled)

### Kafka Record Headers
Every published message to Kafka topics (e.g., `reservation.events`, `waiting-list.events`) automatically includes:
- Key: `traceparent` (Binary/UTF-8 string matching W3C format)

---

## 3. Telemetry Collector Pipeline Contract (`otel-collector-config.yaml`)

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 1s
    send_batch_size: 256

exporters:
  prometheus:
    endpoint: 0.0.0.0:8889
  opensearch:
    http:
      endpoint: "http://opensearch:9200"
    tls:
      insecure: true
    logs_index: "otel-logs"

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [opensearch]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
    logs:
      receivers: [otlp]
      processors: [batch]
      exporters: [opensearch]
```
