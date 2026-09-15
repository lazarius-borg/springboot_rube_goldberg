# Data Model: OpenTelemetry Logging & Tracing Schemas

**Feature**: `011-otel-logging-tracing`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](./spec.md)

---

## 1. Structured Log Event Model

Represents application log records emitted by Spring Boot services, forwarded via OTLP to the OpenTelemetry Collector, and indexed in OpenSearch under `otel-logs`.

| Field | Type | Description | Example |
|---|---|---|---|
| `Timestamp` | DateTime (ISO-8601 / Epoch Nanos) | Exact timestamp when the log event occurred | `2026-09-15T15:30:00.123456Z` |
| `ObservedTimestamp` | DateTime | Timestamp when the collector observed the record | `2026-09-15T15:30:00.125000Z` |
| `SeverityNumber` | Integer (1–24) | OpenTelemetry numeric severity level | `9` (INFO) |
| `SeverityText` | String | Standard log level name | `INFO`, `WARN`, `ERROR` |
| `Body` | String / Object | The primary log message content | `Processing reservation booking for restaurant 1` |
| `ServiceName` | String | Originating microservice application name | `reservation-service` |
| `TraceId` | String (32 hex chars) | W3C 128-bit distributed trace identifier | `4bf92f3577b34da6a3ce929d0e0e4736` |
| `SpanId` | String (16 hex chars) | W3C 64-bit operation span identifier | `00f067aa0ba902b7` |
| `TraceFlags` | Integer | W3C trace options / sampled flag | `1` (Sampled) |
| `LoggerName` | String | Logger class / source name | `nl.invokedynamic.demo.reservation.service.ReservationService` |
| `ThreadName` | String | Name of the executing thread | `http-nio-8085-exec-1` |
| `Exception` | Object | Captured exception details if an error occurred | Full stack trace string |

---

## 2. Distributed Trace Span Model

Represents individual units of work across synchronous REST endpoints and asynchronous Kafka messaging hops, stored in OpenSearch under `otel-traces`.

| Field | Type | Description | Example |
|---|---|---|---|
| `TraceId` | String (32 hex chars) | Shared unique identifier across all services in the transaction | `4bf92f3577b34da6a3ce929d0e0e4736` |
| `SpanId` | String (16 hex chars) | Unique identifier for this specific span | `5fb397be34d23b0f` |
| `ParentSpanId` | String (16 hex chars) | Span identifier of the calling parent operation | `00f067aa0ba902b7` |
| `Name` | String | Human-readable operation descriptor | `POST /api/v1/reservations` |
| `Kind` | Enum | Span role in the distributed call tree | `SERVER`, `CLIENT`, `PRODUCER`, `CONSUMER` |
| `StartTime` | Epoch Nanos | Start time of operation | `1789486200123000000` |
| `EndTime` | Epoch Nanos | Completion time of operation | `1789486200185000000` |
| `Duration` | Long (Nanoseconds) | Calculated execution duration | `62000000` (62 ms) |
| `Status` | Object | Operation outcome code (`STATUS_CODE_OK`, `STATUS_CODE_ERROR`) | `{ "code": 1 }` |
| `ServiceName` | String | Service executing the span | `reservation-service` |
| `Attributes` | Map<String, Any> | Semantic attributes describing operation parameters | `http.method: POST`, `http.status_code: 201`, `messaging.destination: reservation.events` |

---

## 3. OpenSearch Index Schema Mappings

OpenSearch receives telemetry via the OpenTelemetry Collector's `opensearch` exporter. The indices are segregated into:

### Log Index (`otel-logs`)
- Document structure:
  ```json
  {
    "timestamp": "2026-09-15T15:30:00.123Z",
    "serviceName": "reservation-service",
    "severity": "INFO",
    "message": "Reservation created with ID: 42",
    "logger": "nl.invokedynamic.demo.reservation.service.ReservationService",
    "thread": "virtual-12",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "spanId": "00f067aa0ba902b7",
    "attributes": {
      "customer.id": "1",
      "restaurant.id": "1"
    }
  }
  ```

### Trace Index (`otel-traces`)
- Document structure:
  ```json
  {
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "spanId": "00f067aa0ba902b7",
    "parentSpanId": "5fb397be34d23b0f",
    "name": "POST /api/v1/reservations",
    "serviceName": "reservation-service",
    "durationInNanos": 62000000,
    "statusCode": "OK",
    "attributes": {
      "http.method": "POST",
      "http.status_code": 201
    }
  }
  ```
