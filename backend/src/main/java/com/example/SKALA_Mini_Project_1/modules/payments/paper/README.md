# Payments Step 5-7 Guide and Applied Changes

## Goal
- Step 5: Toss 결제 완료 후 `PAYING -> PAID -> CONFIRMED` 확정 흐름을 안정화
- Step 6: 만료 결제(`PENDING/PAYING`)를 `EXPIRED`로 전환하고 booking/seat hold를 복구
- Step 7: `REFUND_REQUIRED` 처리 흐름과 운영 추적성(이벤트/요약 지표) 확보

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

### 7) 환불/이벤트 도메인 추가 (Step 7)
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/domain/Refund.java`
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/domain/PaymentEvent.java`
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/repository/RefundRepository.java`
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/repository/PaymentEventRepository.java`
- Purpose:
  - `REFUND_REQUIRED` 결제의 환불 요청 추적
  - 상태 전이/웹훅 수신 이벤트 축적

### 8) 결제 서비스에 환불 처리/운영 요약 API 로직 추가
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/service/PaymentService.java`
- Added:
  - `getRefundRequiredPayments()`
  - `requestRefund(paymentId, reasonCode)`
  - `getOpsSummary()`
  - `recordEvent(...)`
- Behavior:
  - `REFUND_REQUIRED` 결제만 환불 요청 생성(`refunds.status=REQUESTED`)
  - 중복 환불요청은 최신 요청 건 재사용(멱등)
  - 운영 요약에서 만료율/환불필요율/웹훅 중복 추정치 제공
  - 주요 상태 전이에 `payment_events` 로그 기록

### 9) 운영/환불 API 엔드포인트 추가
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/controller/PaymentController.java`
- Endpoints:
  - `GET /api/payments/refunds/required`
  - `POST /api/payments/refunds/{paymentId}/request`
  - `GET /api/payments/ops/summary`

### 10) 결제 API 보안 정책 정리 (permitAll 제거)
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/global/config/SecurityConfig.java`
- Changed:
  - `"/api/payments/**"` 광범위 `permitAll` 제거
  - 외부 PG 콜백 경로만 공개: `"/api/payments/toss/**"`
  - 결제 일반 API는 JWT 인증 필수로 통일

### 11) 결제 API 사용자 식별을 JWT principal로 통일
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/controller/PaymentController.java`
- Changed:
  - `create`: body `userId`를 사용하지 않고 SecurityContext principal 사용
  - `submit`: `X-USER-ID` 헤더 제거, principal 사용
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/service/PaymentService.java`
  - `createPayment(PaymentCreateRequest)` -> `createPayment(UUID bookingId, Long userId)` 로 변경
- File: `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/controller/dto/PaymentCreateRequest.java`
  - `userId` 필드 제거
- Frontend alignment:
  - `frontend/src/data/payments.ts`에서 `submit` 호출 시 `X-USER-ID` 제거
  - `frontend/src/components/PaymentRedirectSuccess.vue`에서 `confirm` 호출 전 토큰 필수 체크

## Follow-up Checks
1. 동일 `orderId`로 webhook `DONE`을 2~3회 연속 호출해도 최종 상태가 유지되는지 확인
2. 결제 완료 후 `bookings.status = CONFIRMED`, `seats.status = RESERVED`가 맞는지 확인
3. `EXPIRED` 결제에 `DONE` webhook 도착 시 `REFUND_REQUIRED`로 바뀌는지 확인
4. `PENDING/PAYING` + `expired_at` 지난 건이 스케줄러로 `EXPIRED`로 바뀌는지 확인
5. 위 4번 대상의 booking이 `CANCELED`, 좌석이 `AVAILABLE`로 복구되는지 확인
6. `REFUND_REQUIRED` 건에 `POST /refunds/{paymentId}/request` 호출 시 `refunds`에 `REQUESTED`가 생성되는지 확인
7. `GET /ops/summary` 값이 `verification.sql` 7번 결과와 일치하는지 확인
8. 인증 없이 `/api/payments/ops/summary` 접근 시 401, 인증 포함 시 200 확인
9. 인증 포함 `POST /api/payments/refunds/{paymentId}/request` 호출 시 200 + `refunds` row 생성 확인

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

4. Step 7 ops/refund check (DB snapshot).
- `verification.sql` 6번(`REFUND_REQUIRED` 큐) 기준 1 row 확인.
- `verification.sql` 7번(운영 요약) 기준:
  - total_payments = 4
  - expired_payments = 0
  - refund_required_payments = 1
  - webhook_done_received = 0
  - duplicate_webhook_done_estimated = 0

5. Runtime API check for Step 7.
- `/api/users/login` 및 `/api/users/me`는 정상 응답(200) 확인.
- 동일 토큰으로 `/api/payments/refunds/{paymentId}/request`, `/api/payments/ops/summary` 호출 시 401 발생.
- 현재 실행 중 서버가 최신 Security/Controller 반영본이 아니거나, payments 경로 인증 규칙이 런타임에서 다르게 적용되는 상태로 판단.
- 따라서 Step 7 API E2E 검증은 백엔드 재기동(최신 빌드 반영) 후 재시도 필요.

## Re-test and Fixes (2026-02-18 UTC)
1. Frontend/Backend manual run
- frontend: `npm run dev` (Vite `http://localhost:5173`)
- backend: `./gradlew bootRun` (Tomcat `8081`)

2. Security policy re-check
- unauth `GET /api/payments/ops/summary` -> `401`
- auth `GET /api/payments/ops/summary` -> `200`
- unauth `POST /api/payments/refunds/{paymentId}/request` -> `401`
- 결제 API 전체 `permitAll` 없이 동작 확인.

3. 401 root-cause trace (refund request)
- 실제 원인은 인증 실패가 아니라 서버 내부 예외였다.
- `/api/payments/refunds/{paymentId}/request` 처리 중 예외가 발생하고 `/error` 디스패치가 401로 가려졌다.

4. Applied fixes
- `backend/src/main/java/com/example/SKALA_Mini_Project_1/global/config/SecurityConfig.java`
  - `"/error"`를 `permitAll`에 추가해 내부 예외가 401로 마스킹되지 않도록 수정.
- `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/domain/Refund.java`
  - `id` 필드에 `@GeneratedValue` 추가 (UUID 생성 누락 보완).
- `backend/src/main/java/com/example/SKALA_Mini_Project_1/modules/payments/domain/PaymentEvent.java`
  - `payload_json(jsonb)` 매핑에 `@JdbcTypeCode(SqlTypes.JSON)` 추가.
  - 원인: `jsonb` 컬럼에 `varchar` 바인딩되어 `POST /refunds/.../request`에서 500 발생.

5. Final verification (same payment)
- auth `POST /api/payments/refunds/d8f2ae6c-df49-4103-8b29-45cdd38cb0b6/request` -> `200`
- response includes:
  - `refundId=60d5a41f-d8d2-4e82-97de-edee4a6f9e98`
  - `status=REQUESTED`
  - `reasonCode=AUTOTEST_FIX2`
- DB check:
  - `refunds` row inserted (status `REQUESTED`)
  - `payment_events` row inserted (`event_type=REFUND_REQUESTED`)
