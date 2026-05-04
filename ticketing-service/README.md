# ticketing-service

이 디렉토리는 예매 도메인의 핵심 서비스입니다. 좌석 선점, 예약 생성, 예매 확정/취소, 팬 점수 반영, 이벤트 아웃박스, 정합성 보정까지 실제 예매 트랜잭션과 가장 가까운 책임을 가집니다.

## 이 서비스가 하는 일

- 좌석 진입, 선택, 홀드, 해제
- 예약 생성과 예약 상세 조회
- 결제 서비스가 호출하는 예약 확정/취소/만료 처리
- 팬 점수 동기화와 운영 메트릭 수집
- 이벤트 인박스/아웃박스 관리
- 정합성 점검과 재처리 작업

## 처음 보면 좋은 파일

- `src/main/java/.../TicketingServiceApplication.java`: 서비스 시작점
- `src/main/java/.../modules/seats/controller/SeatController.java`: 좌석 API
- `src/main/java/.../modules/bookings/controller/BookingController.java`: 예약 API
- `src/main/java/.../modules/finalization/service/TicketingFinalizationService.java`: 결제 후 확정/취소 처리
- `src/main/java/.../modules/reconciliation/service/ReconciliationTaskService.java`: 정합성 보정 작업

## 디렉토리 구조

```text
ticketing-service/
├── build.gradle
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/example/SKALA_Mini_Project_1/
│   │   │   ├── config/                  Kafka 토픽 설정
│   │   │   ├── global/config/           보안 설정
│   │   │   ├── integration/             concert, user-auth 연동
│   │   │   ├── kafka/                   결제 이벤트 소비 모델
│   │   │   └── modules/
│   │   │       ├── bookings/            예약 도메인
│   │   │       ├── events/              inbox/outbox 이벤트 관리
│   │   │       ├── fanscore/            팬 점수 동기화
│   │   │       ├── finalization/        예약 확정/취소/만료 내부 API
│   │   │       ├── reconciliation/      정합성 점검과 재처리
│   │   │       └── seats/               좌석 선점과 상태 관리
│   │   └── resources/
│   └── test/
└── README.md
```

## 구조를 읽는 방법

- 예매의 시작은 `modules/seats/`
- 예약 생성과 조회는 `modules/bookings/`
- 결제 이후 상태 전이는 `modules/finalization/`
- 이벤트 기반 운영 보강은 `modules/events/`, `modules/reconciliation/`
