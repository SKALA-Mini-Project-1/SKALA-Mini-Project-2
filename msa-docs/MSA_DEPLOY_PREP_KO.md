# MSA 배포 준비 정리

작성일: `2026-04-30`

## 문서 목적

- 현재 5개 백엔드 서비스를 Kubernetes 배포 기준으로 정리한다.
- 서비스별 포트, 외부 노출 경로, 내부 의존성, 필수 환경변수를 한 번에 본다.
- `gateway` 제거 이후 Ingress 기반 구조로 옮기기 위한 준비 자료로 사용한다.

## 배포 대상 서비스

- `user-auth-service`
- `concert-service`
- `queue-service`
- `ticketing-service`
- `payment-service`

## 서비스별 실행 정보

| 서비스 | 컨테이너 포트 | 외부 경로 | 내부 의존성 | 비고 |
| --- | --- | --- | --- | --- |
| `user-auth-service` | `8080` | `/api/users/**` | Postgres, Redis | 인증/JWT/이메일 |
| `concert-service` | `8080` | `/api/concerts/**` | Postgres, Redis | 콘서트/회차/좌석 조회 |
| `queue-service` | `8080` | `/api/ticketing/**` | Redis, `user-auth-service`, `concert-service` | 대기열 |
| `ticketing-service` | `8080` | `/api/seats/**`, `/api/bookings/**` | Postgres, Redis, `user-auth-service`, `concert-service` | 좌석/예약/finalization/reconciliation/fan score trigger |
| `payment-service` | `8080` | `/api/payments/**` | Postgres, Redis, `ticketing-service`, Toss | 결제/환불/웹훅 |

## Health Probe 기준

| 서비스 | Liveness | Readiness | 상태 |
| --- | --- | --- | --- |
| `user-auth-service` | `/actuator/health/liveness` | `/actuator/health/readiness` | 설정 확인 |
| `concert-service` | `/actuator/health/liveness` | `/actuator/health/readiness` | 설정 확인 |
| `queue-service` | `/actuator/health/liveness` | `/actuator/health/readiness` | 설정 확인 |
| `ticketing-service` | `/actuator/health/liveness` | `/actuator/health/readiness` | 설정 확인 |
| `payment-service` | `/actuator/health/liveness` | `/actuator/health/readiness` | 설정 확인 |

## Ingress 기준 외부 라우팅

| Path Prefix | 대상 서비스 |
| --- | --- |
| `/api/users` | `user-auth-service` |
| `/api/concerts` | `concert-service` |
| `/api/ticketing` | `queue-service` |
| `/api/seats` | `ticketing-service` |
| `/api/bookings` | `ticketing-service` |
| `/api/payments` | `payment-service` |

## 서비스 간 내부 통신

| 호출 서비스 | 대상 서비스 | 목적 | 현재 방식 |
| --- | --- | --- | --- |
| `queue-service` | `user-auth-service` | 사용자 존재 확인 | HTTP |
| `queue-service` | `user-auth-service` | artist fan score 조회 | HTTP |
| `queue-service` | `concert-service` | 공연/회차 검증, artist 조회 | HTTP |
| `ticketing-service` | `user-auth-service` | 사용자 프로필 조회, fan score 반영 | HTTP |
| `ticketing-service` | `concert-service` | concertId 기준 artistId 조회 | HTTP |
| `payment-service` | `ticketing-service` | 결제 생성 전 booking context 조회 | HTTP |
| `payment-service` | `ticketing-service` | 결제 완료 후 booking finalization 요청 | HTTP |

## 서비스별 주요 환경변수

### 공통

