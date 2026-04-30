# MSA 서비스 역할 및 통신 정리

작성일: `2026-04-30`

## 문서 목적

- 현재 브랜치에서 서비스가 어떻게 나뉘어 있는지 빠르게 파악한다.
- 각 서비스의 역할, 공개 API, 내부 통신 관계를 한 번에 본다.
- ECR 이미지 업로드 및 Kubernetes 배포 전에 어떤 서비스가 어떤 책임을 갖는지 기준 문서로 사용한다.
- Kafka/outbox 관련 구현 상태를 코드 기준으로 확인한다.

## 현재 한 줄 요약

- 현재 코드는 `user-auth-service`, `concert-service`, `queue-service`, `ticketing-service`, `payment-service`로 멀티모듈 분리되어 있고, 각 서비스별 `Dockerfile`도 존재한다.
- 프론트엔드는 이제 서비스별 직접 프록시가 가능하고, `gateway`는 기본 기동 경로가 아닌 선택적 레거시 프로필로 내려갔다.
- 서비스 간 통신은 현재 기준으로 대부분 `RestClient` 기반 동기 HTTP 호출이다.
- Redis와 PostgreSQL은 모든 서비스가 공용 인프라를 사용한다.
- Kafka broker 연동 코드는 현재 레포에서 확인되지 않았다.

## Kafka / Outbox / Inbox 현황

### 결론

- 현재 레포에는 `spring-kafka`, `KafkaTemplate`, `@KafkaListener` 같은 Kafka 연동 코드가 없다.
- 즉, "Kafka를 사용해서 실제로 이벤트를 publish/consume하는 구조"는 아직 구현되어 있지 않다.
- 다만 이벤트 저장용 구조와 inbox/reconciliation용 로컬 테이블은 일부 들어와 있다.

### 코드 기준 확인 내용

| 항목 | 현재 상태 | 설명 |
| --- | --- | --- |
| Kafka 의존성 | 없음 | 각 서비스 `build.gradle`, `shared-kernel/build.gradle` 어디에도 Kafka 의존성이 없다. |
| Kafka producer/consumer | 없음 | `KafkaTemplate`, `@KafkaListener`, broker 설정 코드가 없다. |
| Payment 이벤트 저장 | 있음 | `payment-service`에 `payment_events` 테이블과 `PaymentEventRecorder`가 있다. |
| Ticketing inbox 저장 | 있음 | `ticketing-service`에 `ticketing_inbox_events` 테이블과 dedupe 로직이 있다. |
| Reconciliation 작업 저장 | 있음 | `ticketing-service`에 `reconciliation_tasks`와 재시도 스케줄러가 있다. |
| 실제 outbox relay | 없음 | DB에 쌓인 이벤트를 Kafka로 내보내는 worker/relay가 없다. |
| 실제 inbox consumer | 없음 | Kafka/메시지 브로커에서 읽어오는 consumer가 없다. 현재는 internal API 호출 수신 시 inbox 기록만 한다. |

### 해석

- `payment_events`는 현재 "이벤트 로그 테이블"에 가깝다.
- `ticketing_inbox_events`는 현재 "수신 이벤트 dedupe 및 추적 테이블"에 가깝다.
- 즉, 설계 문서의 목표는 `outbox/inbox/reconciliation` 방향이지만, 실제 런타임은 아직 `HTTP + DB 기록 + scheduler` 단계다.

## 서비스 역할

| 서비스 | 핵심 역할 | 대표 공개 API | 내부 API / 통신 | 주요 데이터/상태 |
| --- | --- | --- | --- | --- |
| `user-auth-service` | 회원가입, 로그인, JWT, 로그아웃, 내 정보, 이메일 인증 | `/api/users/**` | `GET /internal/users/{userId}` 제공 | `users`, 이메일 인증, 일부 fan score 조회 |
| `concert-service` | 콘서트 목록/상세 조회, 스케줄별 좌석 맵 조회 | `/api/concerts/**` | `/internal/concerts/{concertId}/schedules/{scheduleId}`, `/internal/concerts/{concertId}/artist-id` 제공 | 콘서트/스케줄 조회, 좌석 조회용 데이터 |
| `queue-service` | 대기열 진입, 순번 조회, 대기열 이탈, 입장 토큰 전 단계 | `/api/ticketing/**` | `user-auth-service`, `concert-service`를 동기 HTTP로 호출 | Redis 기반 대기열, 입장 순번, 일부 fan score 참조 |
| `ticketing-service` | 좌석 선점/해제, 좌석 화면 입장, 예약 생성/조회, 결제 후 최종 확정, reconciliation | `/api/seats/**`, `/api/bookings/**` | `user-auth-service` 호출, `payment-service`로부터 internal finalization 수신 | `seats`, `bookings`, `booking_items`, inbox, reconciliation task |
| `payment-service` | 결제 생성/제출/승인/취소/환불, Toss 연동, 결제 이력, webhook 처리 | `/api/payments/**` | `ticketing-service` 조회/최종확정 internal API 호출 | `payments`, `payment_events`, `refunds` |
| `gateway` | 임시 외부 진입 프록시 | `:8080` | 각 서비스로 reverse proxy | 상태 저장 없음 |

