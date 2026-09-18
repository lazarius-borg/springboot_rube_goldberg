# Quickstart: Verification Guide for Active Telemetry Export

**Feature**: `020-active-telemetry-export`  
**Date**: 2026-09-18  

This guide provides step-by-step instructions to verify that application logs and distributed traces are actively exported over OTLP and visible in OpenSearch Dashboards.

---

## 1. Prerequisites

Ensure all Docker Compose infrastructure and services are running:
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml ps
```

---

## 2. Generate Traffic Through Gateway

Send sample requests through the API Gateway to trigger business operations across microservices:

```bash
# 1. Query restaurants (restaurant-service)
curl -s http://localhost:8080/api/v1/restaurants | jq .

# 2. Query availability (availability-service)
curl -s http://localhost:8080/api/v1/availability/1/summary | jq .
```

---

## 3. Verify Log & Trace Indices in OpenSearch

Verify that documents are actively indexed into `otel-logs` and `ss4o_traces-*`:

```bash
# Check index document counts (docs.count must be > 0):
curl -s "http://localhost:9200/_cat/indices?v" | grep -E "otel-logs|ss4o_traces"
```

Verify sample documents in OpenSearch:
```bash
# Verify logs
curl -s "http://localhost:9200/otel-logs/_search?pretty&size=1"

# Verify traces
curl -s "http://localhost:9200/ss4o_traces-default-namespace/_search?pretty&size=1"
```

---

## 4. OpenSearch Dashboards Visual Inspection

Open OpenSearch Dashboards in your browser:
- **Application Logs Dashboard**: `http://localhost:5601/app/dashboards#/view/application-logs-dashboard`
  - Verify log volume chart is populated.
  - Verify severity pie chart displays log breakdown.
  - Verify log stream table shows service names, timestamps, and messages.
- **Distributed Traces Dashboard**: `http://localhost:5601/app/dashboards#/view/distributed-traces-dashboard`
  - Verify span throughput and duration charts are populated.
- **Platform Observability Overview**: `http://localhost:5601/app/dashboards#/view/platform-observability-overview`
  - Verify high-level health metrics and logs/traces overview.
