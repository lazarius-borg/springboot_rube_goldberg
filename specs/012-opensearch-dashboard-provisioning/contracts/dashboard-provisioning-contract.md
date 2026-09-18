# Interface Contract: OpenSearch Dashboards Provisioning

**Feature**: `012-opensearch-dashboard-provisioning`  
**Date**: 2026-09-15  
**Spec Reference**: [spec.md](../spec.md)

---

## 1. OpenSearch Dashboards Status API Contract

Used by the internal entrypoint wrapper script to poll for readiness before attempting saved objects import.

### Request
```http
GET /api/status
Host: localhost:5601
Accept: application/json
```

### Response (200 OK)
```json
{
  "name": "rube-opensearch-dashboards",
  "version": {
    "number": "2.19.0"
  },
  "status": {
    "overall": {
      "state": "green",
      "title": "Green",
      "nickname": "Looking good"
    }
  }
}
```

- **Readiness Condition**: HTTP status code `200` and `.status.overall.state` in `["green", "yellow"]`.

---

## 2. Saved Objects Import API Contract

Used to idempotently import index patterns, visualizations, searches, dashboards, and UI configurations.

### Request
```http
POST /api/saved_objects/_import?overwrite=true
Host: localhost:5601
osd-xsrf: true
Content-Type: multipart/form-data; boundary=------------------------boundary

--------------------------boundary
Content-Disposition: form-data; name="file"; filename="dashboards.ndjson"
Content-Type: application/ndjson

<binary ndjson payload>
--------------------------boundary--
```

### Parameters
| Query Parameter | Value | Purpose |
|---|---|---|
| `overwrite` | `true` | Ensures idempotency by updating existing objects rather than throwing duplicate key errors. |

### Headers
| Header | Value | Purpose |
|---|---|---|
| `osd-xsrf` | `true` | Required cross-site request forgery prevention header for OpenSearch Dashboards APIs. |

### Response (200 OK)
```json
{
  "success": true,
  "successCount": 11,
  "errors": []
}
```

- **Success Criterion**: `.success == true` and `.errors` array is empty or zero-length.

---

## 3. Container Wrapper & Volume Mount Contract (`docker-compose.yml` & `entrypoint-wrapper.sh`)

Rather than maintaining a separate ephemeral container, provisioning is embedded directly into the `opensearch-dashboards` service definition via volume mount and entrypoint wrapper:

### Service Configuration in `infrastructure/docker-compose.yml`:
```yaml
  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:2.19.0
    container_name: rube-opensearch-dashboards
    environment:
      - "OPENSEARCH_HOSTS=[\"http://opensearch:9200\"]"
      - "DISABLE_SECURITY_DASHBOARDS_PLUGIN=true"
    ports:
      - "5601:5601"
    volumes:
      - ./opensearch-dashboards:/usr/share/opensearch-dashboards/provisioning:ro
    entrypoint: ["/bin/sh", "/usr/share/opensearch-dashboards/provisioning/entrypoint-wrapper.sh"]
    depends_on:
      - opensearch
```

### Entrypoint Wrapper Contract (`entrypoint-wrapper.sh`):
1. **Primary Launch**: Executes `./opensearch-dashboards-docker-entrypoint.sh opensearch-dashboards &` and captures the primary process PID.
2. **Background Probe**: In a concurrent subshell, polls `http://localhost:5601/api/status` using the container's built-in `/usr/bin/curl` every 2 seconds (up to 30 attempts).
3. **Execution**: Once readiness is established (`state: green|yellow`), sends `POST /api/saved_objects/_import?overwrite=true` with `/usr/share/opensearch-dashboards/provisioning/dashboards.ndjson`.
4. **Signal & Process Management**: Traps `SIGTERM` / `SIGINT` to cleanly forward stop signals to the Dashboards PID, and executes `wait $PID` to supervise the primary container lifecycle.