## 서비스별 상세 메모

### 1. `user-auth-service`

- 인증과 사용자 정보의 소유 서비스다.
- 이메일 인증, 회원가입, 로그인, 로그아웃, 내 정보 조회/수정이 모두 여기 있다.
- 다른 서비스는 내부 API로 사용자 존재 여부나 프로필을 조회한다.

주요 공개 API:

- `POST /api/users/email/send`
- `POST /api/users/email/verify`
- `POST /api/users/login`
- `POST /api/users/signup`
- `POST /api/users/logout`
- `GET /api/users/me`
- `PUT /api/users/me`

주요 내부 API:

- `GET /internal/users/{userId}`

### 2. `concert-service`

- 콘서트와 회차의 조회 책임을 갖는다.
- 좌석 변경 책임보다는 "조회용 seat map"에 더 가깝다.
- `queue-service`가 회차 검증과 artist 조회를 위해 내부 API를 사용한다.

주요 공개 API:

- `GET /api/concerts`
- `GET /api/concerts/{concertId}`
- `GET /api/concerts/{concertId}/seats`
- `GET /api/concerts/schedules/{scheduleId}/seats`

주요 내부 API:

- `GET /internal/concerts/{concertId}/schedules/{scheduleId}`
- `GET /internal/concerts/{concertId}/artist-id`

### 3. `queue-service`

- 대기열의 진입점이다.
- 사용자가 특정 공연/회차에 대해 대기열에 진입하고, 상태를 조회하고, 대기열에서 나갈 수 있다.
- 서비스 내부적으로 `user-auth-service`와 `concert-service`를 호출해 사용자 및 공연/회차 유효성을 검증한다.

주요 공개 API:

- `POST /api/ticketing/start`
- `GET /api/ticketing/status`
- `POST /api/ticketing/leave`

의존 통신:

- `queue-service -> user-auth-service`
- `queue-service -> concert-service`

### 4. `ticketing-service`

- 실질적인 예매 도메인의 중심 서비스다.
- 좌석 선점/해제, 좌석 화면 입장, 예약 생성, 예약 조회, 결제 후 최종 상태 확정이 여기에 있다.
- 또한 inbox/reconciliation 구조도 이 서비스 안에 들어와 있다.
- 결제 이후 최종 정합성 책임은 현재 구조상 `ticketing-service`에 가장 가깝다.

주요 공개 API:

- `POST /api/seats/hold`
- `POST /api/seats/holds`
- `DELETE /api/seats/hold`
- `POST /api/seats/leave`
- `GET /api/seats/seats`
- `POST /api/bookings`
- `GET /api/bookings/{bookingId}`

주요 내부 API:

- `GET /internal/bookings/{bookingId}/payment-context`
- `GET /internal/bookings/users/{userId}/ids`
- `POST /internal/bookings/history-details`
- `POST /internal/finalizations/confirm`
- `POST /internal/finalizations/cancel`
- `POST /internal/finalizations/expire`
- `GET /internal/ops/inbox/summary`
- `GET /internal/ops/inbox`
- `GET /internal/ops/reconciliations/**`

의존 통신:

- `ticketing-service -> user-auth-service`
- `payment-service -> ticketing-service`

### 5. `payment-service`

- 결제의 소유 서비스다.
- 결제 생성, 제출, 승인, 취소, 환불, PG webhook 처리, 결제 이력 조회가 여기에 있다.
- `ticketing-service`에서 payment context를 읽고, 결제 결과를 다시 `ticketing-service`의 internal finalization API에 전달한다.
- 현재 이벤트 저장은 `payment_events` 테이블에 남기지만, Kafka publish는 하지 않는다.

주요 공개 API:

- `POST /api/payments/create`
- `GET /api/payments/{id}`
- `POST /api/payments/{paymentId}/submit`
- `POST /api/payments/{paymentId}/cancel`
- `POST /api/payments/confirm`
- `GET /api/payments/toss/success`
- `GET /api/payments/toss/fail`
- `POST /api/payments/toss/webhook`
- `GET /api/payments/refunds/required`
- `GET /api/payments/history`
- `POST /api/payments/refunds/{paymentId}/request`
- `POST /api/payments/refunds/{paymentId}/complete`
- `GET /api/payments/ops/**`

의존 통신:

- `payment-service -> ticketing-service`
- `payment-service -> Toss Payments`

## 외부 요청 라우팅

### 현재 기본 개발 경로

- `frontend`는 Vite 프록시로 아래 경로를 각 서비스에 직접 연결한다.
- 즉, 현재 기본 compose/dev 기준에서는 `gateway` 없이도 프론트와 5개 서비스 구조를 설명할 수 있다.

현재 `gateway`를 사용할 경우 외부 트래픽은 아래처럼 분기한다.

| 외부 경로 | 실제 대상 서비스 |
| --- | --- |
| `/api/users/**` | `user-auth-service` |
| `/api/concerts/**` | `concert-service` |
| `/api/ticketing/**` | `queue-service` |
| `/api/seats/**` | `ticketing-service` |
| `/api/bookings/**` | `ticketing-service` |
| `/api/payments/**` | `payment-service` |

