# MSA 분리 작업 기록

## 문서 목적

- 이 문서는 현재 모놀리식 구조를 MSA로 분리하는 전 과정을 기록하기 위한 작업 로그다.
- 이후 다른 프롬프트나 다른 작업 세션에서도 이 문서만 읽으면 현재 상태, 결정 배경, 다음 할 일을 빠르게 파악할 수 있도록 남긴다.
- 원칙적으로 MSA 관련 의미 있는 변경이 있을 때마다 이 문서를 갱신한다.

## 현재 시점

- 작성일 기준: `2026-04-28`
- 현재 프로젝트 상태:
  - `frontend` 별도 디렉터리 존재
  - 기존 `backend` 단일 Spring Boot 애플리케이션 구조는 그대로 유지 중
  - 루트에 Gradle 멀티모듈 MSA 구조를 신규로 추가함
  - 루트 `docker-compose.yaml` 로 `postgres`, `redis`, `gateway`, `frontend`, 5개 백엔드 서비스를 함께 띄울 수 있는 상태
  - `docker compose build`, `docker compose up -d`, Postgres 초기화, gateway를 통한 `concert-service` 조회까지 1차 검증 완료
  - `user-auth 직접 참조 제거`, `concert/ticketing 읽기-쓰기 경계 정리`, `team4-*` 이미지명 반영까지 추가 완료
  - 현재 커스텀 컨테이너는 `team4-*` 이미지명 기준으로 모두 기동 확인

## 최종 목표

1. 현재 백엔드 모놀리스를 서비스 경계 기준으로 분리한다.
2. 각 서비스 폴더 기준으로 `Dockerfile` 을 만든다.
3. 루트 `docker-compose.yaml` 로 전체 서비스를 로컬에서 올려 통합 테스트한다.
4. 로컬 compose 환경이 안정화되면 Kubernetes 매니페스트 작업으로 넘어간다.
5. 이후 EKS 배포 구조로 확장한다.

## 이번 분리의 핵심 원칙

- 처음부터 이상적인 완전 분산 구조를 만들기보다, 현재 코드 정합성을 해치지 않는 선에서 현실적으로 분리한다.
- 데이터 정합성이 강하게 필요한 흐름은 초기 단계에서 한 서비스 안에 둔다.
- 서비스 분리 1차 단계에서는 DB까지 서비스별로 완전 분리하지 않는다.
- 1차 목표는 "서비스 코드와 실행 단위 분리 + compose 기반 실행 검증" 이다.

## 현재 구조에서 확인한 주요 사실

### 기술 스택

- 백엔드: Spring Boot 3.4.x, Java 21, JPA, PostgreSQL, Redis, Spring Security, JWT
- 프론트엔드: Vue 3 + Vite
- 결제: Toss Payments 연동
- 대기열: Redis 기반

### 현재 모듈 구성

- `users`
- `concerts`
- `waiting`
- `seats`
- `bookings`
- `payments`
- `fanscore`

### 현재 결합도 요약

- `waiting` 은 Redis 중심이라 상대적으로 독립적이다.
- `concerts` 는 조회성 성격이 강해서 상대적으로 분리 난이도가 낮다.
- `users` 는 인증/JWT/이메일과 결합되어 있으나 도메인 경계는 비교적 명확하다.
- `seats`, `bookings`, `payments` 는 현재 트랜잭션 흐름과 상태 전이가 강하게 연결되어 있다.
- 특히 결제 성공 이후 좌석 상태 변경, 예약 확정, Redis hold 해제, 팬점수 반영 등이 한 흐름으로 연결되어 있다.

## 합의된 목표 서비스 경계

이번 1차 MSA 분리에서 목표로 하는 서비스는 아래 6개다.

- `frontend`
- `user-auth-service`
- `concert-service`
- `queue-service`
- `ticketing-service`
- `payment-service`

## 서비스 경계 결정 배경

### 1. `frontend`

- Vue 애플리케이션
- 백엔드 API 호출 클라이언트
- 향후 `nginx` 또는 Ingress 뒤에서 정적 서빙 가능

### 2. `user-auth-service`

- 사용자 가입/로그인
- JWT 발급 및 인증 관련 기능
- 이메일 인증
- `/api/users/**` 계열 기능

### 3. `concert-service`

- 콘서트 목록/상세/회차 조회
- 좌석 배치 조회 중 "조회 전용" 성격의 일부 기능은 여기서 소유할 수 있음
- 읽기 중심 서비스

### 4. `queue-service`

- 대기열 진입
- 순번 조회
- 입장 토큰 발급/소비
- Redis 기반 queue 상태 관리

### 5. `ticketing-service`

