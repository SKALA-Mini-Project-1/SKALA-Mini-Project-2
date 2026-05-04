# user-auth-service

이 디렉토리는 사용자 인증과 계정 관련 기능을 담당하는 Spring Boot 서비스입니다. 회원가입, 로그인, 내 정보 조회/수정, 이메일 인증, 내부 서비스용 사용자 조회와 팬 점수 조회 기능이 들어 있습니다.

## 이 서비스가 하는 일

- 사용자 회원가입과 로그인
- JWT 기반 인증 처리
- 이메일 인증 코드 발송 및 검증
- 로그아웃과 내 정보 조회/수정
- 다른 서비스가 호출하는 내부 사용자/팬 점수 API 제공

## 처음 보면 좋은 파일

- `src/main/java/.../UserAuthServiceApplication.java`: 서비스 시작점
- `src/main/java/.../modules/users/UserController.java`: 외부 사용자 API
- `src/main/java/.../modules/users/InternalUserController.java`: 내부 서비스 API
- `src/main/java/.../modules/fanscore/controller/InternalFanScoreController.java`: 팬 점수 내부 API
- `src/main/java/.../global/config/SecurityConfig.java`: 인증/인가 정책

## 디렉토리 구조

```text
user-auth-service/
├── build.gradle
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/example/SKALA_Mini_Project_1/
│   │   │   ├── global/
│   │   │   │   ├── config/        보안 설정
│   │   │   │   ├── email/         메일 발송 설정과 서비스
│   │   │   │   └── util/          이메일 검증 유틸
│   │   │   └── modules/
│   │   │       ├── users/         사용자 도메인, 컨트롤러, 서비스, DTO
│   │   │       └── fanscore/      팬 점수 저장소와 내부 조회 기능
│   │   └── resources/
│   └── test/
└── README.md
```

## 구조를 읽는 방법

- 사용자 기능은 `modules/users/`부터 보면 됩니다.
- 다른 서비스 연동용 정보가 궁금하면 `modules/fanscore/`와 `Internal*Controller`를 보면 됩니다.
- 인증 문제를 확인할 때는 `global/config/`와 `shared-kernel`의 JWT 관련 코드를 함께 보는 편이 좋습니다.
