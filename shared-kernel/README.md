# shared-kernel

이 디렉토리는 여러 백엔드 서비스가 공통으로 사용하는 코드 모듈입니다. 단독 서비스가 아니라 라이브러리 성격의 Gradle 모듈이며, JWT, Redis, 공통 예외 응답, CORS, Swagger 설정 같은 반복 코드를 모아 둡니다.

## 이 모듈이 하는 일

- JWT 생성과 검증
- Redis 연동 설정과 공통 저장소 제공
- 공통 예외 응답 형식 제공
- CORS, Swagger 같은 공통 설정 제공

## 처음 보면 좋은 파일

- `src/main/java/.../global/jwt/JwtUtil.java`: JWT 유틸
- `src/main/java/.../global/jwt/JwtAuthenticationFilter.java`: 인증 필터
- `src/main/java/.../global/config/RedisConfig.java`: Redis 설정
- `src/main/java/.../global/redis/`: Redis 기반 공통 저장소
- `src/main/java/.../common/GlobalExceptionHandler.java`: 공통 예외 응답

## 디렉토리 구조

```text
shared-kernel/
├── build.gradle
├── src/
│   ├── main/
│   │   └── java/com/example/SKALA_Mini_Project_1/
│   │       ├── common/         CORS, 예외 응답, 전역 예외 처리
│   │       └── global/
│   │           ├── config/     Redis, Swagger 설정
│   │           ├── jwt/        JWT 필터와 유틸
│   │           └── redis/      이메일 인증, 락, 토큰 블랙리스트, 대기열 저장소
│   └── test/
└── README.md
```

## 구조를 읽는 방법

- 인증 공통 코드는 `global/jwt/`
- Redis 관련 재사용 로직은 `global/redis/`
- 각 서비스의 `SecurityConfig`는 이 모듈을 기반으로 조립됩니다.
