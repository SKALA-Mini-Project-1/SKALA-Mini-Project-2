> ⚠️ 이 문서는 `yewon/docs/결제운영진단_Agent_상세설계.md` 기준, **dev-kafka 브랜치 구현 반영 v2 업데이트 버전**입니다.
> 원본 설계 의도는 원본 파일을 참조하세요. 이 문서는 현재 코드와의 정합성을 맞춘 구현 기준 문서입니다.

---

# 결제 운영 진단 Agent 상세 설계 v2

## 1. 문서 목적

이 문서는 `결제 운영 진단 Agent`를 실제로 구현하기 위한 기준 문서다.

목표는 다음과 같다.

- 다른 구현자나 AI가 이 문서만 읽고도 동일한 구조와 동작을 가진 Agent를 구현할 수 있어야 한다.
- 기능 설계뿐 아니라 운영, 보안, 품질, 장애 대응까지 포함한 `구현 직전 수준의 결정 완료 상태`를 제공해야 한다.
- dev-kafka 브랜치에서 실제 구현된 코드(Outbox 패턴, Kafka 연동, Inbox 패턴, ReconciliationTask)를 기준으로 탐지 전략과 스키마를 구체화한다.

## 2. 한 줄 정의

`결제 운영 진단 Agent는 Payment/Ticketing 이벤트를 Kafka를 통해 수집하고, 룰 기반 detector가 이상 incident를 생성하면, LLM 기반 agent가 사건을 해석하여 운영자에게 원인 추정과 운영 절차 수준의 조치 가이드를 제공하는 운영 보조 플랫폼이다.`

## 3. 비목표

이 설계는 아래를 v1 범위에 포함하지 않는다.

- 매크로 사용자 탐지
- 실시간 결제 차단
- 자동 환불
- 자동 좌석 해제
- 운영자 승인 없는 상태 강제 변경
- 복구 버튼 기반 실행 자동화

복구 버튼과 매우 구체적인 수동 복구 실행 가이드는 후속 확장 범위로 둔다.

## 4. 현재 코드베이스 기준 전제 [v2 수정]

현재 저장소(dev-kafka 브랜치) 기준으로 확인되는 도메인 특성은 아래와 같다.

- `Payment` 상태는 `PENDING`, `PAYING`, `PAID`, `CONFIRMED`, `FAILED`, `CANCELED`, `EXPIRED`, `REFUND_REQUIRED`, `REFUNDED`를 사용한다.
- `Booking`은 현재 `HOLDING`, `CONFIRMED`, `CANCELED` 중심으로 운영된다.
- 좌석 hold는 Redis 기반 TTL 구조이며, user hold set과 seat lock owner를 사용한다.
- 결제 만료는 scheduler가 `15초`마다 스캔하는 구조다.
- 결제 진행 구간의 hard deadline은 현재 코드에서 `10분`, 결제 생성 기본 만료는 `5분`이다.
- **Outbox 패턴이 payment-service, ticketing-service 양쪽에 구현 완료되어 있다.**
  - payment-service: `PaymentEventRecorder`, `PaymentOutboxRelayScheduler`, `PaymentKafkaProducer`
  - ticketing-service: `TicketingOutboxRecorder`, `TicketingOutboxRelayScheduler`, `TicketingKafkaProducer`
- **Consumer Inbox 패턴도 ticketing-service에 구현 완료되어 있다.**
  - `TicketingInboxEventService`, `ticketing_inbox_events` 테이블

이 문서의 detector 기본값은 위 현재 코드 흐름을 기반으로 제안한다. 다만 구현은 모두 config로 조정 가능해야 한다.

## 5. 전체 아키텍처

### 5.1 상위 구조

최종 운영 구조는 Kafka + Kubernetes 전제를 따른다.

구성 요소:

1. `Ticketing Svc`
2. `Payment Svc`
3. `Kafka`
4. `incident-detector`
5. `incident-agent`
6. `incident-api`
7. `Incident Store (PostgreSQL + JSONB)`

### 5.2 데이터 흐름

1. Ticketing/Payment 서비스는 도메인 상태 변경 시 outbox에 이벤트를 기록한다.
2. outbox publisher가 Kafka topic으로 이벤트를 발행한다. (**현재 구현 완료**)
3. `incident-detector`가 Kafka 이벤트를 소비해 사건 후보를 판별하고 incident를 생성/갱신한다.
4. `incident-agent`는 생성 또는 갱신된 incident를 비동기로 해석한다.
5. `incident-api`는 운영자 화면에 목록/상세/분석 이력/재해석 요청/상태 전이 기능을 제공한다.

### 5.3 [v2 신규] ReconciliationTask 보조 데이터 소스

ticketing-service에는 `ReconciliationTaskService`가 구현되어 있으며, booking 확정/취소/만료 처리 결과에서 아래 불일치 상황을 자동으로 기록한다.

| mismatchType | 발생 조건 | 관련 incident |
|---|---|---|
| `BOOKING_ALREADY_CANCELED` | payment 확정 신호 → booking이 이미 취소 상태 | 유령 주문 보조 신호 |
| `BOOKING_ALREADY_CONFIRMED` | payment 취소/만료 신호 → booking이 이미 확정 상태 | 중복 처리 보조 신호 |

