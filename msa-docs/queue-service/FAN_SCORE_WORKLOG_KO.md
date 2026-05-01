# Fan Score 작업 로그

작성일: `2026-05-01`

## 목적

- `queue-service`의 대기열 우선순위에 적용할 `Fan Score` 로직의 설계 결정을 기록한다.
- 구현 전에 서비스 책임, 통신 방식, 데이터 반영 시점을 고정한다.
- 이후 다른 프롬프트 창이나 발표 준비 시에도 현재 설계 기준을 빠르게 복기할 수 있도록 한다.

## 현재 결정 사항

- `queue-service`는 `Fan Score`를 직접 계산하지 않고 조회만 한다.
- `queue-service`는 `Fan Score` 데이터를 DB로 직접 읽지 않는다.
- `queue-service`는 `user-auth-service`의 `internal API`를 동기 `HTTP`로 호출해 `Fan Score`를 조회한다.
- `concert-service`는 기존처럼 `concertId -> artistId` 조회를 제공한다.
- `ticketing-service`는 공연 종료 후 `Fan Score` 반영 대상을 판단하고 `user-auth-service`에 점수 반영을 요청한다.
- `Kafka`는 이번 범위에서 고려하지 않는다.

## 서비스 책임 분리

### `queue-service`

- `artistId` 조회
- `userId + artistId` 기준 `Fan Score` 조회
- 조회 결과를 `boostMillis`로 변환하여 대기열 우선순위에 반영

### `concert-service`

- `concertId` 기준 `artistId` 조회용 `internal API` 제공

### `ticketing-service`

- `CONFIRMED booking + 공연 종료 + 미반영` 조건의 대상 booking 탐색
- `Fan Score` 반영 요청 트리거
- 중복 반영 방지 상태 관리

### `user-auth-service`

- `Fan Score` 저장 책임
- `Fan Score` 조회 책임
- `internal API` 제공

## 점수 반영 기준

- 반영 대상은 단순 예매가 아니라 `CONFIRMED booking`이어야 한다.
- 추가로 해당 콘서트의 종료 시각이 현재 시각보다 이전이어야 한다.
- 즉 `예매 확정 + 공연 종료`가 되어야 `Fan Score` 반영 대상이 된다.
- 기본 적립 규칙은 현재 문서 기준으로 `confirmed attendance-equivalent booking 1건 = 1000점`을 기본값으로 본다.
- 대기열 혜택 상한은 기존 기준을 유지하여 최대 `5000ms`까지 적용하는 방향을 기본안으로 둔다.

## 중복 반영 방지

- 같은 `booking`으로 `Fan Score`가 두 번 이상 올라가면 안 된다.
- 중복 방지 책임은 `ticketing-service`가 가진다.
- 가장 단순한 기본안은 `bookings`에 `fan_score_applied_at` 컬럼을 추가하는 방식이다.
- 스케줄러는 `fan_score_applied_at IS NULL` 조건인 booking만 처리한다.
- `user-auth-service` 반영 성공 후 `fan_score_applied_at`을 기록한다.

## 통신 방식

- `queue-service -> concert-service`: 동기 `HTTP`
- `queue-service -> user-auth-service`: 동기 `HTTP`
- `ticketing-service -> user-auth-service`: 동기 `HTTP`
- 이벤트 브로커나 `Kafka` 기반 비동기 처리 방식은 이번 범위에서 제외한다.

## 예상 `internal API`

### `concert-service`

- `GET /internal/concerts/{concertId}/artist-id`

### `user-auth-service`

- `GET /internal/fan-scores/users/{userId}/artists/{artistId}`
- `POST /internal/fan-scores/events/attendance-confirmed`

## 작업 체크리스트

- [x] `queue-service` 리팩토링 완료 후 `Fan Score` 작업 시작 기준선 확보
- [x] `Fan Score` 조회는 동기 `HTTP`로 처리하기로 결정
- [x] `Kafka`는 이번 범위에서 제외
- [x] `user-auth-service`를 `Fan Score` 저장/조회 책임 서비스로 결정
- [x] `ticketing-service`를 `Fan Score` 반영 트리거 서비스로 결정
- [x] `CONFIRMED booking + 공연 종료` 조건을 반영 기준으로 정리
- [x] 중복 반영 방지 필요성 및 `fan_score_applied_at` 기본안 정리
- [x] `user-auth-service` `internal API` request/response 스펙 확정
- [x] `ticketing-service` 스케줄러 설계 및 대상 조회 쿼리 확정
- [x] `bookings.fan_score_applied_at` 반영 방식 확정
- [x] `queue-service` `QueuePriorityPolicy` 실제 구현
- [x] 서비스별 단위 테스트 작성 및 실행
- [ ] Docker / Kubernetes 환경 변수 및 서비스 주소 반영
- [ ] 로컬 다중 서비스 시나리오 검증 스크립트 정리

## 작업 로그

- 2026-05-01: Decided that `queue-service` should read `Fan Score` via synchronous `HTTP` instead of direct DB access.
- 2026-05-01: Decided that `user-auth-service` owns `Fan Score` storage/read responsibilities and `ticketing-service` owns update triggering.
- 2026-05-01: Confirmed that `Kafka` is out of scope for this feature and that service-to-service `internal API` calls are sufficient.
- 2026-05-01: Defined the initial update condition as `CONFIRMED booking + concert ended`.
- 2026-05-01: Defined duplicate-prevention responsibility at `ticketing-service` with `booking`-level processed-state tracking.
- 2026-05-02: Added `user-auth-service` internal endpoints for `Fan Score` read/apply and moved score accumulation logic into `FanScoreService`.
- 2026-05-02: Added `ticketing-service` scheduler/startup sync flow for `CONFIRMED + concert ended + not applied` bookings.
- 2026-05-02: Added `queue-service` `QueuePriorityPolicy` lookup flow via `concert-service` and `user-auth-service` internal APIs.
- 2026-05-02: Verified core unit tests for `user-auth-service`, `ticketing-service`, and `queue-service`.
