# Fan Score 검증 가이드

작성일: `2026-05-02`

## 목적

- `Fan Score` 기능이 설계대로 자동 반영되고 `queue-service` 우선순위에 연결되는지 검증한다.
- 로컬 실행 또는 `docker-compose` 실행 기준에서 동일한 흐름으로 확인할 수 있게 한다.

## 검증 대상

- `ticketing-service`가 `CONFIRMED + concert ended + not applied` booking만 대상으로 잡는지
- `ticketing-service`가 `user-auth-service`에 `Fan Score` 반영 요청을 보내는지
- `user-auth-service`가 `user_artist_fan_scores`를 갱신하는지
- `queue-service`가 `concert-service`, `user-auth-service`를 호출해 `boostMillis`를 계산하는지
- downstream 장애 시 `queue-service`가 중립 우선순위(`0`)로 fallback 하는지

## 사전 조건

- `docker-compose up` 또는 로컬 서비스 실행이 가능한 상태
- `postgres`, `redis`, `user-auth-service`, `concert-service`, `ticketing-service`, `queue-service` 기동 완료
- 내부 토큰과 base URL 환경변수가 현재 코드 기준으로 맞춰져 있어야 함

## 필수 환경변수

### `user-auth-service`

- `USER_AUTH_INTERNAL_API_TOKEN`

### `concert-service`

- `CONCERT_INTERNAL_API_TOKEN`

### `ticketing-service`

- `USER_AUTH_SERVICE_BASE_URL`
- `CONCERT_SERVICE_BASE_URL`
- `USER_AUTH_INTERNAL_API_TOKEN`
- `CONCERT_INTERNAL_API_TOKEN`
- `TICKETING_FAN_SCORE_SYNC_FIXED_DELAY_MS`

### `queue-service`

- `USER_AUTH_SERVICE_BASE_URL`
- `CONCERT_SERVICE_BASE_URL`
- `USER_AUTH_INTERNAL_API_TOKEN`
- `CONCERT_INTERNAL_API_TOKEN`

## 검증 시나리오

### 1. Fan Score 반영 전 상태 확인

- 대상 사용자와 아티스트 조합의 `user_artist_fan_scores` 값을 확인한다.
- 대상 `booking`의 `status`, `confirmed_at`, `fan_score_applied_at` 값을 확인한다.

기대 결과:

- `booking.status = CONFIRMED`
- `confirmed_at` 존재
- `fan_score_applied_at = NULL`
- 초기 `Fan Score`는 반영 전 상태

### 2. 공연 종료 조건 확인

- 대상 `booking`이 연결된 `schedule.end_time`이 현재 시각보다 이전인지 확인한다.

기대 결과:

- `schedule.end_time <= now`

### 3. ticketing-service 자동 반영 확인

- `FanScoreSyncScheduler`가 한 번 이상 실행되도록 기다리거나 앱 재기동 후 startup sync 로그를 확인한다.

기대 결과:

- `ticketing-service` 로그에 `Fan score sync complete` 또는 scheduler 적용 로그가 남는다.
- 같은 `booking`에 대해 `user-auth-service` 반영 요청이 한 번만 수행된다.

### 4. user-auth-service 저장 결과 확인

- `GET /internal/fan-scores/users/{userId}/artists/{artistId}` 를 호출한다.

기대 결과:

- `totalScore = 기존 점수 + 1000`
- `user_artist_fan_scores`에 해당 조합이 없던 경우 새 row 생성

### 5. ticketing-service 중복 방지 확인

- 동일한 scheduler 주기를 한 번 더 기다리거나 수동으로 sync를 재실행한다.
- 동일 `booking`의 `fan_score_applied_at` 값을 다시 확인한다.

기대 결과:

- `fan_score_applied_at` 이 이미 채워져 있음
- 점수가 다시 오르지 않음

### 6. queue-service 우선순위 반영 확인

- 동일 사용자로 `POST /api/ticketing/start?concertId=&scheduleId=` 요청을 보낸다.
- `queue-service`가 `artistId`와 `Fan Score`를 조회하도록 유도한다.

기대 결과:

- `QueuePriorityPolicy`가 `artistId` 기준 점수를 조회한다.
- `boostMillis`가 `totalScore` 기반으로 계산된다.
- 최종 가중치는 `QueuePriorityService.MAX_QUEUE_PRIORITY_BOOST_MILLIS` 상한 내에서 적용된다.

### 7. queue-service fallback 확인

- `user-auth-service` 또는 `concert-service`를 일시적으로 내리거나 잘못된 토큰으로 실행한다.
- 다시 `POST /api/ticketing/start` 요청을 보낸다.

기대 결과:

- `queue-service`는 에러로 전체 기능이 멈추지 않는다.
- `QueuePriorityPolicy`는 `0`을 반환한다.
- 대기열은 기본 우선순위로 계속 동작한다.

## 체크리스트

- [x] `user-auth-service` 단위 테스트 통과
- [x] `ticketing-service` 단위 테스트 통과
- [x] `queue-service` 단위 테스트 통과
- [ ] 로컬 다중 서비스 시나리오 검증 완료
- [ ] `docker-compose` 기준 검증 완료
- [ ] Kubernetes 환경 변수 반영 확인

## 작업 로그

- 2026-05-02: Added a validation guide for `Fan Score` automatic update and queue priority integration.
- 2026-05-02: Included both happy-path verification and downstream fallback verification steps.