- 좌석 선점
- 좌석 hold 해제
- 예약 생성
- 예약 만료 처리
- 결제 가능 여부 검증
- 결제 성공 이후 예약 확정
- 좌석 상태 최종 반영

중요:

- `좌석 선택 > 결제 일부 > 예약 확정` 흐름은 데이터 정합성이 매우 중요하므로 초기 분리 단계에서는 `ticketing-service` 안에 묶는다.
- 현재 구조에서는 `seats + bookings + 일부 예약 확정 흐름` 을 함께 유지하는 것이 맞다.

### 6. `payment-service`

- Toss 결제 생성/승인 요청
- PG 응답 처리
- 결제 웹훅 수신
- 결제 이력/환불 이력 관리

중요:

- `payment-service` 는 외부 결제 시스템과 통신하는 책임을 갖는다.
- 예약 확정의 최종 소유권은 가능한 한 `ticketing-service` 가 갖는 방향으로 설계한다.
- 즉, `payment-service` 가 결제 성공 사실을 전달하고, `ticketing-service` 가 최종 상태 확정을 담당하는 구조를 목표로 한다.

## 왜 `ticketing-service` 와 `payment-service` 를 이렇게 나누는가

- 현재 코드 기준으로 결제 성공 시점에 아래 작업들이 강하게 이어진다.
  - 예약 상태 변경
  - 좌석 상태 변경
  - hold 검증
  - hold 해제
  - 일부 자원 정리
  - 팬점수 반영
- 이 모든 걸 처음부터 완전히 분산시키면 분산 트랜잭션, 멱등성, 재시도, 최종 일관성 설계 부담이 커진다.
- 따라서 1차 분리에서는 아래 원칙을 유지한다.
  - 외부 PG 통신 책임: `payment-service`
  - 도메인 정합성 최종 책임: `ticketing-service`

## DB 전략

### 로컬 compose / 1차 MSA 단계

- `PostgreSQL` 컨테이너 1개 사용
- `Redis` 컨테이너 1개 사용
- 초기에는 서비스별 완전한 물리 DB 분리 대신, 공용 Postgres 인스턴스를 사용한다.
- 필요하면 스키마 분리 또는 논리적 경계만 먼저 둔다.

### 이후 Kubernetes / EKS 단계

- 운영 환경에서는 우선 `RDS/Aurora PostgreSQL` 을 가장 먼저 검토한다.
- 꼭 EKS 내부 self-hosted DB를 운영해야 한다면 `StatefulSet + EBS` 방향이 우선이다.
- `EFS` 는 공유 파일 시스템 용도에는 적합하지만, 관계형 DB 데이터 파일 저장소의 1차 선택지로 보지 않는다.

정리:

- 운영 권장: `EKS + RDS/Aurora`
- 차선: `EKS + self-hosted PostgreSQL + EBS`
- 비권장: `EKS + PostgreSQL data directory on EFS`

## `docker-compose` 1차 목표

루트에 최종적으로 `docker-compose.yaml` 을 두고 아래 구성을 목표로 한다.

- `frontend`
- `user-auth-service`
- `concert-service`
- `queue-service`
- `ticketing-service`
- `payment-service`
- `postgres`
- `redis`
- 필요 시 `nginx` 또는 단순 gateway

## `docker-compose` 단계의 기본 원칙

- 서비스 이름으로 내부 네트워크 통신하도록 구성한다.
- 애플리케이션 설정에서 `localhost` 를 직접 참조하지 않도록 바꾼다.
- 모든 민감값은 환경변수 또는 `.env` 로 관리한다.
- `depends_on` 만 믿지 말고, DB/Redis 준비 상태를 고려한 healthcheck 또는 재시도 전략을 둔다.

## 예정 작업 순서

### Phase 0. 사전 정리

1. 서비스 경계 확정
2. 현재 모놀리스 코드에서 각 서비스별 소유 패키지 식별
3. 공통 설정, DTO, 보안, 예외 처리의 이동 전략 결정

### Phase 1. 인프라 컨테이너화 안정화

1. 루트 `docker-compose.yaml` 설계
2. `postgres`, `redis` 를 루트 compose 기준으로 정리
3. 기존 단일 `backend` 를 컨테이너로 올리는 최소 성공 버전 확보
4. 프론트엔드와 연결 확인

주의:

- 완전 분리 전이라도 먼저 "현재 시스템이 compose에서 뜬다" 는 최소 기준을 만들면 이후 디버깅 난이도가 크게 낮아진다.

### Phase 2. 서비스 폴더 분리

1. `user-auth-service` 생성
2. `concert-service` 생성
3. `queue-service` 생성
4. `ticketing-service` 생성
5. `payment-service` 생성
6. 공통 코드 이동 또는 중복 허용 범위 결정

### Phase 3. Dockerfile 작성

