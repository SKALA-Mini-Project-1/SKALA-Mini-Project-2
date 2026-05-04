# incident-api

이 디렉토리는 운영자가 장애 현황을 조회하고 상태를 바꾸는 API를 제공하는 Spring Boot 서비스입니다. 프론트의 운영 화면에서 호출하는 백엔드라고 보면 됩니다.

## 이 서비스가 하는 일

- 장애 목록 조회
- 장애 상세 조회
- 분석 버전 조회
- 장애 상태 전이 처리
- 재분석 요청 처리

## 처음 보면 좋은 파일

- `src/main/java/.../IncidentApiApplication.java`: 서비스 시작점
- `src/main/java/.../controller/IncidentController.java`: 운영 API 엔드포인트
- `src/main/java/.../service/IncidentQueryService.java`: 조회 로직
- `src/main/java/.../service/IncidentCommandService.java`: 상태 변경/재분석 로직
- `src/main/java/.../dto/`: 프론트 응답 모델

## 디렉토리 구조

```text
incident-api/
├── build.gradle
├── Dockerfile
├── src/
│   └── main/
│       ├── java/com/example/incident/api/
│       │   ├── config/         JPA 설정
│       │   ├── controller/     장애 운영 API, 예외 처리
│       │   ├── domain/         장애와 분석 버전 엔티티
│       │   ├── dto/            응답/요청 모델
│       │   └── service/        조회와 명령 처리
│       └── resources/
└── README.md
```

## 구조를 읽는 방법

- 프론트가 어떤 API를 쓰는지 보려면 `controller/`
- 읽기 전용 기능은 `IncidentQueryService`
- 상태 변경이나 재분석 요청은 `IncidentCommandService`
