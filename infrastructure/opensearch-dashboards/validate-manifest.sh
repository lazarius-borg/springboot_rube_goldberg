#!/usr/bin/env sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MANIFEST="${1:-$SCRIPT_DIR/dashboards.ndjson}"

if [ ! -f "$MANIFEST" ]; then
  echo "Error: Manifest file not found: $MANIFEST" >&2
  exit 1
fi

echo "Validating OpenSearch Dashboards manifest: $MANIFEST"

LINE_NUM=0
VALID_TYPES="index-pattern visualization search dashboard config"

# Read manifest line by line
while IFS= read -r line || [ -n "$line" ]; do
  # Skip empty or whitespace-only lines
  trimmed=$(echo "$line" | tr -d '[:space:]')
  if [ -z "$trimmed" ]; then
    continue
  fi

  LINE_NUM=$((LINE_NUM + 1))

  # 1. Validate JSON syntax
  if ! echo "$line" | jq empty >/dev/null 2>&1; then
    echo "Error on line $LINE_NUM: Invalid JSON syntax" >&2
    exit 1
  fi

  # 2. Assert mandatory fields: id, type, attributes
  ID=$(echo "$line" | jq -r '.id // empty')
  TYPE=$(echo "$line" | jq -r '.type // empty')
  HAS_ATTRS=$(echo "$line" | jq 'has("attributes")')

  if [ -z "$ID" ]; then
    echo "Error on line $LINE_NUM: Missing 'id' field" >&2
    exit 1
  fi

  if [ -z "$TYPE" ]; then
    echo "Error on line $LINE_NUM: Missing 'type' field" >&2
    exit 1
  fi

  if [ "$HAS_ATTRS" != "true" ]; then
    echo "Error on line $LINE_NUM: Missing 'attributes' object" >&2
    exit 1
  fi

  # 3. Assert valid type
  TYPE_MATCH=0
  for vt in $VALID_TYPES; do
    if [ "$TYPE" = "$vt" ]; then
      TYPE_MATCH=1
      break
    fi
  done

  if [ "$TYPE_MATCH" -eq 0 ]; then
    echo "Error on line $LINE_NUM: Invalid type '$TYPE'. Allowed: $VALID_TYPES" >&2
    exit 1
  fi

  echo "  [OK] Line $LINE_NUM: type=$TYPE, id=$ID"
done < "$MANIFEST"

if [ "$LINE_NUM" -eq 0 ]; then
  echo "Warning: Manifest contains 0 objects."
else
  echo "Manifest validation passed successfully ($LINE_NUM objects validated)."
fi

exit 0
