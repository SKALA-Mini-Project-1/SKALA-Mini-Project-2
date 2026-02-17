# Payments Step 5-6 Guide and Applied Changes

## Goal
- Step 5: Toss 결제 완료 후 `PAYING -> PAID -> CONFIRMED` 확정 흐름을 안정화
- Step 6: 만료 결제(`PENDING/PAYING`)를 `EXPIRED`로 전환하고 booking/seat hold를 복구

## Recommended Method (Step 5)
1. 성공 경로를 하나로 모은다.
- `confirm` API와 `webhook(DONE)`이 동일한 내부 확정 함수(`confirmPayment`)를 사용하게 한다.

2. 확정 단계를 원자적으로 처리한다.
- `PAID -> CONFIRMED`
- booking 상태 `CONFIRMED`
- booking에 속한 seats 상태 `RESERVED`
- 같은 트랜잭션 안에서 처리한다.

3. webhook 멱등 규칙을 명확히 둔다.
- 이미 `CONFIRMED`면 즉시 no-op(200)
- `DONE`이 `EXPIRED` 결제에 도착하면 `REFUND_REQUIRED`
- `FAILED/CANCELED`는 `PAYING|PENDING`일 때만 `FAILED`로 전이

4. 조회/변경 충돌을 막는다.
- `findByPgOrderIdForUpdate`를 사용해서 결제 행을 비관 락으로 잡고 상태 전이한다.

## What Was Implemented

### 1) Seat 확정용 DB 업데이트 추가
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/seats/repository/SeatRepository.java`
- Added:
  - `reserveSeatsByBookingId(UUID bookingId)`
  - booking_items 기준으로 연결된 좌석을 일괄 `RESERVED`로 변경

### 2) Payment confirm/webhook 확정 로직 통합
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/service/PaymentService.java`
- Added:
  - `private void confirmPayment(Payment payment, OffsetDateTime now)`
- Behavior:
  - `PAID` 상태만 `CONFIRMED` 전이 허용
  - booking 상태를 `CONFIRMED`로 확정
  - 좌석 상태를 `RESERVED`로 확정
  - 활성 인원 카운터(redis) 감소 로직을 확정 시점으로 통일

### 3) Confirm API 멱등/확정 강화
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/service/PaymentService.java`
- Changed:
  - 결제 조회를 `findByPgOrderIdForUpdate`로 변경
  - 이미 `CONFIRMED`면 그대로 반환
  - 이미 `PAID`면 `confirmPayment` 실행 후 반환
  - `PAYING`인 경우 Toss confirm 후 `PAID -> CONFIRMED`까지 수행

### 4) Webhook 멱등 규칙 반영
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/service/PaymentService.java`
- Changed:
  - 결제 조회를 `findByPgOrderIdForUpdate`로 변경
  - `CONFIRMED`는 즉시 no-op
  - `DONE`:
    - `EXPIRED`면 `REFUND_REQUIRED`
    - `PAYING`이면 `PAID` 후 `confirmPayment`
    - `PAID`면 `confirmPayment`만 실행(복구성)
  - `FAILED/CANCELED`:
    - `PAYING|PENDING`일 때만 `FAILED` 처리

### 5) Payment 만료 스케줄러 구현 (Step 6)
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/scheduler/PaymentScheduler.java`
- Added:
  - `@Scheduled(fixedDelay = 15000)` 만료 스캔
  - 만료 대상: `status IN (PENDING, PAYING)` and `expired_at < now`
  - 처리: `EXPIRED` 전환 + booking `CANCELED` + seats hold `AVAILABLE` 복구

### 6) Scheduler용 조회/복구 Repository 보강
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/repository/PaymentRepository.java`
  - `findTop200ByStatusInAndExpiredAtBeforeOrderByExpiredAtAsc(...)` 추가
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/bookings/repository/BookingRepository.java`
  - `findByIdForUpdate(...)` 추가
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/seats/repository/SeatRepository.java`
  - `releaseSeatHoldsByBookingId(...)` 추가

## Follow-up Checks
1. 동일 `orderId`로 webhook `DONE`을 2~3회 연속 호출해도 최종 상태가 유지되는지 확인
2. 결제 완료 후 `bookings.status = CONFIRMED`, `seats.status = RESERVED`가 맞는지 확인
3. `EXPIRED` 결제에 `DONE` webhook 도착 시 `REFUND_REQUIRED`로 바뀌는지 확인
4. `PENDING/PAYING` + `expired_at` 지난 건이 스케줄러로 `EXPIRED`로 바뀌는지 확인
5. 위 4번 대상의 booking이 `CANCELED`, 좌석이 `AVAILABLE`로 복구되는지 확인

## Verification Assets
- SQL checks: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/paper/verification.sql`
- Webhook idempotency script:
  `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/paper/webhook-idempotency-check.sh`

## Executed Results (2026-02-17 UTC)
1. SQL checks executed against `ticketing` container, DB `postgres`.
- command:
  `docker exec -i ticketing psql -U skala -d postgres < backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/paper/verification.sql`
- result summary:
  - `bookings.status` is `CONFIRMED` for recent rows.
  - all linked seats are already `RESERVED`.
  - `payments.status` is still `PAID` for recent rows (4건), so `CONFIRMED` 전이가 필요한 상태가 존재.

2. Webhook duplicate-call runtime test status.
- API probe to `http://localhost:8081` failed (connection refused), so webhook replay call could not be executed in this session.
- script is prepared and can be run as soon as backend is up:
  `BASE_URL=http://localhost:8081 ORDER_ID=... PAYMENT_KEY=... backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/paper/webhook-idempotency-check.sh`

3. Step 6 scheduler target check.
- `verification.sql`의 4번/5번 쿼리 실행 결과 모두 0 rows.
- current DB snapshot 기준 `PENDING/PAYING` + 만료(`expired_at < now`) 대상이 없어,
  본 세션에서는 `EXPIRED` 자동 전환 샘플 행을 관측하지 못했다.
