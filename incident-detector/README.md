# incident-detector

이 디렉토리는 운영 이상 징후를 자동 탐지하는 Spring Boot 서비스입니다. Kafka로 유입된 결제/예매 이벤트를 읽고 규칙 기반으로 장애 후보를 만들며, 후속 분석 대상 데이터를 적재합니다.

## 이 서비스가 하는 일

- 결제 이벤트와 예매 이벤트 소비
- 중복 결제, 유령 주문, 미확정 결제, 좀비 홀드 탐지
- 상관관계 대기 데이터 관리
- 장애 엔티티와 상태 이력 저장
- 주기적인 탐지 스케줄링

## 처음 보면 좋은 파일

- `src/main/java/.../IncidentDetectorApplication.java`: 서비스 시작점
- `src/main/java/.../kafka/PaymentEventConsumer.java`: 결제 이벤트 소비
- `src/main/java/.../kafka/TicketingEventConsumer.java`: 예매 이벤트 소비
- `src/main/java/.../rules/`: 탐지 규칙 모음
- `src/main/java/.../incident/IncidentWriteService.java`: 장애 생성/저장 로직

## 디렉토리 구조

```text
incident-detector/
├── build.gradle
├── Dockerfile
├── src/
│   └── main/
│       ├── java/com/example/incident/detector/
│       │   ├── config/         Kafka, Redis 설정
│       │   ├── correlation/    상관관계 대기 데이터
│       │   ├── inbox/          탐지 입력 이벤트 저장
│       │   ├── incident/       장애 도메인과 저장 로직
│       │   ├── kafka/          이벤트 컨슈머와 메시지 모델
│       │   ├── rules/          탐지 규칙
│       │   ├── scheduler/      주기 검사 작업
│       │   └── zombie/         좀비 홀드 탐지 전용 로직
│       └── resources/
└── README.md
```

## 구조를 읽는 방법

- 어떤 장애를 잡는지 보려면 `rules/`
- 이벤트가 어떻게 들어오는지 보려면 `kafka/`
- 탐지 결과 저장 구조는 `incident/`
