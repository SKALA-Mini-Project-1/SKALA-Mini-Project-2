# concert-service

이 디렉토리는 공연 정보와 좌석 배치 조회를 담당하는 Spring Boot 서비스입니다. 예매 자체를 처리하지 않고, 공연 목록·회차·좌석 맵 같은 조회성 데이터를 제공하는 역할에 집중합니다.

## 이 서비스가 하는 일

- 공연 목록과 상세 정보 조회
- 회차별 공연 일정 조회
- 좌석 맵과 구역별 좌석 현황 조회
- 다른 서비스가 사용하는 내부 공연 조회 API 제공

## 처음 보면 좋은 파일

- `src/main/java/.../ConcertServiceApplication.java`: 서비스 시작점
- `src/main/java/.../modules/concerts/controller/ConcertController.java`: 공연 조회 API
- `src/main/java/.../modules/seats/controller/ConcertSeatController.java`: 좌석 맵 API
- `src/main/java/.../modules/concerts/service/ConcertQueryService.java`: 공연 조회 비즈니스 로직
- `src/main/java/.../modules/seats/service/SeatMapService.java`: 좌석 맵 조합 로직

## 디렉토리 구조

```text
concert-service/
├── build.gradle
├── Dockerfile
├── src/
│   └── main/
│       ├── java/com/example/SKALA_Mini_Project_1/
│       │   ├── global/config/     보안, Redis 캐시 설정
│       │   └── modules/
│       │       ├── concerts/      공연 조회 컨트롤러, DTO, 리포지토리, 서비스
│       │       └── seats/         좌석 도메인, 조회 DTO, 리포지토리, 서비스
│       └── resources/
└── README.md
```

## 구조를 읽는 방법

- 공연 목록/상세 흐름은 `modules/concerts/`
- 좌석 구성과 상태 조회는 `modules/seats/`
- 캐시나 인증 예외가 걸릴 때는 `global/config/`
