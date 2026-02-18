#!/usr/bin/env bash
set -euo pipefail

# Redis 대기열/active 메트릭 관측 스크립트.
#
# 사용 예시:
#   ./scripts/watch_queue_metrics.sh 3 4
#   ./scripts/watch_queue_metrics.sh 3 4 1 concert-redis
#
# 출력 컬럼:
# timestamp, queue_size, active, heartbeat_count

CONCERT_ID="${1:?concertId 필요}"
SCHEDULE_ID="${2:?scheduleId 필요}"
INTERVAL="${3:-1}"
REDIS_CONTAINER="${4:-concert-redis}"

QUEUE_KEY="queue:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID}"
ACTIVE_KEY="seat:active:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID}"
HEARTBEAT_PATTERN="queue:heartbeat:concert:${CONCERT_ID}:schedule:${SCHEDULE_ID}:user:*"

echo "timestamp,queue_size,active,heartbeat_count"

while true; do
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  queue_size="$(docker exec "${REDIS_CONTAINER}" redis-cli ZCARD "${QUEUE_KEY}" | tr -d '\r')"
  active="$(docker exec "${REDIS_CONTAINER}" redis-cli GET "${ACTIVE_KEY}" | tr -d '\r')"
  heartbeat_count="$(docker exec "${REDIS_CONTAINER}" redis-cli --scan --pattern "${HEARTBEAT_PATTERN}" | wc -l | tr -d ' ')"

  if [[ -z "${active}" ]]; then
    active="0"
  fi

  echo "${ts},${queue_size},${active},${heartbeat_count}"
  sleep "${INTERVAL}"
done

