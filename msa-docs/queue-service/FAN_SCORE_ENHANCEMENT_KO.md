# Queue Service 팬 점수 기능 보강

작성일: `2026-05-01`

## 목적

- 대기열 우선순위에 반영할 팬 점수 정책을 `queue-service` 관점에서 다시 정리한다.
- 현재 임시 상태인 `QueuePriorityPolicy`를 실제 정책 구현 지점으로 확장할 수 있게 준비한다.
- DB 직접 접근을 최소화하면서도 팬 점수 기반 우선순위를 안정적으로 계산하는 방향을 정한다.

## 현재 상태

- 현재 `QueuePriorityService`는 `QueuePriorityPolicy`에 위임한다.
- 현재 `QueuePriorityPolicy`는 `concert-service`와 `user-auth-service`의 `internal API`를 사용한다.
- `artistId`와 `Fan Score` 조회에 성공하면 실제 `boostMillis`를 계산한다.
- downstream 호출 실패 시에는 중립값 `0L`로 fallback 한다.

## 체크리스트

- [x] `QueuePriorityService`가 정책 클래스로 위임하도록 구조 정리
- [x] `QueuePriorityPolicy` 임시 구현 추가
- [x] 팬 점수 데이터의 최종 소스 결정
- [x] 내부 API 호출 기반으로 점수 조회할지 여부 결정
- [x] 아티스트 기준 점수 계산 규칙 명문화
- [x] 점수 상한선과 음수/이상치 처리 규칙 확정
- [ ] 캐시 사용 여부와 TTL 정의
- [x] downstream 장애 시 우선순위 fallback 정책 정의
- [x] `QueuePriorityPolicy` 테스트 추가
- [ ] 팬 점수 정책 변경 시 문서와 운영 가이드 업데이트

## 작업 대상 파일

- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueuePriorityService.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueuePriorityService.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueuePriorityPolicy.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueuePriorityPolicy.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueueService.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/waiting/service/QueueService.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/integration/concert/ConcertServiceClient.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/integration/concert/ConcertServiceClient.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/fanscore/UserArtistFanScore.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/fanscore/UserArtistFanScore.java)
- [queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/fanscore/UserArtistFanScoreRepository.java](/Users/jihyunpark/Desktop/SKALA-Mini-Project-2/queue-service/src/main/java/com/example/SKALA_Mini_Project_1/modules/fanscore/UserArtistFanScoreRepository.java)

## 작업 메모

- 팬 점수 기능은 현재 `queue-service` 영역에서 우선순위 정책으로 남아 있지만, 장기적으로는 점수 저장소와 조회 책임을 어디에 둘지 다시 정해야 한다.
- 현재 구조상 `QueuePriorityPolicy`는 정책 변경 지점으로 적합하고, `QueuePriorityService`는 서비스 레벨 어댑터로 유지하는 편이 자연스럽다.
- 이후 구현 시에는 `queue-service`가 다시 JPA/DB 직접 의존으로 돌아가지 않도록 주의한다.
- 현재 대기열 기본 흐름, 스케줄러 점검, Redis fallback 테스트 위에 `Fan Score` lookup을 연결했다.
- 현재 구현 기준 `totalScore`는 그대로 `boostMillis`로 사용하고, 최종 상한은 `QueuePriorityService.MAX_QUEUE_PRIORITY_BOOST_MILLIS`에서 제한한다.

## 작업 로그

- 2026-05-01: Replaced direct fan-score repository usage in `QueuePriorityService` with a policy-based delegation layer.
- 2026-05-01: Added a neutral placeholder implementation in `QueuePriorityPolicy` so queue ordering stays stable while the final scoring contract is undecided.
- 2026-05-01: Preserved the queue priority extension point without forcing queue-service to own fan-score persistence again.
- 2026-05-02: Connected `QueuePriorityPolicy` to `concert-service` artist lookup and `user-auth-service` artist fan score lookup.
- 2026-05-02: Added neutral fallback behavior for missing artist data and downstream failures.
- 2026-05-02: Added `QueuePriorityPolicyTest` to verify score mapping and fallback behavior.