이 데이터는 incident-detector의 보조 신호로 활용 가능하다. 단, MSA 원칙상 incident-detector가 ticketing-service DB를 직접 읽는 것은 권장하지 않는다. 아래 방식을 권장한다.

- `incident-api`가 ticketing-service의 internal API(`/internal/ops/reconciliation/*`)를 통해 조회하는 방식
- 또는 ReconciliationTask 기록 시 Kafka 이벤트를 추가 발행하는 방식 (v2 확장)

ReconciliationTask는 주된 탐지 수단이 아니라 보조 신호임을 유의한다.

### 5.4 Kubernetes 배치

컴포넌트는 모두 별도 Deployment로 배포한다.

- `incident-detector`
- `incident-agent`
- `incident-api`

분리 이유:

- detector는 Kafka lag와 처리량 기준으로 스케일되어야 한다.
- agent는 LLM 호출 지연과 비용 기준으로 스케일되어야 한다.
- api는 운영자 조회 QPS와 latency 기준으로 스케일되어야 한다.

### 5.5 장애 격리

- detector 장애는 incident 생성 지연으로 이어질 수 있으나 운영자 API를 직접 막아선 안 된다.
- agent 장애는 summary 생성 실패로 이어질 수 있으나 incident 자체 가시성을 막아선 안 된다.
- api 장애는 운영자 조회에만 영향을 줘야 하며 detector/agent 처리 파이프라인을 막아선 안 된다.

## 6. 이벤트 신뢰성 설계

### 6.1 Outbox 패턴 [v2 수정]

Outbox 패턴은 payment-service와 ticketing-service 양쪽에 **구현 완료**되어 있다.

**Payment Outbox 구현 구조:**
- `PaymentEventRecorder`: 도메인 상태 변경과 동일 트랜잭션 내에서 `payment_events` 테이블에 이벤트 기록
- `PaymentOutboxRelayScheduler`: 5초 주기로 `PENDING` 이벤트를 `payment.events.v1` 토픽에 발행
- `PaymentKafkaProducer`: KafkaTemplate 기반 발행, acks=all, idempotence=true, retries=3

**Ticketing Outbox 구현 구조:**
- `TicketingOutboxRecorder`: booking 상태 변경과 동일 트랜잭션 내에서 `ticketing_outbox` 테이블에 이벤트 기록
- `TicketingOutboxRelayScheduler`: 5초 주기로 `PENDING` 이벤트를 `ticketing.events.v1` 토픽에 발행
- `TicketingKafkaProducer`: PaymentKafkaProducer와 동일 설정

### 6.2 Outbox 저장 원칙

도메인 상태 변경과 outbox row 기록은 같은 DB 트랜잭션에서 수행해야 한다.

예:

- payment status 변경 + payment event outbox 기록
- booking confirmed 처리 + booking event outbox 기록

### 6.3 Kafka 운영 규약 [v2 수정]

#### Topic 목록

| 토픽 | 상태 | 용도 |
|---|---|---|
| `payment.events.v1` | **구현 완료** | payment 도메인 이벤트 발행 |
| `ticketing.events.v1` | **구현 완료** | booking 도메인 이벤트 발행 |
| `incident.analysis.requests.v1` | 설계 예정 | incident-agent 분석 요청 |
| `incident.analysis.results.v1` | 설계 예정 | incident-agent 분석 결과 |

#### Partition Key 기준

- payment 관련 이벤트: `paymentId` (ordering_key)
- booking/seat 관련 이벤트: `bookingId` (ordering_key)
- incident 재분석 요청: `incidentId`

#### Retry / 실패 처리 [v2 수정]

현재 구현에서는 DLQ를 사용하지 않는다.

- Outbox Relay Scheduler가 발행 실패 시 `retry_count`를 증가시킨다.
- `retry_count >= 5` 도달 시 `publish_status = FAILED`, `last_error`에 오류 메시지 기록.
- FAILED 건수는 `GET /api/payments/ops/schedulers/health` API로 모니터링 가능.
- DLQ 토픽(`payment.events.dlq.v1` 등)은 **v2 확장 항목**으로 예정되어 있다.

#### Ordering 기준

- 같은 `paymentId` 내 이벤트 순서는 Kafka partition ordering에 의존한다.
- 같은 `bookingId` 내 이벤트 순서는 Kafka partition ordering에 의존한다.
- detector는 cross-topic ordering 완전 일치를 가정하지 않고, 이벤트 타임스탬프와 상태 스냅샷을 함께 사용한다.

### 6.4 Consumer Inbox 패턴 [v2 신규]

ticketing-service에는 Kafka 이벤트 중복 수신 방지를 위해 Inbox 패턴이 구현되어 있다.

**구현 구조:**
- `TicketingInboxEventService` + `ticketing_inbox_events` 테이블
- `dedupe_key = "kafka:{eventId}"` 형식으로 unique 제약 설정
- `tryRecordKafkaEvent()`: 최초 수신 시 `true`, 중복 시 `duplicate_count` 증가 후 `false` 반환
- `PaymentEventConsumer`는 `tryRecordKafkaEvent()`가 `false`를 반환하면 해당 이벤트를 즉시 스킵

**incident-detector도 동일한 Inbox 패턴을 구현해야 한다.**

