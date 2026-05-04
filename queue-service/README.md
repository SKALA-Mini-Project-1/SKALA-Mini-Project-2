# queue-service

이 디렉토리는 예매 대기열을 관리하는 Spring Boot 서비스입니다. 공연 오픈 시점에 사용자를 순서대로 입장시키고, 예매 가능 상태를 판정하는 역할을 합니다.

## 이 서비스가 하는 일

- 대기열 입장과 이탈 처리
- 현재 순번과 대기 상태 조회
- 예매 가능 시점에 입장 토큰 발급
- 팬 점수 기반 우선순위 정책 반영
- Redis 기반 대기열 운영과 정리 스케줄링

## 처음 보면 좋은 파일

- `src/main/java/.../QueueServiceApplication.java`: 서비스 시작점
- `src/main/java/.../modules/waiting/controller/QueueController.java`: 대기열 API
- `src/main/java/.../modules/waiting/service/QueueService.java`: 핵심 대기열 처리
- `src/main/java/.../modules/waiting/service/QueueScheduler.java`: 대기열 승급 스케줄러
- `src/main/java/.../modules/waiting/service/QueuePriorityService.java`: 우선순위 계산

## 디렉토리 구조

```text
queue-service/
├── build.gradle
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/example/SKALA_Mini_Project_1/
│   │   │   ├── global/config/         보안 설정
│   │   │   ├── integration/           user-auth, concert 서비스 연동 클라이언트
│   │   │   └── modules/
│   │   │       ├── fanscore/          팬 점수 조회용 저장소
│   │   │       └── waiting/
│   │   │           ├── config/        런타임 설정
│   │   │           ├── controller/    대기열 API
│   │   │           ├── dto/           요청/응답 모델
│   │   │           ├── exception/     예외 처리
│   │   │           ├── observability/ 메트릭
│   │   │           └── service/       대기열 로직, 스케줄러, 정리 작업
│   │   └── resources/
│   └── test/
└── README.md
```

## 구조를 읽는 방법

- 실제 예매 대기 로직은 `modules/waiting/service/`
- 외부 서비스와 주고받는 데이터는 `integration/`
- 모니터링 포인트는 `modules/waiting/observability/`
