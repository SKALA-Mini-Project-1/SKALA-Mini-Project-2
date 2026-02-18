#!/usr/bin/env bash
set -euo pipefail

# 대기열 부하/우선순위 테스트용 사용자 대량 생성 스크립트.
# 생성 규칙:
# - email: load_user_000001@test.local 형태
# - password: 동일 비밀번호(기본값: password123)
# - fan_score: 짝수 0, 홀수 100 (우선순위 비교용)
#
# 사용 예시:
#   ./scripts/seed_test_users.sh 2000
#   ./scripts/seed_test_users.sh 5000 1 password123 concert-postgres
#
# 출력:
# - scripts/test_users.csv (email,password,fan_score)

COUNT="${1:-1000}"
START_INDEX="${2:-1}"
PLAIN_PASSWORD="${3:-password123}"
PG_CONTAINER="${4:-concert-postgres}"
DB_USER="${DB_USER:-skala}"
DB_NAME="${DB_NAME:-concert}"
OUT_CSV="${5:-scripts/test_users.csv}"

if ! command -v htpasswd >/dev/null 2>&1; then
  echo "[ERROR] htpasswd 명령이 필요합니다(apache2-utils/httpd-tools)." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker 명령을 찾을 수 없습니다." >&2
  exit 1
fi

END_INDEX=$((START_INDEX + COUNT - 1))
HASH="$(htpasswd -nbBC 10 test "${PLAIN_PASSWORD}" | cut -d: -f2)"
ESC_HASH="${HASH//\'/\'\'}"

echo "[INFO] users ${COUNT}명 생성/보정 중 (index ${START_INDEX}-${END_INDEX})..."

docker exec "${PG_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -c "
INSERT INTO users (email, password, name, fan_score)
SELECT
  format('load_user_%s@test.local', lpad(gs::text, 6, '0')),
  '${ESC_HASH}',
  format('Load User %s', gs),
  CASE WHEN (gs % 2) = 0 THEN 0 ELSE 100 END
FROM generate_series(${START_INDEX}, ${END_INDEX}) AS gs
ON CONFLICT (email) DO NOTHING;
"

echo "[INFO] CSV 파일 생성: ${OUT_CSV}"
mkdir -p "$(dirname "${OUT_CSV}")"

docker exec "${PG_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -A -F "," -t -c "
SELECT 'email', 'password', 'fan_score'
UNION ALL
SELECT
  format('load_user_%s@test.local', lpad(gs::text, 6, '0')),
  '${PLAIN_PASSWORD}',
  CASE WHEN (gs % 2) = 0 THEN 0 ELSE 100 END::text
FROM generate_series(${START_INDEX}, ${END_INDEX}) AS gs
" > "${OUT_CSV}"

echo "[DONE] 생성 완료"
echo " - users: ${COUNT}명"
echo " - csv: ${OUT_CSV}"
echo " - fan_score: 0/100 교차 분포"