- 구현 방식: 동일하게 `dedupe_key = "kafka:{eventId}"` 기반 DB unique 제약
- 이유: Kafka consumer는 재시작 또는 리밸런싱 시 같은 메시지를 재수신할 수 있으므로 incident 중복 생성을 방지해야 한다.

**Inbox 운영 모니터링 API (ticketing-service):**
- `GET /internal/ops/inbox/summary`: RECEIVED / DUPLICATE 건수 통계
- `GET /internal/ops/inbox`: 최근 50개 이벤트 상세

## 7. 다루는 incident 유형

v1 범위에서 아래 4종을 모두 포함한다.

### 7.1 중복 결제

정의:

- 같은 `bookingId` 또는 같은 좌석 집합에 대해 결제 승인 또는 확정이 중복 반영된 사건

기본 severity:

- `critical`

기본 detector 신호:

- `DUPLICATE_PAYMENT_CONFIRMED`
- `MULTIPLE_PAYMENTS_FOR_BOOKING`

### 7.2 유령 주문

정의:

- 결제는 성공했으나 booking/seat 후처리가 완료되지 않아 주문이 끝나지 않은 사건

기본 severity:

- `high`

기본 detector 신호:

- `PAYMENT_CONFIRMED_BUT_BOOKING_NOT_CONFIRMED`

### 7.3 좀비 예약

정의:

- 사용자 이탈 또는 비정상 종료 후 hold/seat access가 남아 다른 사용자의 예매를 막는 사건

기본 severity:

- `medium`

기본 detector 신호:

- `BOOKING_ENDED_BUT_SEAT_HOLD_REMAINS`

### 7.4 미확정 결제

정의:

- PG 승인 신호는 존재하지만 내부 payment 확정이 완료되지 않은 사건

기본 severity:

- `high`

기본 detector 신호:

- `PAYMENT_PAID_BUT_NOT_CONFIRMED`
- `WEBHOOK_DONE_BUT_PAYMENT_NOT_CONFIRMED`

## 8. Detector 설계

### 8.1 책임

detector는 아래만 책임진다.

- Kafka 이벤트 소비
- 이벤트 정규화
- 도메인 상태 스냅샷 보강 (필요 시 Redis/DB 직접 조회)
- 룰 기반 이상 판별
- incident 생성/갱신
- analysis 요청 생성

detector는 자연어 설명을 생성하지 않는다.

### 8.2 Detector 입력 — 실제 발행 이벤트 기준 [v2 수정]

detector는 아래 Kafka 토픽의 이벤트를 소비한다.

**`payment.events.v1` 이벤트 타입:**

| 이벤트 타입 | 의미 |
|---|---|
| `PAYMENT_CREATED` | 결제 생성 |
| `SUBMIT_PAYING` | 결제 진행 시작 (PENDING → PAYING) |
| `PAYMENT_PAID` | PG사 승인 완료 (PAYING → PAID) |
| `PAYMENT_CONFIRMED` | 내부 결제 확정 (PAID → CONFIRMED) |
| `PAYMENT_FAILED` | 결제 실패 |
| `PAYMENT_CANCELED_BY_USER` | 사용자 취소 |
| `PAYMENT_EXPIRED` | 결제 만료 |
| `REFUND_REQUESTED` | 환불 요청 |
| `REFUND_COMPLETED` | 환불 완료 |
| `WEBHOOK_*` | PG사 Webhook 수신 이벤트 (DONE, DUPLICATE_IGNORED 등) |

**`ticketing.events.v1` 이벤트 타입:**

| 이벤트 타입 | 의미 |
|---|---|
| `booking.confirmed` | 예약 확정 완료 |
| `booking.canceled` | 예약 취소 완료 |
| `booking.expired` | 예약 만료 처리 완료 |

**현재 Kafka에 발행되지 않는 이벤트 (미발행):**

| 이벤트 | 이유 | 탐지 대안 |
|---|---|---|
| `booking.created` | BookingService에 outbox 없음 | `PAYMENT_CREATED` 이후 흐름으로 대체 |
| `seat.hold.created/released` | Redis TTL 연산 기반, Kafka 미발행 | Redis 직접 폴링으로 대체 |
| `seat.reserved` | DB 직접 업데이트, Kafka 미발행 | `booking.confirmed` 이후 흐름으로 대체 |

### 8.3 detector 기본 임계값 제안

구현은 config로 조정 가능해야 하며, 아래는 v1 기본값 제안이다.

| 유형 | 기준 | 기본값 |
|---|---|---|
| 유령 주문 | `PAYMENT_CONFIRMED` 후 `booking.confirmed` 없음 | 5분 |
| 미확정 결제 | `PAYMENT_PAID` 후 `PAYMENT_CONFIRMED` 없음 | 2분 |
| 미확정 결제 | `WEBHOOK_DONE` 후 `PAYMENT_CONFIRMED` 없음 | 2분 |
| 좀비 예약 | `booking.canceled`/`expired` 후 Redis hold key 잔존 | 1분 유예 |
| 중복 결제 | 같은 bookingId에 `PAYMENT_CONFIRMED` 2건 이상 | 즉시 |

기본값 선정 근거:

