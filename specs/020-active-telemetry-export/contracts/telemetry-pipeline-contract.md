# Telemetry Contract: Active OTLP Telemetry Pipeline

**Feature**: `020-active-telemetry-export`  
**Date**: 2026-09-18  

---

## 1. OTLP HTTP Export Endpoints & Timeouts

All 8 platform services (`gateway` and 7 microservices) communicate with the OpenTelemetry Collector via standard OTLP JSON/Protobuf over HTTP:

- **Logs Exporter Endpoint**:
  - Container environment: `http://otel-collector:4318/v1/logs`
  - Local environment: `http://localhost:4318/v1/logs`
- **Traces Exporter Endpoint**:
  - Container environment: `http://otel-collector:4318/v1/traces`
  - Local environment: `http://localhost:4318/v1/traces`
- **Network Timeouts**:
  - Connect Timeout: `1000ms` (1 second)
  - Read / Export Timeout: `3000ms` (3 seconds)
  - Total asynchronous export retry / abort deadline: `5000ms`

---

## 2. Ingestion & Storage Contract in OpenSearch

1. **Logs Pipeline**:
   - Receiver: `otlp` (ports 4317 gRPC, 4318 HTTP)
   - Processor: `batch`
   - Exporter: `opensearch`
   - Target Index: `otel-logs`
   - Dashboards Index Pattern: `otel-logs*`
   - Primary Timestamp: `timestamp`

2. **Traces Pipeline**:
   - Receiver: `otlp` (ports 4317 gRPC, 4318 HTTP)
   - Processor: `batch`
   - Exporter: `opensearch` (SS4O Data Stream)
   - Target Index / Data Stream: `ss4o_traces-default-namespace`
   - Dashboards Index Pattern: `ss4o_traces-*`
   - Primary Timestamp: `startTime`

---

## 3. Reliability, Resilience & Failure Contract

- **Buffer Capacity & Memory Limits**: Maximum 2048 records per batch queue (logs queue: 2048, traces queue: 2048). Total heap allocation per telemetry queue is strictly bounded to `< 32MB`.
- **Non-Blocking Enqueue**: In-memory event dispatch must complete in `< 5ms`. Application request threads are never blocked by telemetry export, even when the collector is offline.
- **Drop Policy on Full**: When queue capacity (2048) is reached, new incoming records are dropped (`drop-newest` policy) and an intermittent rate-limited warning log (max once every 60 seconds) is emitted.
- **Partial Collector Failures**: The log exporter and trace exporter operate on decoupled, independent client channels. A failure or HTTP 5xx error in `/v1/logs` does not impede `/v1/traces`, and vice versa.
- **Graceful Shutdown**: On JVM shutdown or Spring context close, telemetry providers execute an asynchronous flush with a hard timeout of `5000ms` (5 seconds) before shutting down.
- **Oversized Message Truncation**: Log messages exceeding 32KB and stack traces exceeding 64KB are automatically truncated with a `[TRUNCATED]` suffix to avoid OpenSearch bulk rejection and memory bloat.
- **HTTP 4xx vs 5xx Telemetry Mapping**:
  - HTTP 4xx (Client Errors): Span `statusCode: OK` or `UNSET` with `http.response.status_code` attribute; logs recorded at `WARN` or `INFO`.
  - HTTP 5xx (Server Errors): Span `statusCode: ERROR` with exception details; logs recorded at `ERROR` level.
- **CPU Overhead Guarantee**: In-process telemetry logging and tracing overhead must remain `< 2%` baseline CPU consumption under typical loads (< 1000 req/sec).
- **Health Check Exclusions**: Synthetic health probes targeting `/actuator/health/**` are excluded from span recording and routine log export unless a degraded state occurs (HTTP status != 200 or health status != `UP`).
