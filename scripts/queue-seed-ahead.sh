#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <ACCESS_TOKEN> <CONCERT_CODE_OR_ID> <SCHEDULE_ID> [COUNT] [QUEUE_BASE_URL]"
  echo "Example: $0 eyJ... 3 4 150 http://localhost:10010"
  exit 1
fi

TOKEN="$1"
CONCERT_CODE="$2"
SCHEDULE_ID="$3"
COUNT="${4:-150}"
QUEUE_BASE_URL="${5:-http://localhost:10010}"

echo "[1/3] start queue"
curl -sS -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  "${QUEUE_BASE_URL}/api/ticketing/start?concertCode=${CONCERT_CODE}&scheduleId=${SCHEDULE_ID}"
echo

echo "[2/3] seed fake users ahead: ${COUNT}"
curl -sS -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  "${QUEUE_BASE_URL}/api/ticketing/dev/seed-ahead?concertCode=${CONCERT_CODE}&scheduleId=${SCHEDULE_ID}&count=${COUNT}"
echo

echo "[3/3] current queue status"
curl -sS \
  -H "Authorization: Bearer ${TOKEN}" \
  "${QUEUE_BASE_URL}/api/ticketing/status?concertCode=${CONCERT_CODE}&scheduleId=${SCHEDULE_ID}"
echo