- 현재 결제/예약 흐름은 사용자 상호작용보다 후처리 지연 감지가 중요하므로 분 단위 기준을 사용
- scheduler가 `15초` 단위로 동작하므로 너무 짧은 threshold는 오탐 가능성이 높음
- hold는 Redis TTL 기반이라 TTL 종료 직후 즉시 판단보다 짧은 유예 시간을 둔다

### 8.4 detector severity 보정 규칙

severity는 기본값에 아래 보정 규칙을 추가한다.

- 같은 incident 유형이 단일 건이면 기본 severity 유지
- 동일 유형 사건이 짧은 시간 안에 `10건` 이상 누적되면 1단계 상향
- 단일 incident라도 `20분` 이상 미해결이면 1단계 상향
- 직접적인 금전 손실 가능성 또는 고객 이중 결제 가능성이 있으면 `critical` 유지 또는 상향

### 8.5 incident 식별 정책

incident 식별은 `혼합형`으로 고정한다.

- 열린 동일 사건은 기존 incident를 갱신한다.
- `RESOLVED` 이후 동일 도메인 키에서 다시 같은 유형이 발생하면 새 incident를 만든다.

기본 집계 키:

- 중복 결제: `bookingId`
- 유령 주문: `bookingId`
- 좀비 예약: `bookingId` 우선
- 미확정 결제: `paymentId`

### 8.6 4가지 incident 탐지 전략 확정판 [v2 신규]

아래는 현재 이벤트 구조(dev-kafka 브랜치 기준)에서 각 incident를 탐지하는 구체적인 방법이다.

| incident | 탐지 방법 | 사용 데이터 소스 |
|---|---|---|
| **중복 결제** | 동일 `bookingId`로 `PAYMENT_CONFIRMED` 이벤트 2회 이상 수신 | `payment.events.v1` 단독 |
| **유령 주문** | `PAYMENT_CONFIRMED` 수신 후 N분 내에 동일 `bookingId`의 `booking.confirmed` 미수신 | `payment.events.v1` + `ticketing.events.v1` 크로스체크 |
| **미확정 결제** | `PAYMENT_PAID` 또는 `WEBHOOK_*` 수신 후 N분 내에 `PAYMENT_CONFIRMED` 미수신 | `payment.events.v1` 단독 |
| **좀비 예약** | `booking.canceled` 또는 `booking.expired` 수신 → 유예시간 경과 → Redis hold key 잔존 확인 | `ticketing.events.v1` + Redis 직접 폴링 |

**구현 시 필수 유의사항:**

- **유령 주문**: Detector가 두 토픽(`payment.events.v1`, `ticketing.events.v1`)을 동시에 소비하면서 `bookingId` 기준으로 이벤트 상태를 time-window 내에 유지해야 한다. in-memory 또는 별도 DB 테이블을 사용한다.
- **좀비 예약**: Detector는 Redis client 의존성이 필요하다. Redis 조회 키는 ticketing-service의 `RedisKeyGenerator` 구현과 동일한 패턴을 따라야 한다(`seatAccessKey(userId, concertId, scheduleId)` 등).
- **ReconciliationTask 보조 활용**: ticketing-service의 `BOOKING_ALREADY_CANCELED`(→ 유령 주문 보조 신호), `BOOKING_ALREADY_CONFIRMED`(→ 중복 처리 보조 신호)는 incident 판단에 참고 가능하지만, 주된 탐지 수단이 아니다.

## 9. Incident 저장 모델

저장소는 PostgreSQL을 사용하고, LLM/analysis payload는 JSONB를 사용한다.

### 9.1 incident

필수 필드:

- `incident_id`
- `incident_type`
- `incident_key`
- `status`
- `severity`
- `confidence`
- `primary_payment_id`
- `primary_booking_id`
- `user_id`
- `concert_id`
- `schedule_id`
- `first_detected_at`
- `last_detected_at`
- `last_analyzed_at`
- `latest_analysis_version`
- `needs_human_approval`
- `current_state_jsonb`
- `open_reason_signal`
- `resolved_at`
- `resolved_by`

### 9.2 incident_analysis_version

필수 필드:

- `analysis_version_id`
- `incident_id`
- `version_number`
- `analysis_status`
- `model_provider`
- `model_name`
- `input_schema_version`
- `output_schema_version`
- `analysis_input_jsonb`
- `analysis_output_jsonb`
- `summary_text`
- `error_code`
- `error_message`
- `requested_by`
- `trigger_type`
- `created_at`

### 9.3 incident_status_history

필수 필드:

- `history_id`
- `incident_id`
- `from_status`
- `to_status`
- `changed_by`
- `change_reason`
- `created_at`

### 9.4 outbox_event 실제 스키마 [v2 수정]

Payment Outbox (`payment.payment_events` 테이블, `PaymentEvent` 엔티티 기준):

