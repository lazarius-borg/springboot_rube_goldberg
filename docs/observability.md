# Platform Observability Architecture: Active Telemetry Export

## Overview

The platform uses active OpenTelemetry telemetry export across the API Gateway and all 7 backend microservices:
- `gateway` (Port 8080)
- `customer-service` (Port 8082)
- `restaurant-service` (Port 8083)
- `availability-service` (Port 8084)
- `reservation-service` (Port 8085)
- `waiting-list-service` (Port 8086)
- `analytics-service` (Port 8087)
- `notification-service` (Port 8088)

Telemetry is transmitted via standard OTLP HTTP protocols directly to the OpenTelemetry Collector container:
- **Logs Endpoint**: `http://otel-collector:4318/v1/logs`
- **Traces Endpoint**: `http://otel-collector:4318/v1/traces`

---

## Pipeline Architecture

```
[Microservices & Gateway]
   │
   ├──> OpenTelemetryLogbackAppender (ILoggingEvent bridge)
   │       │
   │       └──> OtlpHttpLogRecordExporter ──[OTLP HTTP :4318/v1/logs]──┐
   │                                                                   │
   └──> BatchSpanProcessor (HealthProbeSpanProcessor filtered)         │
           │                                                           ▼
           └──> OtlpHttpSpanExporter ─────[OTLP HTTP :4318/v1/traces]───> [OTel Collector :4318]
                                                                           │
                                                                           ├── Batch Processor
                                                                           │
                                                                           ├── OpenSearch Exporter
                                                                           │     │
                                                                           │     ├──> otel-logs
                                                                           │     └──> ss4o_traces-*
                                                                           ▼
                                                             [OpenSearch & OpenSearch Dashboards :5601]
```

---

## Resilience and Buffering

1. **In-Memory Batch Queue**:
   - `maxQueueSize`: 2048 records
   - `maxExportBatchSize`: 512 records
   - `scheduleDelay`: 1000ms
   - `exporterTimeout`: 3000ms
   - `connectTimeout`: 1000ms

2. **Non-Blocking Drop Policy**:
   - When collector is offline or slow, memory allocation is strictly bounded (<32MB per service).
   - Overflow records are dropped non-blockingly without thread starvation or impact to client transactions.

3. **Routine Health Probe Suppression**:
   - `HealthProbeSpanProcessor` suppresses routine synthetic probes (`/actuator/health/**`) from trace export unless the status is `ERROR`.
   - `OpenTelemetryLogbackAppender` suppresses routine health probe log lines below `WARN` level.

4. **Dual Output Preservation**:
   - Standard console logging (`ConsoleAppender` to `STDOUT`) remains completely functional alongside remote OTLP export.

---

## Dashboards & Index Patterns

OpenSearch Dashboards provisions indices and dashboards at startup via `infrastructure/opensearch-dashboards/entrypoint-wrapper.sh`:
- **Application Logs Dashboard**: `http://localhost:5601/app/dashboards#/view/application-logs-dashboard`
- **Distributed Traces Dashboard**: `http://localhost:5601/app/dashboards#/view/distributed-traces-dashboard`
- **Platform Observability Overview**: `http://localhost:5601/app/dashboards#/view/platform-observability-overview`