각 서비스 폴더 기준으로 아래를 만든다.

- `Dockerfile`
- 필요 시 `.dockerignore`
- 서비스별 환경변수 정의

### Phase 4. 통합 compose 테스트

1. 모든 서비스 기동 확인
2. DB 연결 확인
3. Redis 연결 확인
4. 서비스 간 API 호출 확인
5. 주요 사용자 흐름 점검

핵심 검증 시나리오:

- 회원가입 / 로그인
- 콘서트 조회
- 대기열 진입 / 상태 조회
- 좌석 hold
- 예약 생성
- 결제 생성
- 결제 성공 처리
- 예약 확정 확인

### Phase 5. Kubernetes 준비

1. 서비스별 환경변수/포트/헬스체크 정리
2. 컨테이너 이미지 전략 정리
3. `Deployment`, `Service`, `ConfigMap`, `Secret` 설계
4. 이후 EKS 전환

## 현재 코드에서 예상되는 서비스 귀속

### `user-auth-service`

- `modules/users`
- JWT 관련 보안 구성 일부
- 이메일 인증 관련 코드

### `concert-service`

- `modules/concerts`
- 조회 전용 콘서트/회차 관련 리포지토리
- 좌석 조회 기능 중 읽기 전용 부분은 추후 배치 검토

### `queue-service`

- `modules/waiting`
- Redis queue 관련 코드
- 입장 토큰 로직

### `ticketing-service`

- `modules/seats`
- `modules/bookings`
- 예약 확정에 필요한 일부 결제 후처리 로직

### `payment-service`

- `modules/payments`
- Toss client
- 환불 이력/결제 이벤트

### 공통 검토 대상

- `global/config`
- `global/redis`
- `global/email`
- `common`
- 인증 필터 및 보안 설정
- 팬점수 연관 로직

## 현재 확인된 리스크

### 1. 결제와 예약 확정의 경계가 복잡함

- 현재 `PaymentService` 내부에 예약/좌석/hold 정리 로직이 들어가 있다.
- 이를 바로 분리하면 도메인 경계 충돌이 발생할 수 있다.

대응 원칙:

- 1차 분리에서는 우선 "동작 보존" 을 우선한다.
- 필요한 경우 처음에는 일부 내부 API 호출 또는 임시 공유 DB 접근을 허용한다.

### 2. Redis host 설정이 컨테이너 기준으로 바뀌어야 함

- 현재 설정에 `spring.data.redis.host=localhost` 가 있다.
- compose 내부에서는 서비스명 기준으로 바뀌어야 한다.

### 3. 기존 `infra/nginx.conf` 는 host 기반 가정이 있음

- 현재 upstream 이 `host.docker.internal` 기준이라 compose 내부 서비스명 기준으로 다시 정리해야 한다.

### 4. 단일 DB에서 여러 서비스가 같은 테이블을 계속 접근할 수 있음

- 1차 분리 단계에서는 불가피할 수 있다.
- 다만 장기적으로는 서비스 간 소유 테이블 경계를 정리해야 한다.

## 이번 분리에서 지켜야 할 설계 가드레일

- 서비스 분리는 하되, 사용자 흐름이 깨지지 않아야 한다.
- `ticketing-service` 는 좌석/예약 정합성의 중심이 되어야 한다.
- `payment-service` 는 PG 연동 중심으로 수렴해야 한다.
- `queue-service` 는 Redis 중심으로 독립성을 최대한 확보한다.
- 처음부터 이벤트 기반 아키텍처를 과도하게 도입하지 않는다.
- 로컬 compose 에서 재현 가능한 구조를 먼저 만든다.

## 작업 시작 전 체크리스트

- [x] 서비스 경계 6개 합의
- [x] `ticketing-service` 에 좌석/예약/일부 확정 흐름 묶기 합의
- [x] `queue-service` 별도 분리 합의
- [x] 로컬 compose 선행 후 k8s 전환 합의
- [x] EKS 단계에서 DB는 `RDS/Aurora 우선`, self-hosted 시 `EBS 우선` 방향 합의
- [x] 서비스별 패키지 이동 계획 상세화
- [x] 루트 `docker-compose.yaml` 초안 작성
- [x] 서비스별 `Dockerfile` 초안 작성
- [ ] 통합 테스트 시나리오 확정

## 다음 작업에서 우선 할 일

1. gateway 뒤에서 주요 사용자 흐름별 API smoke test 범위를 넓힌다.
2. `ticketing-service` 와 `payment-service` 경계에서 실제 네트워크 호출로 바꿀 후보 지점을 정리한다.
3. Harbor push 기준 태그 규칙과 레지스트리 변수 구조를 정리한다.
4. `.env`, `Secret`, `ConfigMap` 후보 값을 정리해 k8s 전환 준비를 시작한다.
5. 이후 Kubernetes `Deployment` / `Service` / `ConfigMap` 초안으로 넘어간다.