| 필드 | 타입 | 설명 |
|---|---|---|
| `event_id` | UUID | 이벤트 고유 식별자 |
| `payment_id` | UUID | 결제 ID |
| `booking_id` | UUID | 예약 ID |
| `event_type` | varchar(40) | 이벤트 타입 (PAYMENT_CONFIRMED 등) |
| `event_version` | varchar(20) | 스키마 버전 (현재 "v1" 고정) |
| `producer` | varchar(60) | 발행 서비스 ("payment-service") |
| `aggregate_type` | varchar(40) | 집계 루트 타입 ("payment") |
| `aggregate_id` | varchar(100) | payment_id와 동일 |
| `ordering_key` | varchar(100) | Kafka 파티션 키 (payment_id) |
| `from_status` | varchar(20) | 이전 상태 |
| `to_status` | varchar(20) | 다음 상태 |
| `pg_event_id` | varchar(100) | PG사 Webhook 이벤트 ID (중복 감지용) |
| `payload_json` | TEXT | 결제 관련 JSON (bookingId, amount, pgOrderId, pgPaymentKey 등) |
| `idempotency_key` | varchar(255) | 멱등성 키 |
| `correlation_id` | varchar(100) | 상관관계 추적 ID |
| `causation_id` | varchar(100) | 원인 이벤트 ID |
| `trace_id` | varchar(100) | 분산 추적 ID |
| `occurred_at` | timestamptz | 이벤트 발생 시각 |
| `created_at` | timestamptz | 레코드 생성 시각 |
| `publish_status` | varchar(20) | PENDING / PUBLISHED / FAILED |
| `published_at` | timestamptz | Kafka 발행 완료 시각 |
| `retry_count` | int | 발행 재시도 횟수 |
| `last_error` | TEXT | 마지막 오류 메시지 |

Ticketing Outbox (`ticketing.ticketing_outbox` 테이블, `TicketingOutbox` 엔티티 기준):

| 필드 | 타입 | 설명 |
|---|---|---|
| `event_id` | UUID | 이벤트 고유 식별자 |
| `event_type` | varchar | booking.confirmed / booking.canceled / booking.expired |
| `event_version` | varchar | "v1" 고정 |
| `producer` | varchar | "ticketing-service" |
| `aggregate_type` | varchar | "booking" |
| `aggregate_id` | varchar | booking_id |
| `ordering_key` | varchar | booking_id (bookingId 기준 파티셔닝) |
| `booking_id` | UUID | 예약 ID |
| `payment_id` | UUID | 결제 ID |
| `correlation_id` | varchar | booking_id와 동일 |
| `payload_json` | TEXT | 부가 정보 JSON |
| `occurred_at` | timestamptz | 이벤트 발생 시각 |
| `created_at` | timestamptz | 레코드 생성 시각 |
| `publish_status` | varchar | PENDING / PUBLISHED / FAILED |
| `published_at` | timestamptz | Kafka 발행 완료 시각 |
| `retry_count` | int | 재시도 횟수 |
| `last_error` | TEXT | 마지막 오류 메시지 |

**주의**: `payload_json`은 String 타입이며, JSONB가 아니다. `schema_version` 필드는 없으며 `event_version`으로 대체된다.

## 10. Incident lifecycle

### 10.1 상태 정의

- `OPEN`
  detector가 incident를 생성했고 analysis 대기 중
- `ANALYZING`
  agent가 분석 수행 중
- `ANALYZED`
  최신 analysis 결과가 존재
- `ACKNOWLEDGED`
  운영자가 확인했고 조사 중으로 표시
- `RESOLVED`
  운영자가 해결됨 또는 false positive 처리 완료

### 10.2 상태 전이 규칙

- detector 생성 시: `OPEN`
- agent 실행 시작 시: `OPEN -> ANALYZING`
- agent 성공 시: `ANALYZING -> ANALYZED`
- agent 실패 시: `ANALYZING -> ANALYZED`는 아니며 analysis version status를 `FAILED`로 기록하고 incident는 `OPEN` 또는 `ANALYZED` 유지 정책을 사용한다.
  v1에서는 incident 상태는 유지하고 analysis version만 실패로 남긴다.
- 운영자 확인 시: `ANALYZED -> ACKNOWLEDGED`
- 운영자 해결 처리 시: `ACKNOWLEDGED -> RESOLVED`

### 10.3 자동 해결 제안

시스템은 아래처럼 `해결된 것 같음`을 감지할 수 있으나 자동 RESOLVED 전이는 하지 않는다.

예:

- 기존 유령 주문 incident의 booking이 CONFIRMED로 전환됨
- 좀비 예약 incident에서 Redis hold lock이 제거됨
- 미확정 결제 incident에서 payment가 CONFIRMED로 전환됨

이 경우 detector는 `AUTO_RESOLUTION_SUGGESTED` signal을 남긴다.

## 11. Agent 설계

### 11.1 책임

agent는 아래만 책임진다.

- incident input 구성 읽기
- 구조화된 해석 생성
- 자연어 summary 생성
- 권장 액션 생성
- confidence와 보정 의견 제안

agent는 detector 룰 자체를 실행하지 않는다.

### 11.2 실행 시점

agent는 비동기로 실행된다.

트리거:

- incident 신규 생성
- incident 상태/스냅샷 갱신
- 운영자 수동 재해석 요청
- 이전 analysis 실패 후 재시도

### 11.3 LLM 호출 실패 정책

LLM 실패 시 incident는 즉시 유지/노출한다.

- incident 가시성은 숨기지 않는다.
- analysis version은 `FAILED` 또는 `PENDING_RETRY` 상태로 저장한다.
- 재시도 큐에 재등록한다.
- 운영자 화면에는 `AI 분석 대기` 또는 `AI 분석 실패` 상태를 표시한다.

