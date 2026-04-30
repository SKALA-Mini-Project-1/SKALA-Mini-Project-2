#!/usr/bin/env bash
set -euo pipefail

# Usage:
# BASE_URL=http://localhost:18085 \
# ORDER_ID=PAY_xxx PAYMENT_KEY=payment_key_xxx \
# ./payment-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/paper/webhook-idempotency-check.sh

BASE_URL="${BASE_URL:-http://localhost:18085}"
ORDER_ID="${ORDER_ID:-}"
PAYMENT_KEY="${PAYMENT_KEY:-}"
TOTAL_AMOUNT="${TOTAL_AMOUNT:-1000}"

if [[ -z "$ORDER_ID" || -z "$PAYMENT_KEY" ]]; then
  echo "ORDER_ID and PAYMENT_KEY are required"
  exit 1
fi

PAYLOAD=$(cat <<JSON
{"orderId":"$ORDER_ID","paymentKey":"$PAYMENT_KEY","status":"DONE","totalAmount":$TOTAL_AMOUNT}
JSON
)

echo "[1/3] first DONE webhook"
curl -i -sS -X POST "$BASE_URL/api/payments/toss/webhook" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD"
echo

echo "[2/3] second DONE webhook (idempotency)"
curl -i -sS -X POST "$BASE_URL/api/payments/toss/webhook" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD"
echo

echo "[3/3] third DONE webhook (idempotency)"
curl -i -sS -X POST "$BASE_URL/api/payments/toss/webhook" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD"
echo