## 작업 로그

### 2026-04-28 - 초기 계획 수립

- 현재 프로젝트 구조를 확인했다.
- 현재 백엔드는 단일 Spring Boot 애플리케이션이며, 프론트는 별도 Vue 프로젝트임을 확인했다.
- `infra/docker-compose.yml` 에 `postgres`, `redis`, `nginx` 일부 구성이 있음을 확인했다.
- 현재 코드 결합도를 기준으로 `waiting` 과 `concerts` 는 상대적으로 먼저 분리 가능한 후보임을 확인했다.
- 반대로 `seats`, `bookings`, `payments` 는 결합도가 높아 첫 단계에서 완전 독립 분리하기 어렵다고 판단했다.

### 2026-04-28 - 서비스 경계 합의

- 아래 6개 서비스를 1차 목표 서비스로 합의했다.
  - `frontend`
  - `user-auth-service`
  - `concert-service`
  - `queue-service`
  - `ticketing-service`
  - `payment-service`
- 특히 `seats + bookings + 일부 예약 확정 흐름` 은 `ticketing-service` 안에 묶는 방향으로 합의했다.
- `대기열은 별도 분리 가능하지만, 좌석 선택 > 결제 일부 > 예약 확정 흐름은 데이터 정합성 때문에 묶어야 한다` 는 기준을 명시했다.

### 2026-04-28 - EKS 단계 DB 방향 합의

- 향후 EKS 운영 시 DB 저장소 선택에 대해 검토했다.
- 결론은 아래와 같다.
  - 운영 우선 권장: `RDS/Aurora`
  - self-hosted DB 필요 시: `EBS`
  - `EFS` 는 DB 데이터 저장소 1차 선택지로 보지 않음

### 2026-04-28 - MSA 코드 분리 작업 시작

- 실제 코드 분리 작업을 시작했다.
- 작업 시작 전 루트에 이 문서를 생성해 이후 세션에서도 이어서 참고할 수 있는 기록 기준점을 만들었다.
- 현재 브랜치에서 직접 작업하며, 기존 `backend` 원본은 유지한 상태로 신규 MSA 구조를 병행 생성하는 전략을 택했다.

### 2026-04-28 - 서비스별 소스 귀속 기준 확정

- 1차 분리 기준을 아래처럼 확정했다.
  - `user-auth-service`
    - `modules/users`
    - 이메일 인증 관련 코드
    - JWT 인증 관련 코드 사용
    - 팬점수 조회(`me`)에 필요한 `fanscore` 참조
  - `concert-service`
    - `modules/concerts`
    - 좌석 맵 조회용 `ConcertSeatController`, `SeatMapService`, `SeatMapResponse`
  - `queue-service`
    - `modules/waiting`
    - 대기열/입장토큰/스케줄러
  - `ticketing-service`
    - `modules/bookings`
    - `modules/seats` 중 좌석 hold/해제/입장 처리
  - `payment-service`
    - `modules/payments`
    - 결제 외부 연동 및 결제 이력/환불 흐름
  - `shared-kernel`
    - `common`
    - `global/jwt`
    - `global/redis`
    - `global/config` 중 공용 가능한 설정
    - `modules/users` 의 `User`, `UserRepository`
    - `modules/fanscore`
    - `modules/bookings` 의 domain/repository
    - `modules/seats` 의 domain/repository

### 2026-04-28 - 멀티모듈 Gradle 구조 추가

- 루트에 아래 파일을 추가했다.
  - `settings.gradle`
  - `build.gradle`
- 신규 Gradle 모듈을 추가했다.
  - `shared-kernel`
  - `user-auth-service`
  - `concert-service`
  - `queue-service`
  - `ticketing-service`
  - `payment-service`
- 기존 `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/` 를 루트로 복사해 멀티모듈 빌드 기준으로 사용하기 시작했다.

### 2026-04-28 - 신규 서비스 폴더 생성

- 루트에 아래 신규 폴더를 생성했다.
  - `shared-kernel/`
  - `user-auth-service/`
  - `concert-service/`
  - `queue-service/`
  - `ticketing-service/`
  - `payment-service/`
  - `gateway/`
- 각 서비스에 `src/main/java`, `src/main/resources` 기본 구조를 만들었다.

### 2026-04-28 - 서비스별 진입점 및 보안 설정 추가

- 각 서비스에 독립 실행용 Spring Boot application class 를 추가했다.
  - `UserAuthServiceApplication`
  - `ConcertServiceApplication`
  - `QueueServiceApplication`
  - `TicketingServiceApplication`
  - `PaymentServiceApplication`