### 11.4 Provider-agnostic 원칙

설계 본문은 특정 벤더 SDK에 종속되지 않는다.

고정 대상:

- input schema
- prompt contract
- output schema
- validation rules
- retry/fallback rules

## 12. Agent 입력 계약

Agent 입력 단위는 `incident 1건`이다.

### 12.1 입력 JSON 스키마

```json
{
  "schemaVersion": "incident-analysis-input.v1",
  "incidentId": "inc_123",
  "incidentTypeCandidate": "GHOST_ORDER",
  "severityCandidate": "high",
  "currentState": {
    "paymentStatus": "CONFIRMED",
    "bookingStatus": "HOLDING",
    "seatHoldStatus": "HELD",
    "seatReservedStatus": "NOT_RESERVED",
    "webhookDoneReceived": true,
    "pgApproved": true
  },
  "signals": [
    "PAYMENT_CONFIRMED_BUT_BOOKING_NOT_CONFIRMED"
  ],
  "timeline": [
    {
      "eventType": "PAYMENT_CREATED",
      "occurredAt": "2026-01-01T10:00:00Z",
      "source": "payment"
    },
    {
      "eventType": "PAYMENT_CONFIRMED",
      "occurredAt": "2026-01-01T10:03:00Z",
      "source": "payment"
    }
  ],
  "relatedResources": {
    "paymentId": "pay_123",
    "bookingId": "book_123",
    "userId": 17,
    "concertId": 1,
    "scheduleId": 3,
    "seatIds": [101, 102]
  },
  "analysisContext": {
    "analysisTrigger": "NEW_INCIDENT",
    "analysisRequestedAt": "2026-01-01T10:08:00Z",
    "previousAnalysisSummary": null
  }
}
```

### 12.2 PII 규칙

Agent 입력에는 아래를 기본 포함하지 않는다.

- email
- phone
- 사용자 이름
- raw card/payment 민감정보

필요한 경우에도 마스킹된 참조값만 허용한다.

## 13. Agent 출력 계약

출력은 `엄격한 JSON + 자연어 summary`로 고정한다.

### 13.1 출력 JSON 스키마

```json
{
  "schemaVersion": "incident-analysis-output.v1",
  "incidentType": "GHOST_ORDER",
  "severity": "high",
  "confidence": 0.89,
  "summary": "결제는 확정됐지만 예약 확정 후처리가 누락된 유령 주문 의심 사건입니다.",
  "suspectedRootCause": "PAYMENT_CONFIRMED 이후 ticketing-service에서 booking.confirmed 이벤트가 발행되지 않아 후처리 단계가 누락된 것으로 보입니다.",
  "recommendedActions": [
    {
      "order": 1,
      "action": "booking 상태 재조회",
      "reason": "현재 booking이 HOLDING에 머물러 있는지 확인합니다."
    },
    {
      "order": 2,
      "action": "ticketing-service Inbox 이벤트 확인",
      "reason": "PAYMENT_CONFIRMED 수신 후 booking.confirmed 처리가 실행됐는지 /internal/ops/inbox에서 확인합니다."
    }
  ],
  "needsHumanApproval": true,
  "reclassificationReason": null,
  "resolutionSuggestion": null
}
```

### 13.2 필수 규칙

- JSON 파싱 가능해야 한다.
- schemaVersion이 반드시 있어야 한다.
- summary는 1~3문장 수준으로 짧아야 한다.
- recommendedActions는 운영 절차 수준이어야 한다.
- SQL, shell command, 내부 관리자 명령을 직접 실행하라는 식의 위험한 지시는 금지한다.

## 14. Prompt 계약

### 14.1 System Prompt 요구사항

시스템 프롬프트는 아래 역할을 반드시 포함해야 한다.

- 너는 운영 보조 incident analyst이다.
- detector가 제공한 candidate와 signals를 존중한다.
- 확정 불가한 내용은 가능성으로 표현한다.
- 구조화된 JSON만 반환한다.
- PII를 새로 유추하거나 복원하지 않는다.
- 조치는 운영 절차 수준으로만 제안한다.

### 14.2 Validation 규칙

- output JSON schema validation 실패 시 결과 버림
- 필수 필드 누락 시 retry
- 재시도 후에도 실패 시 analysis version을 `FAILED` 처리

## 15. 운영자 API 설계 [v2 수정]

### 15.1 현재 구현 완료된 Ops API

아래 API는 이미 각 서비스에 구현되어 있으며, incident-detector/agent 구현 시 참조 및 활용 가능하다.

**payment-service** (인증: `X-Internal-Api-Key` 헤더):

| 엔드포인트 | 기능 |
|---|---|
| `GET /api/payments/ops/summary` | 결제 상태별 통계, Webhook 중복 감지 현황 |
| `GET /api/payments/ops/refund-required` | REFUND_REQUIRED 상태 결제 목록 (최대 50건) |
| `GET /api/payments/ops/webhooks` | 최근 100개 Webhook 이벤트 이력 |
| `GET /api/payments/ops/schedulers/health` | Outbox PENDING/PAYING/EXPIRED/FAILED 건수 |

