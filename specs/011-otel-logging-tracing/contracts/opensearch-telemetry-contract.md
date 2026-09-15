# Interface Contract: OpenSearch Telemetry Queries

**Feature**: `011-otel-logging-tracing`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](../spec.md)

---

## 1. Overview

OpenSearch exposes REST endpoints at port `9200` and OpenSearch Dashboards at port `5601`. This contract defines standard query templates for verifying and querying logs and traces.

---

## 2. Querying Structured Logs (`otel-logs`)

### Search by Service Name
```http
POST /otel-logs/_search
Content-Type: application/json

{
  "query": {
    "match": {
      "serviceName": "reservation-service"
    }
  },
  "sort": [
    { "timestamp": { "order": "desc" } }
  ],
  "size": 50
}
```

### Correlate Logs by Trace ID
Retrieves all logs produced across the entire microservice ecosystem for a single transaction:

```http
POST /otel-logs/_search
Content-Type: application/json

{
  "query": {
    "term": {
      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
    }
  },
  "sort": [
    { "timestamp": { "order": "asc" } }
  ]
}
```

---

## 3. Querying Distributed Traces (`ss4o_traces-*`)

### Retrieve Trace Spans by Trace ID
```http
POST /ss4o_traces-*/_search
Content-Type: application/json

{
  "query": {
    "term": {
      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
    }
  },
  "sort": [
    { "startTime": { "order": "asc" } }
  ]
}
```

### Filter Errors across Services
```http
POST /ss4o_traces-*/_search
Content-Type: application/json

{
  "query": {
    "match": {
      "statusCode": "ERROR"
    }
  },
  "sort": [
    { "startTime": { "order": "desc" } }
  ]
}
```