- 각 서비스별 `SecurityConfig` 를 새로 만들었다.
- 서비스별 `scanBasePackages` 를 명시해, 분리 후 다른 서비스의 컨트롤러/빈을 실수로 함께 스캔하지 않도록 했다.

### 2026-04-28 - queue 의존 분리 보정

- `concert-service` 와 `ticketing-service` 는 원래 `QueueService` 를 직접 참조하고 있었다.
- 이 상태로 두면 `modules.waiting` 전체를 함께 스캔해야 해서 대기열 컨트롤러/스케줄러가 다른 서비스에 섞여 들어갈 위험이 있었다.
- 이를 피하기 위해 두 서비스에 `QueueEntryTokenService` 를 별도로 추가했다.
- 현재는 Redis 기반 토큰 소비 로직만 로컬 헬퍼로 유지하고 있다.
- 즉, 완전한 HTTP 기반 서비스 호출로 바꾸기 전까지의 "전이 단계 분리" 로 본다.

### 2026-04-28 - 서비스별 설정 파일 추가

- 각 서비스에 `application.properties` 를 추가했다.
- 공통 방향:
  - `DB_URL`, `DB_USER`, `DB_PASSWORD`
  - `REDIS_HOST`, `REDIS_PORT`
  - `JWT_SECRET`, `JWT_EXPIRATION`
- 추가 개별 설정:
  - `user-auth-service`
    - 메일 발송 설정
  - `payment-service`
    - Toss 연동 설정
- 기존 `localhost` 고정 Redis 설정은 컨테이너 네트워크 환경을 고려해 환경변수 기반으로 바꿨다.

### 2026-04-28 - Dockerfile 및 compose 초안 추가

- 각 서비스 폴더에 `Dockerfile` 을 추가했다.
  - `user-auth-service/Dockerfile`
  - `concert-service/Dockerfile`
  - `queue-service/Dockerfile`
  - `ticketing-service/Dockerfile`
  - `payment-service/Dockerfile`
- 프론트용 `frontend/Dockerfile` 도 추가했다.
- 라우팅용 `gateway/nginx.conf` 를 새로 만들었다.
- 루트에 `docker-compose.yaml` 을 추가했다.
- 루트에 `.dockerignore` 도 추가했다.

### 2026-04-28 - gateway 라우팅 기준

- 현재 gateway 는 prefix 기준으로 서비스 라우팅하도록 구성했다.
  - `/api/users/**` -> `user-auth-service`
  - `/api/concerts/**` -> `concert-service`
  - `/api/ticketing/**` -> `queue-service`
  - `/api/seats/**` -> `ticketing-service`
  - `/api/bookings/**` -> `ticketing-service`
  - `/api/payments/**` -> `payment-service`

### 2026-04-28 - 초기 분리 단계에서 남겨둔 전이 구조

- 이번 단계는 "완전한 네트워크 분리 완료" 가 아니라 "빌드 가능하고 컨테이너화 가능한 실행 단위 분리" 가 목표다.
- 따라서 아래는 아직 남아 있는 전이 구조다.
  - 공통 엔티티/리포지토리의 `shared-kernel` 공유
  - `payment-service` 가 `booking`, `seat`, `fanscore`, `user` 쪽 shared 코드에 의존
  - `ticketing-service` 가 Redis 기반 좌석 입장 토큰 소비 로직을 로컬 헬퍼로 유지
  - 이후 단계에서 점진적으로 내부 API 호출 또는 더 명확한 경계로 바꿔야 한다.

### 2026-04-28 - 정리 작업

- 신규 MSA 폴더 생성 과정에서 잘못 복사된 중복 경로가 일부 생겨 정리했다.
- 이 정리는 새로 만든 MSA 구조 안에서만 수행했고, 기존 `backend` 원본은 건드리지 않았다.
- 결제 서비스 폴더 안의 잠금 백업 파일(`.LCK...~`)도 정리했다.

### 2026-04-28 - 검증 결과

- Gradle 멀티모듈 컴파일 검증:
  - `./gradlew :user-auth-service:compileJava :concert-service:compileJava :queue-service:compileJava :ticketing-service:compileJava :payment-service:compileJava`
  - 결과: 성공
- Gradle 서비스별 bootJar 검증:
  - `./gradlew :user-auth-service:bootJar :concert-service:bootJar :queue-service:bootJar :ticketing-service:bootJar :payment-service:bootJar`
  - 결과: 성공
- compose 문법 검증:
  - `docker compose config`
  - 결과: 성공
- compose 경고:
  - 구식 `version` 키 경고가 있어 제거했다.
