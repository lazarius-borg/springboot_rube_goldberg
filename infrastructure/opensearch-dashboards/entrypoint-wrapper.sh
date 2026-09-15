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
      echo "[PROVISIONER] Initializing OpenSearch index templates and base indices..."

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/_index_template/otel-logs-template" \
        -H "Content-Type: application/json" \
        -d '{"index_patterns":["otel-logs*"],"template":{"mappings":{"properties":{"timestamp":{"type":"date"},"serviceName":{"type":"keyword"},"severity":{"type":"keyword"},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"},"message":{"type":"text"}}}}}' >/dev/null 2>&1 || true

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/_index_template/ss4o-traces-template" \
        -H "Content-Type: application/json" \
        -d '{"index_patterns":["ss4o_traces-*"],"template":{"mappings":{"properties":{"startTime":{"type":"date"},"endTime":{"type":"date"},"serviceName":{"type":"keyword"},"name":{"type":"keyword"},"durationInNanos":{"type":"long"},"statusCode":{"type":"keyword"},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"}}}}}' >/dev/null 2>&1 || true

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/otel-logs" \
        -H "Content-Type: application/json" \
        -d '{"mappings":{"properties":{"timestamp":{"type":"date"},"serviceName":{"type":"keyword"},"severity":{"type":"keyword"},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"},"message":{"type":"text"}}}}' >/dev/null 2>&1 || true

      /usr/bin/curl -s -X PUT "$OPENSEARCH_URL/ss4o_traces-default-namespace" \
        -H "Content-Type: application/json" \
        -d '{"mappings":{"properties":{"startTime":{"type":"date"},"endTime":{"type":"date"},"serviceName":{"type":"keyword"},"name":{"type":"keyword"},"durationInNanos":{"type":"long"},"statusCode":{"type":"keyword"},"traceId":{"type":"keyword"},"spanId":{"type":"keyword"}}}}' >/dev/null 2>&1 || true

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