**ticketing-service** (인증: `X-Internal-Api-Key` 헤더):

| 엔드포인트 | 기능 |
|---|---|
| `GET /internal/ops/inbox/summary` | Inbox RECEIVED/DUPLICATE 건수 통계 |
| `GET /internal/ops/inbox` | 최근 50개 Inbox 이벤트 상세 |

### 15.2 설계 예정 — incident-api

아래 API는 incident-detector/agent와 함께 구현 예정이다. `admin/operator` role만 호출 가능해야 한다.

- `GET /ops/incidents` — incident 목록 조회
- `GET /ops/incidents/{incidentId}` — incident 상세 조회
- `GET /ops/incidents/{incidentId}/analyses` — analysis 이력 조회
- `POST /ops/incidents/{incidentId}/reanalyze` — 수동 재해석 요청
- `POST /ops/incidents/{incidentId}/acknowledge` — ACKNOWLEDGED 전이
- `POST /ops/incidents/{incidentId}/resolve` — RESOLVED 전이

### 15.3 목록 응답 최소 필드

- incidentId
- incidentType
- severity
- status
- latestSummary
- latestAnalysisStatus
- updatedAt

### 15.4 상세 응답 최소 필드

- incident 메타데이터
- currentState
- signals
- timeline
- latestAnalysis
- analysisVersions
- operatorActionHistory

## 16. 운영자 화면 정보 구조

### 16.1 목록 화면

목적:

- 어떤 incident부터 볼지 빠르게 우선순위화

기본 섹션:

- severity 필터
- status 필터
- incident type 필터
- 최신 사건 리스트

각 row 정보:

- severity badge
- incident type
- status
- latest summary
- updatedAt

### 16.2 상세 화면

구조는 `요약 우선 + 근거 드릴다운`이다.

상단:

- incident type
- severity
- summary
- suspectedRootCause
- recommendedActions
- needsHumanApproval

중단:

- currentState snapshot
- primary resources
- latest analysis status

하단:

- timeline
- detector signals
- previous analysis versions
- operator status history

## 17. 재해석 정책

### 17.1 수동 재해석

운영자가 직접 재해석 요청 가능하다.

use case:

- 새 신호를 반영하고 싶을 때
- 기존 summary가 부족할 때
- 모델 장애 복구 후 다시 분석할 때

### 17.2 자동 재해석

아래 경우 자동 재해석 가능:

- 새 이벤트 유입으로 currentState가 의미 있게 변경됨
- detector signal 세트가 변경됨
- analysis 실패 후 retry window 도래

### 17.3 버전 관리

- 재해석마다 새 analysis version 생성
- 최신 version만 기본 화면 표시
- 이전 version은 이력으로 유지

## 18. 구현 품질 및 비기능 규약

이 장의 규칙은 권장사항이 아니라 `필수 구현 규칙`이다.

### 18.1 하드코딩 금지

아래는 코드 하드코딩 금지:

- API key
- DB 비밀번호
- Kafka broker 주소
- topic 이름
- 모델명
- timeout
- threshold
- retry 횟수
- backoff

### 18.2 설정 관리

아래는 반드시 config화:

- detector 임계값
- severity 보정 기준
- retry 정책
- rate limit
- stale 기준
- analysis timeout
- 발행 실패 최대 재시도 횟수 (현재 기본값: 5회)

### 18.3 secrets 관리

- secrets는 환경변수 또는 비밀 저장소 사용
- repo-tracked 파일에 평문 저장 금지

### 18.4 멱등성

- 같은 Kafka 이벤트 재수신 시 중복 incident 생성 금지
- incident-detector는 `TicketingInboxEventService`와 동일한 Inbox 패턴(`dedupe_key = "kafka:{eventId}"`)을 구현해야 한다.
- 같은 analysis request 재처리 시 version 충돌 방지
- 동일 status 전이 중복 기록 방지

### 18.5 테스트 가능성

- clock 주입 가능 구조 필수
- model client interface 분리 필수
- kafka adapter 분리 필수
- detector rule은 가능한 순수 함수 형태로 테스트 가능해야 함
- Redis 조회 로직은 interface로 분리해 테스트 시 mock 가능해야 한다.

### 18.6 schema versioning

아래는 버전 필드 필수:

- event schema (`event_version`)
- incident input schema (`schemaVersion`)
- incident output schema (`schemaVersion`)

### 18.7 로그/PII

- structured logging 필수
- correlation id 포함 필수
- PII 마스킹 또는 제외 필수
- raw payment payload 전체 로그 금지

### 18.8 실패 격리

- detector/agent/api 실패가 서로 직접 전파되지 않도록 분리
- exception을 삼키지 말고 metrics/status로 노출

## 19. 모델 운영 정책

### 19.1 timeout

기본 제안:

- agent 단건 LLM 호출 timeout: `10초`

### 19.2 retry

기본 제안:

- validation 실패 또는 일시 오류 시 최대 `2회` 재시도

### 19.3 concurrency / rate limit

기본 제안:

- agent worker 동시 분석 수 제한
- 동일 incident 반복 재해석은 debounce 적용
- incident 급증 시 low severity 사건은 backlog 처리 가능

### 19.4 비용 보호

