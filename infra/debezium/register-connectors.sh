#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${KAFKA_CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

wait_for_connect() {
  echo "Waiting for Kafka Connect at $CONNECT_URL ..."
  until curl -sf "$CONNECT_URL/connectors" > /dev/null 2>&1; do
    sleep 3
  done
  echo "Kafka Connect is ready."
}

register_connector() {
  local name="$1"
  local file="$2"

  existing=$(curl -sf "$CONNECT_URL/connectors/$name" 2>/dev/null || true)
  if [ -n "$existing" ]; then
    echo "Connector '$name' already exists, updating config..."
    curl -sf -X PUT "$CONNECT_URL/connectors/$name/config" \
      -H "Content-Type: application/json" \
      -d "$(jq '.config' "$file")"
  else
    echo "Registering connector '$name'..."
    curl -sf -X POST "$CONNECT_URL/connectors" \
      -H "Content-Type: application/json" \
      -d @"$file"
  fi
  echo ""
}

wait_for_connect
register_connector "payment-outbox-connector"   "$SCRIPT_DIR/payment-outbox-connector.json"
register_connector "ticketing-outbox-connector" "$SCRIPT_DIR/ticketing-outbox-connector.json"

echo "All connectors registered."
curl -s "$CONNECT_URL/connectors" | jq .