- 실제 백엔드 이미지 빌드 시도:
  - `docker compose build user-auth-service concert-service queue-service ticketing-service payment-service`
  - 최초 결과: 실패
  - 초기 원인: 현재 실행 환경에서 Docker daemon 연결 불가
  - 초기 메시지: `Cannot connect to the Docker daemon at unix:///Users/jihyunpark/.docker/run/docker.sock`

### 2026-04-28 - Docker Compose 실제 빌드/기동 성공

- 이후 권한이 허용된 환경에서 `docker compose build` 를 다시 수행했다.
- 서비스 이미지 빌드 결과:
  - `user-auth-service`: 성공
  - `concert-service`: 성공
  - `queue-service`: 성공
  - `ticketing-service`: 성공
  - `payment-service`: 성공
  - `frontend`: 성공
- `docker compose up -d` 로 전체 스택 기동을 시도했다.
- 최종적으로 아래 컨테이너가 `Up` 상태임을 확인했다.
  - `skala-postgres`
  - `skala-redis`
  - `skala-gateway`
  - `frontend`
  - `user-auth-service`
  - `concert-service`
  - `queue-service`
  - `ticketing-service`
  - `payment-service`

### 2026-04-28 - Postgres 초기화 및 DB 검증

- 루트 compose 에서 Postgres 초기 데이터 구성을 위해 `infra/postgres/init.sql` 을 추가했다.
- 초기화 스크립트는 아래 테이블과 샘플 데이터를 생성하도록 구성했다.
  - `artist`
  - `concerts`
  - `schedules`
  - `seats`
- 초기 스크립트에서 `NULL` 타입 추론 문제를 수정했다.
  - `held_by`: `CAST(NULL AS BIGINT)`
  - `held_until`: `CAST(NULL AS TIMESTAMP)`
- 검증 명령:
  - `docker exec skala-postgres psql -U skala -d concert -c "select count(*) as concerts from concerts; select count(*) as schedules from schedules; select count(*) as seats from seats;"`
- 검증 결과:
  - `concerts = 1`
  - `schedules = 1`
  - `seats = 4`
- 추가 readiness 검증:
  - `docker exec skala-postgres pg_isready -U skala -d concert`
  - 결과: `/var/run/postgresql:5432 - accepting connections`

### 2026-04-28 - gateway 슬래시 리다이렉트 이슈 수정

- 초기 gateway 설정은 `/api/concerts/` 같은 trailing slash prefix location 만 정의하고 있었다.
- 그 결과 `http://localhost:8080/api/concerts` 요청 시 Nginx 가 `301 Moved Permanently` 로 `/api/concerts/` 로 리다이렉트했다.
- 하지만 `concert-service` 의 실제 매핑은 `/api/concerts` 여서, 리다이렉트 후 `404` 가 발생했다.
- 대응:
  - `gateway/nginx.conf` 에 슬래시 없는 prefix location 을 추가했다.
  - gateway 컨테이너를 재시작해 변경된 Nginx 설정을 반영했다.
- 최종 검증:
  - `curl -i http://localhost:8080/api/concerts`
  - 결과: `HTTP/1.1 200`
  - 응답 본문에서 샘플 콘서트 1건 JSON 반환 확인

### 2026-04-28 - 현재 커밋 기준 판단

- 현재 기준으로 아래 항목은 충족했다.
  - MSA용 멀티모듈 구조 추가
  - 서비스별 Dockerfile 추가
  - 루트 compose 추가
  - Postgres/Redis/gateway/frontend 포함 전체 스택 기동
  - Postgres 초기화 및 데이터 확인
  - gateway 를 통한 `concert-service` 조회 성공
- 즉, "compose로 전체를 띄우고 DB까지 붙는 1차 기준" 은 충족한 상태다.
- 다만 아직 남아 있는 후속 작업은 있다.
  - 모든 서비스 API smoke test 확대
  - `ticketing-service` 와 `payment-service` 사이 전이 구조 축소
  - k8s 리소스 초안 작성

### 2026-04-28 - user-auth 직접 참조 제거

- 목적:
  - `queue-service`, `ticketing-service` 가 `shared-kernel` 의 `UserRepository` 와 `User` 엔티티를 직접 참조하지 않도록 정리한다.
  - 사용자 정보 소유 책임을 `user-auth-service` 로 더 명확히 모은다.