## 서비스 간 통신

### 현재 구현된 동기 HTTP 호출

| 호출 서비스 | 대상 서비스 | 방식 | 목적 |
| --- | --- | --- | --- |
| `queue-service` | `user-auth-service` | `RestClient` + internal API | 대기열 진입 전 사용자 존재 검증 |
| `queue-service` | `concert-service` | `RestClient` + internal API | 회차가 공연에 속하는지 검증, artist 조회 |
| `ticketing-service` | `user-auth-service` | `RestClient` + internal API | 예약/결제 관련 사용자 프로필 조회 |
| `payment-service` | `ticketing-service` | `RestClient` + internal API | 결제 생성 전 booking payment context 조회 |
| `payment-service` | `ticketing-service` | `RestClient` + internal API | 결제 승인/취소/만료 후 booking 최종 확정 요청 |
| `payment-service` | Toss Payments | `WebClient` | PG confirm 요청 |
| Toss Payments | `payment-service` | redirect / webhook | 성공/실패 redirect, 비동기 webhook 전달 |

### 현재 구현된 비동기성

| 형태 | 현재 상태 |
| --- | --- |
| Kafka broker publish | 없음 |
| Kafka consume | 없음 |
| DB 이벤트 로그 | 있음 (`payment_events`) |
| inbox dedupe 기록 | 있음 (`ticketing_inbox_events`) |
| scheduler 기반 후속 처리 | 있음 (`payment-service`, `ticketing-service`) |
| reconciliation retry | 있음 (`ticketing-service`) |

## 통신 흐름 예시

### 1. 대기열 진입

1. 클라이언트가 `queue-service`의 `/api/ticketing/start` 호출
2. `queue-service`가 `user-auth-service`로 사용자 확인
3. `queue-service`가 `concert-service`로 공연/회차 확인
4. Redis 기반 대기열 상태 생성

### 2. 좌석 진입 및 예약

1. 클라이언트가 `queue-service`에서 대기열 상태 확인
2. 입장 조건 충족 시 `ticketing-service` 좌석 화면 진입 토큰 사용
3. `ticketing-service`에서 좌석 선점/해제
4. `ticketing-service`에서 예약 생성

### 3. 결제 생성 및 최종 확정

1. 클라이언트가 `payment-service`에 결제 생성 요청
2. `payment-service`가 `ticketing-service`에 booking payment context 조회
3. 클라이언트가 Toss 결제 진행
4. Toss 성공/실패 redirect 또는 webhook이 `payment-service`로 들어옴
5. `payment-service`가 payment 상태를 갱신
6. `payment-service`가 `ticketing-service /internal/finalizations/*` 호출
7. `ticketing-service`가 inbox 기록 후 booking/seat 최종 상태 반영
8. mismatch가 있으면 `reconciliation_tasks` 생성 후 scheduler가 후속 처리

## Kubernetes 배포 관점에서 중요한 현재 사실

### 바로 활용 가능한 점

- 5개 백엔드 서비스 각각에 `Dockerfile`이 이미 있다.
- 각 서비스는 `server.port=8080` 기준으로 컨테이너화하기 쉬운 형태다.
- 내부 호출 주소도 서비스명 기반 환경변수로 분리되어 있다.
- health/readiness용 actuator 노출 설정이 들어가 있다.
- `frontend`도 서비스별 직접 프록시를 사용하므로 `gateway`가 기본 전제는 아니다.

### 아직 주의할 점

- PostgreSQL과 Redis가 서비스별 완전 분리가 아니라 공용이다.
- 서비스 간 통신이 아직 동기 HTTP 중심이라, 네트워크 실패 시 영향이 직접 전파된다.
- Kafka 기반 event backbone은 아직 없다.
- `payment_events`와 `ticketing_inbox_events`는 있어도, 실제 broker relay/consumer는 없다.
- 과거 문서에는 `backend`, `gateway` 기준 설명이 일부 남아 있을 수 있다.

## 이번 문서 기준 최종 판단

- 현재 구조는 "서비스별 컨테이너 배포가 가능한 MSA 1차 형태"로 볼 수 있다.
- 하지만 "Kafka 기반 이벤트 드리븐 MSA"로 보기는 아직 어렵다.
- 현재 실체는 아래에 더 가깝다.

1. 서비스별 Spring Boot 애플리케이션 분리
2. gateway 기반 외부 진입점 분리
3. 서비스 간 동기 HTTP 호출
4. 공용 DB/Redis 사용
5. 이벤트 로그 및 inbox/reconciliation 구조 일부 도입
6. Kafka 연동은 아직 미구현

## 다음 작업 추천

1. ECR 업로드용 이미지 이름과 태그 규칙을 정리한다.
2. PostgreSQL/Redis 접속값과 internal API token 전달 방식을 팀 기준으로 정리한다.
3. Kafka를 실제로 도입할지, 아니면 우선 HTTP + reconciliation 구조로 갈지 팀 내 기준을 확정한다.
4. Kafka 도입 시 `payment_events`를 outbox로 승격시키고 relay/consumer를 설계한다.
