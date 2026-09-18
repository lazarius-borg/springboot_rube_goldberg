#!/bin/sh
set -e

echo "Starting OpenSearch Dashboards with automated provisioning wrapper..."

# Launch the default OpenSearch Dashboards entrypoint in the background
./opensearch-dashboards-docker-entrypoint.sh opensearch-dashboards &
MAIN_PID=$!

# Trap termination signals and forward to the primary process
trap 'kill -TERM $MAIN_PID 2>/dev/null' TERM INT

# Background provisioning subshell
(
  PROVISIONING_DIR="/usr/share/opensearch-dashboards/provisioning"
  MANIFEST="$PROVISIONING_DIR/dashboards.ndjson"
  STATUS_URL="http://localhost:5601/api/status"
  IMPORT_URL="http://localhost:5601/api/saved_objects/_import?overwrite=true"
  MAX_ATTEMPTS=30
  SLEEP_INTERVAL=2

  if [ ! -f "$MANIFEST" ]; then
    echo "[PROVISIONER] Error: Manifest $MANIFEST not found. Provisioning skipped." >&2
    exit 1
  fi

  echo "[PROVISIONER] Waiting for OpenSearch Dashboards to become healthy..."

  for i in $(seq 1 $MAX_ATTEMPTS); do
    # Probe Dashboards status endpoint using built-in curl
    RESPONSE=$(/usr/bin/curl -s "$STATUS_URL" 2>/dev/null || true)
    STATE=$(echo "$RESPONSE" | grep -o '"state":"[^"]*"' | head -n 1 | cut -d'"' -f4 || true)

    if [ "$STATE" = "green" ] || [ "$STATE" = "yellow" ]; then
      echo "[PROVISIONER] OpenSearch Dashboards is ready (state: $STATE, attempt $i/$MAX_ATTEMPTS)."

      # Ensure base index templates and physical indices exist in OpenSearch
      # so Dashboards index patterns match immediately with zero errors
      OPENSEARCH_URL="http://opensearch:9200"
      echo "[PROVISIONER] Initializing OpenSearch ingest pipelines, index templates, and base indices..."

      # Ingest pipeline for otel-logs (populates message from body, serviceName, traceId, spanId)
      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/_ingest/pipeline/otel-logs-pipeline" \
        -H "Content-Type: application/json" \
        -d '{"description":"Populate dashboard fields for otel-logs","processors":[{"set":{"field":"message","value":"{{body}}","if":"ctx.message == null && ctx.body != null"}},{"set":{"field":"serviceName","value":"{{attributes.serviceName}}","if":"ctx.serviceName == null && ctx.attributes != null && ctx.attributes.serviceName != null"}},{"set":{"field":"serviceName","value":"{{resource.service.name}}","if":"ctx.serviceName == null && ctx.resource != null && ctx.resource[\"service.name\"] != null"}},{"set":{"field":"traceId","value":"{{attributes.traceId}}","if":"ctx.traceId == null && ctx.attributes != null && ctx.attributes.traceId != null"}},{"set":{"field":"traceId","value":"{{trace_id}}","if":"ctx.traceId == null && ctx.trace_id != null"}},{"set":{"field":"spanId","value":"{{attributes.spanId}}","if":"ctx.spanId == null && ctx.attributes != null && ctx.attributes.spanId != null"}},{"set":{"field":"spanId","value":"{{span_id}}","if":"ctx.spanId == null && ctx.span_id != null"}}]}' >/dev/null 2>&1 || true

      # Ingest pipeline for ss4o_traces (calculates durationInNanos, extracts serviceName, computes statusCode)
      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/_ingest/pipeline/ss4o-traces-pipeline" \
        -H "Content-Type: application/json" \
        -d '{"description":"Populate dashboard fields for ss4o_traces","processors":[{"script":{"lang":"painless","source":"if (ctx.startTime != null && ctx.endTime != null && ctx.durationInNanos == null) { try { java.time.Instant start = java.time.Instant.parse(ctx.startTime); java.time.Instant end = java.time.Instant.parse(ctx.endTime); ctx.durationInNanos = java.time.Duration.between(start, end).toNanos(); } catch (Exception e) {} } if (ctx.serviceName == null && ctx.resource != null && ctx.resource[\"service.name\"] != null) { ctx.serviceName = ctx.resource[\"service.name\"]; } if (ctx.statusCode == null) { if (ctx.status != null && ctx.status.code != null && !ctx.status.code.isEmpty() && !ctx.status.code.equals(\"Unset\")) { ctx.statusCode = ctx.status.code; } else if (ctx.attributes != null && ctx.attributes[\"http.status_code\"] != null) { int code = Integer.parseInt(ctx.attributes[\"http.status_code\"].toString()); ctx.statusCode = code >= 400 ? \"ERROR\" : \"OK\"; } else { ctx.statusCode = \"OK\"; } }"}}]}' >/dev/null 2>&1 || true

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/_index_template/otel-logs-template" \
        -H "Content-Type: application/json" \
        -d '{"index_patterns":["otel-logs*"],"template":{"settings":{"index.default_pipeline":"otel-logs-pipeline"},"mappings":{"properties":{"@timestamp":{"type":"date"},"timestamp":{"type":"alias","path":"@timestamp"},"body":{"type":"text"},"message":{"type":"text"},"serviceName":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"},"statusCode":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"severity":{"properties":{"number":{"type":"long"},"text":{"type":"text","fields":{"keyword":{"type":"keyword","ignore_above":256}}},"keyword":{"type":"alias","path":"severity.text.keyword"}}},"attributes":{"properties":{"serviceName":{"type":"text","fields":{"keyword":{"type":"keyword","ignore_above":256}}},"logger":{"type":"keyword"},"thread":{"type":"keyword"},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"}}}}}}}' >/dev/null 2>&1 || true

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/_index_template/ss4o-traces-template" \
        -H "Content-Type: application/json" \
        -d '{"index_patterns":["ss4o_traces-*"],"template":{"settings":{"index.default_pipeline":"ss4o-traces-pipeline"},"mappings":{"properties":{"startTime":{"type":"date"},"endTime":{"type":"date"},"serviceName":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"name":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"durationInNanos":{"type":"long"},"statusCode":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"}}}}}' >/dev/null 2>&1 || true

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/otel-logs" \
        -H "Content-Type: application/json" \
        -d '{"settings":{"index.default_pipeline":"otel-logs-pipeline"},"mappings":{"properties":{"@timestamp":{"type":"date"},"timestamp":{"type":"alias","path":"@timestamp"},"body":{"type":"text"},"message":{"type":"text"},"serviceName":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"},"statusCode":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"severity":{"properties":{"number":{"type":"long"},"text":{"type":"text","fields":{"keyword":{"type":"keyword","ignore_above":256}}},"keyword":{"type":"alias","path":"severity.text.keyword"}}},"attributes":{"properties":{"serviceName":{"type":"text","fields":{"keyword":{"type":"keyword","ignore_above":256}}},"logger":{"type":"keyword"},"thread":{"type":"keyword"},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"}}}}}}' >/dev/null 2>&1 || true

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/ss4o_traces-default-namespace" \
        -H "Content-Type: application/json" \
        -d '{"settings":{"index.default_pipeline":"ss4o-traces-pipeline"},"mappings":{"properties":{"startTime":{"type":"date"},"endTime":{"type":"date"},"serviceName":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"name":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"durationInNanos":{"type":"long"},"statusCode":{"type":"keyword","fields":{"keyword":{"type":"keyword"}}},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"}}}}' >/dev/null 2>&1 || true

      echo "[PROVISIONER] Importing saved objects into OpenSearch Dashboards..."
      IMPORT_OUTPUT=$(/usr/bin/curl -s -X POST "$IMPORT_URL" \
        -H "osd-xsrf: true" \
        --form "file=@$MANIFEST" 2>&1 || true)

      # Validate import response
      SUCCESS=$(echo "$IMPORT_OUTPUT" | grep -o '"success":true' || true)
      HAS_ERRORS=$(echo "$IMPORT_OUTPUT" | grep -o '"errors":\[[^]]' || true)

      if [ -n "$SUCCESS" ] && [ -z "$HAS_ERRORS" ]; then
        echo "[PROVISIONER] Import successful: $IMPORT_OUTPUT"
        echo "[PROVISIONER] OpenSearch Dashboards provisioning complete."
      else
        echo "[PROVISIONER] Warning: Import reported errors or unexpected response:" >&2
        echo "$IMPORT_OUTPUT" >&2
      fi
      exit 0
    fi

    echo "[PROVISIONER] Attempt $i/$MAX_ATTEMPTS: status is '${STATE:-connecting}' - retrying in ${SLEEP_INTERVAL}s..."
    sleep $SLEEP_INTERVAL
  done

  echo "[PROVISIONER] Timeout reached ($((MAX_ATTEMPTS * SLEEP_INTERVAL))s). OpenSearch Dashboards did not become healthy in time." >&2
  exit 1
) &

# Supervise the primary OpenSearch Dashboards process
wait $MAIN_PID
