# payment-service

이 디렉토리는 결제 생성과 승인, PG 연동, 웹훅 처리, 환불 필요 건 관리까지 담당하는 Spring Boot 서비스입니다. 예매 예약과 직접 연결되어 있으며 Toss Payments와의 통신도 이 서비스가 맡습니다.

## 이 서비스가 하는 일

- 결제 생성, 조회, 승인, 취소
- Toss Payments confirm/cancel API 연동
- 웹훅 수신과 멱등 처리
- 만료 결제 스케줄링
- 환불 필요 건 조회와 요청 처리
- 결제 이벤트 발행과 운영 요약 API 제공

## 처음 보면 좋은 파일

- `src/main/java/.../PaymentServiceApplication.java`: 서비스 시작점
- `src/main/java/.../modules/payments/controller/PaymentController.java`: 사용자 결제 API
- `src/main/java/.../modules/payments/controller/PaymentOpsController.java`: 운영 API
- `src/main/java/.../modules/payments/service/PaymentService.java`: 핵심 결제 로직
- `src/main/java/.../modules/payments/scheduler/PaymentScheduler.java`: 만료 결제 처리

## 디렉토리 구조

```text
payment-service/
├── build.gradle
├── Dockerfile
├── src/
│   └── main/
│       ├── java/com/example/SKALA_Mini_Project_1/
│       │   ├── config/                    HTTP, Kafka 설정
│       │   ├── global/config/             보안 설정
│       │   └── modules/payments/
│       │       ├── controller/            외부/운영 API와 DTO
│       │       ├── domain/                Payment, Refund, PaymentEvent
│       │       ├── exception/             결제 예외
│       │       ├── integration/           Toss, ticketing 연동 클라이언트
│       │       ├── kafka/                 결제 이벤트 메시지
│       │       ├── paper/                 검증용 스크립트와 메모 자산
│       │       ├── repository/            결제/환불 저장소
│       │       ├── scheduler/             만료 스캔 작업
│       │       └── service/               핵심 결제 처리
│       └── resources/
└── README.md
```

## 구조를 읽는 방법

- 사용자 결제 플로우는 `controller/`와 `service/`
- 외부 PG 연동은 `integration/toss/`
- 예약 확정/취소 연계는 `integration/ticketing/`
- 운영 이슈 추적은 `domain/PaymentEvent`, `controller/PaymentOpsController`
