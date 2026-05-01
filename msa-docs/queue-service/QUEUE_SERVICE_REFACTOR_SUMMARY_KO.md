# Queue Service 리팩토링 요약

작성일: `2026-05-01`

## 1. 서비스 의존 구조 정리

### 작업목록

- `queue-service`의 DB/JPA 직접 의존 축소
- 내부 검증 책임을 API 호출 중심으로 정리

### 작업 내용

- `queue-service`가 다른 서비스의 DB를 직접 조회하는 방향에서 벗어나도록 구조를 정리했다.
- 사용자 검증은 `user-auth-service`, 공연/회차 검증은 `concert-service` 내부 API 호출로 처리하도록 유지했다.

### 적용내용

- `QueueServiceApplication`에서 datasource/JPA auto-configuration 제외
- `application.properties`에서 datasource/JPA 설정 제거
- `QueueService`에서 Redis 및 내부 API 중심 흐름 유지

## 2. 대기열 키/런타임 처리 정리

### 작업목록

- 대기열/활성 좌석 키 생성 방식 통일
- 하드코딩된 좌석 수용량 설정 외부화

### 작업 내용

- 문자열 조합으로 만들던 Redis key를 공통 key generator 기반으로 정리했다.
- 코드에 박혀 있던 최대 좌석 수용량 값을 설정값으로 이동했다.

### 적용내용

- `QueueService`에서 `RedisKeyGenerator.queueKey`, `seatActiveKey` 사용
- `queue.runtime.max-seat-capacity` 설정 추가
- `QueueRuntimeProperties` 추가로 설정값 바인딩

## 3. 대기열 정리 스케줄러 안정화

### 작업목록

- stale queue member cleanup lock 보강
- active seat sync lock 보강

### 작업 내용

- 스케줄러가 여러 인스턴스에서 동시에 실행될 때 다른 인스턴스의 lock을 잘못 해제하지 않도록 owner token 기반 lock 처리로 보강했다.
- lock renew/release를 Redis script로 처리하도록 정리했다.

### 적용내용

- `QueueScheduler`에 owner token lock 적용
- `ActiveSeatCleanupScheduler`에 owner token lock 적용
- stale queue member 정리 및 active count 재계산 로직 유지

## 4. downstream 예외 처리 보강

### 작업목록

- 내부 API 호출 실패 응답 정리
- queue-service 전용 예외 응답 보강

### 작업 내용

- `user-auth-service`, `concert-service` 호출 실패를 일반 예외로 두지 않고, 대기열 서비스 기준의 downstream 예외로 정리했다.
- 외부에서 실패 원인을 조금 더 일관되게 볼 수 있게 예외 핸들러를 추가했다.

### 적용내용

- `DownstreamServiceException` 추가
- `QueueServiceExceptionHandler` 추가
- `UserAuthClient`, `ConcertServiceClient` 예외 처리 보강

## 5. 팬 점수 우선순위 확장 지점 분리

### 작업목록

- 팬 점수 로직을 정책 클래스로 분리
- 현재는 중립 정책으로 유지

### 작업 내용

- 팬 점수 우선순위 기능을 바로 제거하지 않고, 이후 보강할 수 있도록 정책 레이어를 따로 두었다.
- 현재는 대기열 순서를 깨지 않도록 중립값을 반환하는 임시 정책을 적용했다.

### 적용내용

- `QueuePriorityPolicy` 추가
- `QueuePriorityService`가 정책 클래스에 위임하도록 변경
- 현재 팬 점수 가중치는 `0L` 기준으로 동작

## 6. 테스트 및 검증 추가

### 작업목록

- Redis fallback 단위 테스트 추가
- 스케줄러 동작 시나리오 테스트 추가
- 대기열 입장/상태/이탈 흐름 테스트 추가

### 작업 내용

- Redis 장애 시 대기열 상태 조회와 entry token 소비가 어떻게 동작하는지 최소 단위 테스트를 추가했다.
- 스케줄러가 stale member만 제거하고 active seat count를 다시 계산하는지 검증 테스트를 추가했다.
- 대기열 입장, 상태 조회, 이탈의 기본 흐름과 schedule validation fallback 동작을 테스트로 고정했다.

### 적용내용

- `QueueServiceTest` 추가
- `QueueSchedulerTest` 추가
- `ActiveSeatCleanupSchedulerTest` 추가
- `MSA_DEPLOY_PREP_KO.md`에 queue-service 운영 환경변수 반영
- `queue-service` 테스트 실행으로 리팩토링 결과 검증
