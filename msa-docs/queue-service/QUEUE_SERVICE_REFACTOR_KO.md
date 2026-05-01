# Queue Service 대기열 리팩토링 및 기능 보강

작성일: `2026-05-01`

## 목적

- `queue-service`를 Redis 중심의 대기열 서비스로 정리한다.
- 다른 서비스의 DB를 직접 조회하지 않고, 필요한 검증은 내부 API 호출로 처리한다.
- 이후 대기열 기능 보강과 팬 점수 우선순위 정책 확장을 위한 작업 기준 문서로 사용한다.

## 현재 보강 범위

- `queue-service`의 datasource/JPA 직접 의존 제거 방향 반영
- `user-auth-service`, `concert-service` 내부 API 호출 기반 검증 유지
- 대기열 정리 스케줄러의 Redis lock 보강
- 대기열 코드 정리 이후 추가 기능 보강 포인트 정리

## 체크리스트

- [x] `dev` 최신 변경을 `dev-jihyun`에 반영하고 충돌 정리
- [x] `queue-service`에서 datasource/JPA 자동 설정 제외
- [x] `queue-service`에서 Postgres 직접 설정 제거
- [x] `QueueService`가 `RedisKeyGenerator` 기반 키 생성 사용
- [x] `UserAuthClient`, `ConcertServiceClient`에 downstream 예외 처리 보강
- [x] `QueueScheduler`에 owner token 기반 Redis lock 적용
- [x] 미사용 `ConcertClient.java` 정리
- [x] 대기열 최대 수용 인원(`MAX_SEAT_CAPACITY`) 설정 외부화
- [x] Redis fallback 관련 최소 단위 테스트 추가
- [ ] 대기열 입장/상태 조회/이탈 흐름 통합 테스트 보강
- [ ] `QueueScheduler`, `ActiveSeatCleanupScheduler` 동작 시나리오 점검
- [ ] `QueueService`의 Redis 장애 fallback 동작 범위 재검토
- [ ] `queue-service` 관련 운영/배포 문서 업데이트
- [ ] 팬 점수 정책 연동 방식 확정 후 대기열 우선순위 반영

## 작업 대상 파일

- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueueService.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueueService.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueueScheduler.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueueScheduler.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/ActiveSeatCleanupScheduler.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/ActiveSeatCleanupScheduler.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/controller/QueueController.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/controller/QueueController.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/integration/userauth/UserAuthClient.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/integration/userauth/UserAuthClient.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/integration/concert/ConcertServiceClient.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/integration/concert/ConcertServiceClient.java)
- [queue-service/src/main/resources/application.properties](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/resources/application.properties)
- [queue-service/build.gradle](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/build.gradle)
- [queue-service/Dockerfile](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/Dockerfile)
- [docker-compose.yaml](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/docker-compose.yaml)

## 작업 로그

- 2026-05-01: Synced `dev-jihyun` with `dev` and resolved queue-service merge conflicts on top of the latest shared branch state.
- 2026-05-01: Removed direct datasource/JPA startup dependency from queue-service and kept Redis plus internal API validation as the main runtime path.
- 2026-05-01: Updated `QueueScheduler` to use owner-token Redis locking so cleanup does not release another worker's lock by mistake.
- 2026-05-01: Simplified queue-service container packaging to copy a prebuilt jar instead of building inside the Docker image.
- 2026-05-01: Left fan-score priority as a separate policy hook so queue ordering can be extended without pulling database access back into queue-service.
- 2026-05-01: Externalized queue admission capacity with `queue.runtime.max-seat-capacity` so seat capacity can be tuned without code edits.
- 2026-05-01: Added focused queue-service tests for Redis fallback and configured admission-capacity usage.