- 적용 내용:
  - `user-auth-service` 에 내부 사용자 조회 API를 추가했다.
    - `GET /internal/users/{userId}`
  - 관련 파일:
    - `user-auth-service/.../modules/users/InternalUserController.java`
    - `user-auth-service/.../modules/users/dto/InternalUserProfileResponse.java`
  - `user-auth-service` 보안 설정에서 `/internal/users/**` 를 허용했다.
  - `queue-service` 에 `integration.userauth.UserAuthClient` 를 추가했다.
    - `QueueService.startTicketing()` 에서 더 이상 `UserRepository` 를 직접 조회하지 않고 `user-auth-service` 내부 API로 사용자 존재를 검증한다.
  - `ticketing-service` 에 `integration.userauth.UserAuthClient` 를 추가했다.
    - `BookingService.getBookingDetail()` 에서 더 이상 `UserRepository` / `User` 엔티티를 직접 참조하지 않고 내부 API로 사용자 프로필을 조회한다.
- 설정 변경:
  - `queue-service`, `ticketing-service` 에 `user-auth-service.base-url` 설정을 추가했다.
  - compose 환경변수에도 `USER_AUTH_SERVICE_BASE_URL=http://user-auth-service:8080` 를 반영했다.
- 검증:
  - `curl -i http://localhost:18081/internal/users/999999`
  - 결과: `HTTP/1.1 404`
  - 즉 내부 사용자 조회 API가 비존재 사용자에 대해 404 로 응답하는 것까지 확인했다.

### 2026-04-28 - concert/ticketing 읽기-쓰기 경계 정리

- 목적:
  - `concert-service` 는 좌석 맵 "조회" 만 담당하고, 좌석 화면 입장 권한 부여와 토큰 소비는 `ticketing-service` 가 담당하도록 정리한다.
- 배경:
  - 프론트는 이미 `queue -> ticketing(/api/seats/seats) -> concert(/api/concerts/.../seats)` 흐름을 사용하고 있었다.
  - 따라서 `concert-service` 에 남아 있던 대기열 입장 토큰 소비 책임만 제거하면 읽기/쓰기 경계가 더 선명해진다.
- 적용 내용:
  - `concert-service` 의 `ConcertSeatController` 에서 `entryToken` 소비 로직을 제거했다.
  - `concert-service` 는 이제 Redis 에 기록된 seat access 권한이 있는지만 확인하고, 없으면 `403` 으로 거절한다.
  - `concert-service` 에서 더 이상 `QueueEntryTokenService` 를 사용하지 않으며, 해당 파일을 삭제했다.
- 결과:
  - 좌석 화면 입장/권한 부여는 `ticketing-service`
  - 좌석 맵 조회는 `concert-service`
  - 로 책임이 분리된 상태가 되었다.

### 2026-04-28 - scanBasePackages 누락 이슈 수정

- 내부 HTTP 클라이언트 추가 후 첫 기동에서 `queue-service`, `ticketing-service` 가 `UserAuthClient` 빈을 찾지 못해 종료했다.
- 원인:
  - 두 서비스의 `SpringBootApplication(scanBasePackages=...)` 에 `com.example.SKALA_Mini_Project_1.integration` 패키지가 포함되지 않았다.
- 조치:
  - `QueueServiceApplication`
  - `TicketingServiceApplication`
  - 두 파일 모두 scan 대상에 `integration` 패키지를 추가했다.
- 결과:
  - 두 서비스 모두 재빌드 후 정상 기동을 확인했다.

### 2026-04-28 - team4 이미지명 정리

- Harbor 업로드 전 단계로 로컬 compose 이미지명을 모두 `team4-*` 규칙으로 맞췄다.
- 현재 커스텀 이미지명:
  - `team4-user-auth-service`
  - `team4-concert-service`
  - `team4-queue-service`
  - `team4-ticketing-service`
  - `team4-payment-service`
  - `team4-frontend`
- `docker-compose.yaml` 에 각 서비스의 `image:` 를 위 이름으로 명시했다.
- 이후 Harbor push 시 이 이름을 그대로 `tag/push` 기준으로 사용할 수 있다.

### 2026-04-28 - gateway 베이스 경로 라우팅 재정리

- 증상:
  - 직접 `concert-service` 를 호출하면 `GET /api/concerts` 가 `200` 이었지만,
  - gateway 경유 `GET /api/concerts` 는 재기동 중간 시점에 `401` 이 발생했다.
- 원인:
  - Nginx prefix location 설정으로 베이스 경로(`/api/concerts`) 요청이 upstream 에서 의도와 다르게 해석될 여지가 있었다.
- 조치:
  - `gateway/nginx.conf` 를 수정해
    - 베이스 경로는 `location = /api/...`
    - 하위 경로는 `location ^~ /api/.../`
    - 로 분리했다.
  - gateway 를 재시작해 새 설정을 반영했다.
- 최종 검증:
  - `curl -i http://localhost:8080/api/concerts`
  - 결과: `HTTP/1.1 200`
  - 샘플 콘서트 JSON 응답 확인

### 2026-04-28 - 최종 상태 스냅샷

