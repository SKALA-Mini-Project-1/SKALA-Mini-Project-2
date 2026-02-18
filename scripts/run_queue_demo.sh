#!/usr/bin/env bash
set -euo pipefail

# 발표용: "실제 부하 + 대기열 감소" 데모 원클릭 실행 스크립트
# 사용:
#   bash scripts/run_queue_demo.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

CONCERT_ID="${CONCERT_ID:-3}"
SCHEDULE_ID="${SCHEDULE_ID:-4}"
VIEWER_EMAIL="${VIEWER_EMAIL:-load_user_003500@test.local}"
USERS_CSV="${USERS_CSV:-scripts/test_users.csv}"
BASE_URL="${BASE_URL:-http://localhost:8081}"
REDIS_CONTAINER="${REDIS_CONTAINER:-concert-redis}"

QUEUE_SIZE="${QUEUE_SIZE:-3000}"
WORKERS="${WORKERS:-30}"
JOIN_RETRIES="${JOIN_RETRIES:-4}"
SET_ACTIVE="${SET_ACTIVE:-500}"
CHURN_MIN="${CHURN_MIN:-8}"
CHURN_MAX="${CHURN_MAX:-18}"
TICK_SECONDS="${TICK_SECONDS:-2}"
DURATION_SECONDS="${DURATION_SECONDS:-300}"

echo "[1/4] 백엔드 응답 확인: ${BASE_URL}/api/concerts"
curl -fsS -m 5 "${BASE_URL}/api/concerts" >/dev/null

echo "[2/4] Redis 큐/active/access 초기화"
docker exec "${REDIS_CONTAINER}" sh -lc "
  redis-cli DEL queue:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID} queue:heartbeat:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID} seat:active:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID} >/dev/null
  redis-cli --scan --pattern 'seat:access:user:*:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID}' | xargs -r redis-cli DEL >/dev/null
"

echo "[3/4] 모니터링 시작 (/tmp/queue_metrics_${CONCERT_ID}_${SCHEDULE_ID}.csv)"
(
  echo "timestamp,queue_size,active,heartbeat_count"
  for _ in $(seq 1 $((DURATION_SECONDS / 2 + 5))); do
    ts="$(date '+%Y-%m-%d %H:%M:%S')"
    q="$(docker exec "${REDIS_CONTAINER}" redis-cli ZCARD "queue:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID}" | tr -d '\r')"
    a="$(docker exec "${REDIS_CONTAINER}" redis-cli GET "seat:active:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID}" | tr -d '\r')"
    h="$(docker exec "${REDIS_CONTAINER}" redis-cli --scan --pattern "queue:heartbeat:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID}:user:*" | wc -l | tr -d ' ')"
    [[ -z "${a}" ]] && a="0"
    echo "${ts},${q},${a},${h}"
    sleep 2
  done
) >"/tmp/queue_metrics_${CONCERT_ID}_${SCHEDULE_ID}.csv" &
MON_PID=$!

echo "[4/4] 부하 시뮬레이터 실행 (약 ${DURATION_SECONDS}초)"
python3 scripts/simulate_queue_churn.py \
  --users "${USERS_CSV}" \
  --base-url "${BASE_URL}" \
  --concert-id "${CONCERT_ID}" \
  --schedule-id "${SCHEDULE_ID}" \
  --viewer-email "${VIEWER_EMAIL}" \
  --queue-size "${QUEUE_SIZE}" \
  --workers "${WORKERS}" \
  --join-retries "${JOIN_RETRIES}" \
  --set-active "${SET_ACTIVE}" \
  --churn-min "${CHURN_MIN}" \
  --churn-max "${CHURN_MAX}" \
  --tick-seconds "${TICK_SECONDS}" \
  --duration-seconds "${DURATION_SECONDS}"

kill "${MON_PID}" >/dev/null 2>&1 || true

echo
echo "완료."
echo "- 프론트 로그인 계정: ${VIEWER_EMAIL} / password123"
echo "- 모니터링 CSV: /tmp/queue_metrics_${CONCERT_ID}_${SCHEDULE_ID}.csv"
echo "- 확인 포인트: queue_size가 큰 값으로 시작 -> 랜덤하게 감소"