| 이름 | 설명 |
| --- | --- |
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USER` | DB 계정 |
| `DB_PASSWORD` | DB 비밀번호 |
| `REDIS_HOST` | Redis host |
| `REDIS_PORT` | Redis port |
| `JWT_SECRET` | JWT 서명 키 |

### `user-auth-service`

| 이름 | 설명 |
| --- | --- |
| `MAIL_USERNAME` | SMTP 계정 |
| `MAIL_PASSWORD` | SMTP 비밀번호 |
| `EMAIL_FROM_ADDRESS` | 발신 이메일 주소 |
| `EMAIL_FROM_NAME` | 발신자 표시 이름 |
| `USER_AUTH_INTERNAL_API_TOKEN` | 내부 사용자 조회 API 보호 토큰 |

### `concert-service`

| 이름 | 설명 |
| --- | --- |
| `CONCERT_INTERNAL_API_TOKEN` | 내부 공연 조회 API 보호 토큰 |

### `queue-service`

| 이름 | 설명 |
| --- | --- |
| `USER_AUTH_SERVICE_BASE_URL` | `user-auth-service` 내부 주소 |
| `CONCERT_SERVICE_BASE_URL` | `concert-service` 내부 주소 |
| `USER_AUTH_INTERNAL_API_TOKEN` | 사용자 내부 API 호출 토큰 |
| `CONCERT_INTERNAL_API_TOKEN` | 공연 내부 API 호출 토큰 |
| `QUEUE_MAX_SEAT_CAPACITY` | 대기열 입장 가능 최대 좌석 수 |
| `QUEUE_CLEANUP_SCHEDULER_DELAY_MS` | stale queue cleanup 스케줄러 지연 |
| `QUEUE_ACTIVE_SEAT_SYNC_DELAY_MS` | active seat sync 스케줄러 지연 |
| `QUEUE_REDIS_RETRY_MAX_ATTEMPTS` | Redis 재시도 최대 횟수 |
| `QUEUE_REDIS_RETRY_WAIT_MILLIS` | Redis 재시도 대기 시간 |

### `ticketing-service`

| 이름 | 설명 |
| --- | --- |
| `USER_AUTH_SERVICE_BASE_URL` | `user-auth-service` 내부 주소 |
| `CONCERT_SERVICE_BASE_URL` | `concert-service` 내부 주소 |
| `USER_AUTH_INTERNAL_API_TOKEN` | 사용자 내부 API 호출 토큰 |
| `CONCERT_INTERNAL_API_TOKEN` | 공연 내부 API 호출 토큰 |
| `TICKETING_INTERNAL_API_TOKEN` | ticketing internal API 보호 토큰 |
| `TICKETING_FAN_SCORE_SYNC_FIXED_DELAY_MS` | fan score 동기화 스케줄러 지연 |
| `TICKETING_RECONCILIATION_MONITOR_FIXED_DELAY_MS` | backlog 모니터 스케줄러 지연 |
| `TICKETING_RECONCILIATION_REPLAY_FIXED_DELAY_MS` | replay 스케줄러 지연 |
| `TICKETING_RECONCILIATION_RETRY_WAIT_MS` | 재시도 대기 시간 |
| `TICKETING_RECONCILIATION_MAX_AUTO_RETRY_COUNT` | 자동 재시도 횟수 |

### `payment-service`

| 이름 | 설명 |
| --- | --- |
| `TOSS_SECRET_KEY` | Toss secret |
| `TOSS_API_BASE` | Toss base URL |
| `TOSS_CONFIRM_URL` | Toss confirm URL |
| `TICKETING_SERVICE_BASE_URL` | `ticketing-service` 내부 주소 |
| `TICKETING_INTERNAL_API_TOKEN` | ticketing internal API 호출 토큰 |
| `PAYMENT_SUCCESS_REDIRECT_URL` | 결제 성공 리다이렉트 |
| `PAYMENT_FAIL_REDIRECT_URL` | 결제 실패 리다이렉트 |
| `PAYMENT_OPS_API_TOKEN` | payment ops API 보호 토큰 |

## Kubernetes 전환 시 권장 리소스 단위

| 리소스 | 권장 방식 |
| --- | --- |
| 백엔드 앱 | 서비스별 `Deployment` + `Service` |
| 외부 진입 | `Ingress` |
| DB/Redis 접속값 | `Secret` 또는 `ConfigMap` |
| internal API token | `Secret` |
| scheduler 포함 서비스 | 별도 분리 없이 우선 동일 Pod 내 앱 프로세스 유지 |

## 지금 상태에서 중요한 점

- `gateway`는 제거되었고 더 이상 기본 기동 경로가 아니다.
- `frontend`는 서비스별 직접 프록시 구성이 가능하다.
- `backend`는 제거되었다.
- `shared-kernel`은 현재 공통 인프라 모듈로 유지 중이다.
- 5개 백엔드 서비스 모두 `Dockerfile`과 actuator probe 설정을 갖고 있어 `Deployment` 단위 후보로 보기 좋다.

## 다음 단계

1. ECR 업로드용 이미지 이름과 태그 규칙을 고정한다.
2. 환경변수와 Secret 전달 방식을 팀 기준으로 정리한다.
3. 별도 배포 경로에서 `Deployment`, `Service`, `Ingress` 를 관리한다.