- 최종 `docker compose ps -a` 기준 상태:
  - `user-auth-service`: Up
  - `concert-service`: Up
  - `queue-service`: Up
  - `ticketing-service`: Up
  - `payment-service`: Up
  - `frontend`: Up
  - `gateway`: Up
  - `postgres`: Up
  - `redis`: Up
- 추가 검증:
  - `curl -i http://localhost:18081/internal/users/999999` -> `404`
  - `curl -i http://localhost:18082/api/concerts` -> `200`
  - `curl -i http://localhost:8080/api/concerts` -> `200`

### 2026-04-28 - Harbor 이미지 푸시 완료

- Harbor 정보:
  - Registry: `amdp-registry.skala-ai.com`
  - Project: `skala25a`
- 로컬 `team4-*` 이미지를 Harbor 경로로 태그 후 push 완료했다.
- 업로드한 이미지:
  - `amdp-registry.skala-ai.com/skala25a/team4-user-auth-service:latest`
  - `amdp-registry.skala-ai.com/skala25a/team4-concert-service:latest`
  - `amdp-registry.skala-ai.com/skala25a/team4-queue-service:latest`
  - `amdp-registry.skala-ai.com/skala25a/team4-ticketing-service:latest`
  - `amdp-registry.skala-ai.com/skala25a/team4-payment-service:latest`
  - `amdp-registry.skala-ai.com/skala25a/team4-frontend:latest`
- 확인된 digest:
  - `team4-user-auth-service`: `sha256:9969543dd4de9c7d48f521e83066f988d544cf430218dfef36fce044fac8cd36`
  - `team4-concert-service`: `sha256:09417ff7d25f0c20c88202e8e99291d7b95e681da0929df1c2e76631de49ae71`
  - `team4-queue-service`: `sha256:5297cc985a35696941a1c9868e4081a858917e517bfde57c47374ee4497975d5`
  - `team4-ticketing-service`: `sha256:faa7a895e85457d1d9a27ee26bc4824a0428923aae4508fa265503a94dd1d31e`
  - `team4-payment-service`: `sha256:313e5af18d15f24224a8c294919618769d6ae56c0ad4eac79d6476c5c69aaa69`
  - `team4-frontend`: `sha256:ec7a05b990d6bd7ce5cdc10a10a1c8e39f1411b25ff5e8baa9dc36821e5958ee`

### 2026-04-28 - 현재까지 생성/수정된 핵심 파일

- 빌드/루트
  - `settings.gradle`
  - `build.gradle`
  - `.dockerignore`
  - `docker-compose.yaml`
- 공용
  - `shared-kernel/build.gradle`
- 서비스별 빌드
  - `user-auth-service/build.gradle`
  - `concert-service/build.gradle`
  - `queue-service/build.gradle`
  - `ticketing-service/build.gradle`
  - `payment-service/build.gradle`
- 서비스별 진입점
  - `user-auth-service/.../UserAuthServiceApplication.java`
  - `concert-service/.../ConcertServiceApplication.java`
  - `queue-service/.../QueueServiceApplication.java`
  - `ticketing-service/.../TicketingServiceApplication.java`
  - `payment-service/.../PaymentServiceApplication.java`
- 서비스별 보안 설정
  - 각 서비스 `global/config/SecurityConfig.java`
- 서비스별 Dockerfile
  - 각 서비스 `Dockerfile`
  - `frontend/Dockerfile`
- gateway
  - `gateway/nginx.conf`

### 2026-04-28 - 다음 우선 작업

1. 실제 `docker compose build` 또는 `docker compose up` 기준의 런타임 검증
2. 서비스 간 호출을 현재 shared 구조에서 HTTP 경계로 더 분리할지 결정
3. `payment-service` 와 `ticketing-service` 사이의 최종 정합성 책임 경계 재정리
4. 필요 시 서비스별 DB 스키마 또는 테이블 소유권 문서화

현재 바로 이어서 필요한 실무 작업:

- Docker Desktop 또는 Docker daemon 실행 상태 확인
- 그 다음 `docker compose build`
- 이어서 `docker compose up`
- 각 서비스 로그와 DB/Redis 연결 확인

## 이후 로그 작성 규칙

- 서비스 경계 변경 시 반드시 기록
- 파일/폴더 구조 변경 시 반드시 기록
- `Dockerfile`, `docker-compose.yaml` 변경 시 반드시 기록
- 환경변수 구조 변경 시 반드시 기록
- 장애/이슈/우회 처리도 반드시 기록
- "왜 그렇게 결정했는지" 를 결과만큼 중요하게 남긴다

## 메모

- 다음 세션에서 이 문서를 먼저 읽고 이어서 작업하면 된다.
- 이 문서는 단순 결과 기록이 아니라, 의사결정 문서이자 진행 상태 기준점으로 사용한다.