- 대량 incident 폭주 시 전량 즉시 LLM 호출 금지
- severity 기반 우선 분석 가능
- backlog가 커질 경우 요약 생성 대기 상태 허용

## 20. SLO 및 관측성

### 20.1 SLO 제안

v1 기본 제안:

- detector 이벤트 → incident 생성 p95: `30초 이내`
- incident 생성 → agent 분석 완료 p95: `2분 이내`
- 운영자 목록 조회 p95: `1초 이내`
- 운영자 상세 조회 p95: `2초 이내`

### 20.2 핵심 메트릭

- Kafka consumer lag
- incident 생성률
- incident 유형별 생성률
- analysis 성공률
- analysis 실패율
- analysis 평균/95지연
- 재해석 요청 수
- API latency
- lifecycle 상태별 체류 시간
- Outbox FAILED 건수 (payment-service, ticketing-service 각각)
- Inbox 중복 감지율 (ticketing-service)

### 20.3 기본 알람 기준 제안

- consumer lag 임계치 초과
- analysis 실패율 급증
- analysis p95 지연 초과
- critical severity incident 급증
- api latency 임계치 초과
- Outbox FAILED 건수 임계치 초과

## 21. 테스트 전략

### 21.1 detector 테스트

incident 4종 각각에 대해:

- 정상 탐지 케이스
- 오탐 방지 케이스
- 이벤트 누락 케이스
- 재발/재오픈 케이스

추가:

- 유령 주문: 두 토픽 이벤트 순서가 바뀐 경우(ticketing.events.v1 먼저 도착)에도 정상 처리되는지
- 좀비 예약: Redis 조회 실패 시 오탐 방지 동작 확인

### 21.2 agent 테스트

- input schema fixture 기반 output schema 검증
- summary 필수 필드 검증
- PII 비노출 검증
- validation 실패 및 retry 검증

### 21.3 lifecycle 테스트

- incident 생성 → analysis → acknowledge → resolve 흐름 검증
- 해결 제안 signal 발생 검증

### 21.4 운영자 API 테스트

- role 기반 접근 제어
- 목록/상세 응답 스키마
- 재해석 요청 처리
- analysis version 이력 조회

## 22. 샘플 시나리오

### 22.1 유령 주문 예시 [v2 수정]

**발생 조건**: PAYMENT_CONFIRMED 이후 5분이 지났는데 booking.confirmed가 오지 않음

입력 신호:

- `PAYMENT_CREATED` (payment.events.v1)
- `SUBMIT_PAYING` (payment.events.v1)
- `PAYMENT_PAID` (payment.events.v1)
- `PAYMENT_CONFIRMED` (payment.events.v1) ← 이후 N분 경과
- `booking.confirmed` **없음** (ticketing.events.v1에서 미수신)

예상 결과:

- incidentType: `GHOST_ORDER`
- severity: `high`
- summary: `결제 확정 후 예약 확정 후처리 누락 의심`
- signal: `PAYMENT_CONFIRMED_BUT_BOOKING_NOT_CONFIRMED`

### 22.2 미확정 결제 예시

**발생 조건**: PAYMENT_PAID 이후 2분이 지났는데 PAYMENT_CONFIRMED가 오지 않음

입력 신호:

- `PAYMENT_PAID` (payment.events.v1)
- `PAYMENT_CONFIRMED` **없음** — 2분 경과

예상 결과:

- incidentType: `UNCONFIRMED_PAYMENT`
- severity: `high`
- signal: `PAYMENT_PAID_BUT_NOT_CONFIRMED`

### 22.3 좀비 예약 예시 [v2 수정]

**발생 조건**: booking.canceled 수신 후 1분 유예 경과, Redis에 hold key 잔존

탐지 흐름:

1. `booking.canceled` 수신 (ticketing.events.v1)
2. 1분 유예 후 Detector가 Redis 조회: `seatAccessKey(userId, concertId, scheduleId)` 키 존재
3. → 좀비 예약 탐지

예상 결과:

- incidentType: `ZOMBIE_HOLD`
- severity: `medium`
- signal: `BOOKING_ENDED_BUT_SEAT_HOLD_REMAINS`

### 22.4 중복 결제 예시

**발생 조건**: 같은 bookingId로 PAYMENT_CONFIRMED 2회 수신

입력 신호:

- `PAYMENT_CONFIRMED` (bookingId=book_123) 1회
- `PAYMENT_CONFIRMED` (bookingId=book_123) 2회

예상 결과:

- incidentType: `DUPLICATE_PAYMENT`
- severity: `critical`
- signal: `DUPLICATE_PAYMENT_CONFIRMED`

## 23. 후속 확장

- DLQ 토픽 도입 (`payment.events.dlq.v1`, `ticketing.events.dlq.v1`) — 현재 FAILED 마킹 방식으로 운영 중
- `booking.created`, `seat.hold.created/released` Kafka 이벤트 추가 — 현재 Redis/DB 직접 연산 기반
- ReconciliationTask Kafka 발행 추가 — 현재 ticketing-service 내부 DB에만 기록
- 복구 버튼 기반 수동 액션
- 자동 복구 워크플로우
- 더 정교한 cost-aware analysis 정책
- incident clustering
- 다중 모델 전략
- 운영자 화면에서 raw payload drill-through
