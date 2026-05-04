# SKALA Mini Project 2

이 저장소는 공연 예매 서비스를 MSA 형태로 분리한 프로젝트입니다. 사용자 인증, 공연 조회, 대기열, 좌석 선점, 결제, 장애 탐지와 운영 분석까지 여러 서비스가 함께 동작합니다.

## 이 저장소에서 볼 수 있는 것

- `user-auth-service`: 회원가입, 로그인, JWT, 이메일 인증
- `concert-service`: 공연 목록, 회차, 좌석 맵 조회
- `queue-service`: 예매 대기열과 입장 제어
- `ticketing-service`: 좌석 선점, 예약, 예매 확정, 팬 점수, 정합성 보정
- `payment-service`: 결제 생성, 승인, 웹훅, 환불 필요 건 처리
- `incident-detector`: Kafka 이벤트를 읽어 운영 이상 징후 탐지
- `incident-agent`: 장애 데이터를 LLM으로 분석
- `incident-api`: 운영 화면에서 사용하는 장애 조회/조치 API
- `shared-kernel`: 여러 서비스가 공통으로 쓰는 JWT, Redis, 예외 처리
- `frontend`: 사용자 예매 화면과 운영 화면
- `infra`: 로컬 Postgres, Debezium 연결 설정
- `stress_test`: k6 기반 부하 테스트 스크립트
- `fairline_k8s`: 쿠버네티스 배포 리소스가 들어 있는 서브모듈

## 처음 볼 때 추천 순서

1. 이 `README.md`에서 전체 구성 파악
2. `docker-compose.yaml`에서 로컬 실행 대상 확인
3. 관심 있는 서비스 디렉토리의 `README.md` 확인
4. 프론트 흐름이 궁금하면 `frontend/`
5. 운영/배포 흐름이 궁금하면 `fairline_k8s/`, `infra/`, `stress_test/`

## 최상위 디렉토리 구조

```text
.
├── frontend/              프론트엔드
├── user-auth-service/     인증 서비스
├── concert-service/       공연 조회 서비스
├── queue-service/         대기열 서비스
├── ticketing-service/     예매 핵심 서비스
├── payment-service/       결제 서비스
├── incident-detector/     장애 탐지 서비스
├── incident-agent/        장애 분석 서비스
├── incident-api/          운영 API 서비스
├── shared-kernel/         공통 모듈
├── infra/                 로컬 인프라 설정
├── stress_test/           부하 테스트
├── docs/                  임시/보조 문서
├── msa-docs/              아키텍처 기록 문서
├── yewon/                 시각화·목업 자료
└── fairline_k8s/          쿠버네티스 배포 서브모듈
```

## 로컬 실행 기준

주 실행 파일은 `docker-compose.yaml`입니다.

- 인프라: `postgres`, `redis`, `kafka`, `kafka-connect`
- 애플리케이션: 모든 백엔드 서비스 + `frontend`

실행 예시:

```bash
docker compose up --build
```

## 참고

- 각 주요 디렉토리에는 별도의 `README.md`가 있습니다.
- `build/`, `dist/`, `node_modules/`는 생성 산출물이라 구조 파악 대상에서 제외해도 됩니다.
