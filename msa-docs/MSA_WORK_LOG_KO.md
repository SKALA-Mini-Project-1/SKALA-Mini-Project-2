# MSA 작업 전용 로그

작성일: `2026-04-30`
작업 브랜치 기준 문서

## 문서 목적

- 이번 브랜치에서 진행하는 MSA 정리 작업만 추적한다.
- 다른 프롬프트/세션에서도 이 문서 하나로 현재 상태와 다음 작업을 이어갈 수 있게 한다.
- 특히 `backend`, `gateway`, `shared-kernel`, 서비스 간 의존성 제거, 최종 배포 준비 상태를 함께 기록한다.

## 이번 작업의 최종 목표

아래 5개 백엔드 서비스를 실제 독립 마이크로서비스 단위로 정리한다.

- `user-auth-service`
- `concert-service`
- `queue-service`
- `ticketing-service`
- `payment-service`

최종적으로는:

1. 각 서비스가 독립 실행/독립 빌드 가능한 상태가 된다.
2. `backend`와 `gateway` 없이도 서비스 구조를 설명할 수 있어야 한다.
3. Kubernetes에서 각 서비스를 개별 Pod/Deployment로 띄울 수 있어야 한다.
4. 이후 ECR 업로드와 k8s 배포 작업으로 자연스럽게 넘어갈 수 있어야 한다.

주의:

- 이번 단계의 1차 목표는 **ECR 업로드가 아니라 서비스 분리 정리**다.
- `backend`, `gateway` 삭제는 **최종 단계**에 한다.
- 지금은 먼저 **의존성 제거와 연결 해제**를 한다.

## 현재 합의 사항

### 1. `backend`

- 최종적으로 제거한다.
- 단, 바로 삭제하지 않는다.
- 먼저 `backend`를 참조하는 개발/런타임 경로를 모두 끊어낸 뒤 마지막에 삭제한다.

### 2. `gateway`

- k8s 최종 구조에서는 Ingress가 외부 진입점을 담당하므로 최종적으로 제거 가능하다.
- 단, 지금은 바로 삭제하지 않는다.
- 먼저 `gateway`가 맡고 있는 역할과 참조를 정리하고, 서비스별 직접 배포 기준으로 문서와 경로를 정리한 뒤 마지막에 삭제한다.

### 3. `shared-kernel`

- 이번 단계에서는 유지한다.
- 이유:
  - JWT 관련 공통 코드
  - Redis 설정/키 생성/락 처리
  - 공통 CORS/예외 처리/Swagger 설정
- 즉, 지금 제거 대상이 아니라 현재 서비스들이 공통으로 의존하는 인프라 모듈로 본다.

## 이번 작업의 원칙

- 실행 경로를 먼저 바꾸고, 삭제는 나중에 한다.
- 서비스 경계는 새로 뒤집지 않는다.
- 현재 이미 분리된 서비스 경계를 기준으로 운영 가능한 구조로 정리한다.
- `payment-service`와 `ticketing-service` 경계는 이후 Kafka 고도화가 가능하도록 유지한다.
- 지금 단계에서는 HTTP 기반 내부 통신을 유지하되, DB 직접 침범은 막는 방향을 우선한다.

## 작업 순서

### Phase 1. 작업 로그와 기준 문서 고정

- 이번 문서 생성
- 현재 서비스 역할/통신 문서와 함께 참조 기준 정리

### Phase 2. `backend` 의존성 제거

- 프론트엔드 dev proxy
- 구형 실행 스크립트
- `infra/nginx.conf`
- 문서/스크립트 중 `backend` 기본 전제 제거

목표:

- 더 이상 기본 실행 경로가 `backend`를 바라보지 않게 한다.

### Phase 3. `gateway` 의존성 제거

- 현재 `gateway`가 맡은 라우팅 역할을 정리
- k8s 최종 구조에서 Ingress로 대체되는 전제 반영
- 로컬/문서/구성에서 `gateway` 필수 전제를 없앤다

목표:

- 서비스별 배포 구조를 `gateway` 없이 설명 가능하게 만든다.

### Phase 4. 서비스별 독립성 점검

- 각 서비스가 독립 빌드 가능한지 확인
- 서비스 간 내부 호출 주소/환경변수 정리
- k8s 배포 시 필요한 환경값, 내부 API token, readiness/liveness 관점 점검

목표:

- 각 서비스를 Deployment 단위로 다룰 수 있는 상태로 만든다.

### Phase 5. 최종 삭제

- `backend` 삭제
- `gateway` 삭제
- 구형 스크립트/문서/설정 정리

목표:

- 레포에서 실질적인 백엔드 런타임 단위를 5개 서비스로만 남긴다.

### Phase 6. k8s 전환 준비 완료

- 서비스별 독립 MS 구조 정리 완료
- 이후 ECR 업로드 및 k8s 매니페스트 작업으로 이어갈 수 있는 상태 정리

## 예상 산출물

이번 작업이 끝나면 아래 결과물을 기대한다.

1. `backend` 참조가 제거된 서비스 구조
2. `gateway` 없이도 설명 가능한 최종 배포 기준 구조
3. `shared-kernel` 유지 사유와 현재 역할 정리
4. 서비스별 독립 실행/독립 빌드 상태
5. k8s 배포 전제의 정리된 서비스 구조
6. 이후 ECR 업로드 작업으로 연결 가능한 기준 문서

## 관련 문서

- 기존 전체 MSA 이관 기록: [MSA_MIGRATION_LOG_KO.md](./MSA_MIGRATION_LOG_KO.md)
- 현재 서비스 역할/통신 정리: [MSA_SERVICE_MAP_KO.md](./MSA_SERVICE_MAP_KO.md)
- 현재 배포 준비 정리: [MSA_DEPLOY_PREP_KO.md](./MSA_DEPLOY_PREP_KO.md)

## 체크리스트

- [x] 작업 전용 로그 문서 생성
- [x] `backend` 참조 경로 전체 식별
- [x] `backend` 의존성 제거
- [x] `gateway` 참조 경로 전체 식별
- [x] `gateway` 의존성 제거
- [x] 서비스별 독립 실행 경로 정리
- [x] 최종 `backend` 삭제
- [x] 최종 `gateway` 삭제
- [x] 최종 빌드/구조 검증
- [ ] 후속 k8s/ECR 작업으로 넘길 상태 정리

## 작업 로그

### 2026-04-30

- 이번 브랜치용 MSA 작업 전용 로그 문서를 생성했다.
- 이번 단계의 우선순위를 `삭제`가 아니라 `의존성 제거 및 연결 해제`로 확정했다.
- `backend`, `gateway`는 최종 단계에서 삭제하기로 합의했다.
- `shared-kernel`은 이번 단계에서 유지하기로 정리했다.
- 최종 목표를 "5개 백엔드 서비스의 독립 MS 구조 정리"로 고정했다.
- `backend`를 직접 참조하던 주요 런타임/개발 경로를 식별했다.
  - `frontend/vite.config.ts`
  - `frontend/README.md`
  - `run-all.sh`
  - `infra/nginx.conf`
  - `payment-service`의 webhook 검증 스크립트 기본값
- 1차 정리로 위 파일들의 기본 실행 경로를 `backend`가 아닌 현재 MSA 진입 경로 기준으로 바꾸기 시작했다.
- `gateway`가 실제로 필수인 지점을 식별했다.
  - `docker-compose.yaml`의 `frontend -> gateway` 의존
  - `frontend`의 단일 API 진입 경로 가정
  - `gateway/nginx.conf` 기반 prefix 라우팅
- 프론트엔드 Vite 프록시를 서비스별 직접 프록시 구조로 바꾸기 시작했다.
  - `gateway`를 거치지 않고 `/api/users`, `/api/concerts`, `/api/ticketing`, `/api/seats`, `/api/bookings`, `/api/payments`를 각 서비스로 직접 프록시하도록 전환
  - `docker-compose.yaml`의 frontend 환경변수도 서비스별 직접 타깃으로 변경
- 현재 기준으로 `backend`는 더 이상 기본 실행 경로가 아니다.
  - 남아 있는 `backend` 언급은 주로 과거 기록 문서와 보존 중인 원본 소스 폴더다.
- 현재 기준으로 `frontend`는 더 이상 `gateway`를 필수 전제로 삼지 않는다.
  - `gateway`는 삭제 전까지 선택적 진입점으로 남겨둔다.
- `docker-compose.yaml`에서 `gateway`를 기본 기동 대상이 아닌 `legacy-gateway` profile로 분리했다.
  - 기본 compose 실행 경로는 이제 5개 백엔드 서비스 + frontend 중심이다.
- 서비스별 독립 실행 경로 점검을 위해 배포 준비 정리 문서를 추가했다.
  - `MSA_DEPLOY_PREP_KO.md`
  - Ingress 기준 외부 경로
  - 서비스 간 내부 HTTP 호출
  - 서비스별 필수 환경변수
- 현재 기준으로 5개 백엔드 서비스 모두 `Dockerfile`을 가지고 있다.
  - `user-auth-service/Dockerfile`
  - `concert-service/Dockerfile`
  - `queue-service/Dockerfile`
  - `ticketing-service/Dockerfile`
  - `payment-service/Dockerfile`
- 현재 기준으로 5개 백엔드 서비스 모두 `server.port=8080` 과 actuator health probe 설정을 갖는다.
  - `management.endpoint.health.probes.enabled=true`
  - `management.health.livenessstate.enabled=true`
  - `management.health.readinessstate.enabled=true`
- 현재 기준으로 서비스 간 내부 호출용 base URL / internal API token 환경변수도 compose와 application 설정에 반영되어 있다.
  - `queue-service -> user-auth-service`, `concert-service`
  - `ticketing-service -> user-auth-service`
  - `payment-service -> ticketing-service`
- 따라서 현재 단계의 핵심 미완료 항목은 "서비스 경계 재설계"가 아니라 "최종 삭제 전 남은 레거시 경로 정리"와 "k8s 매니페스트로 옮기기 좋은 상태 정리"다.
- 현재 남아 있는 `backend`, `gateway` 언급의 대부분은 과거 기록 또는 보존 문서다.
  - `MSA_MIGRATION_LOG_KO.md`
  - `yewon/docs/*`
  - `payment-service/.../paper/README.md`
  - `infra/nginx.conf`
  - `docker-compose.yaml` 내 `legacy-gateway` profile
- 즉, 지금 남은 레거시 흔적은 "기본 실행 경로"보다는 "최종 삭제 전 정리 대상 목록"에 가깝다.
- 기본 구성 검증 결과:
  - `docker compose config --services` 기준 기본 서비스 목록은 `postgres`, `redis`, 5개 백엔드 서비스, `frontend`로 확인됐다.
  - 기본 compose 경로에 `gateway`는 포함되지 않았다.
  - `./gradlew build -x test` 는 현재 상태에서 다시 성공했다.
- Kubernetes 배포 관련 초안은 별도 정리 대상으로 남겨두고, 이번 단계의 핵심 범위는 5개 서비스 구조 정리와 실행 경로 정리에 둔다.
- `k8s/base/secret.yaml` 은 로컬 개발용 시크릿 초안으로만 두고 Git 추적에서는 제외한다.
- 최종 정리 단계에서 아래 레거시 경로를 제거했다.
  - `backend/`
  - `gateway/`
  - `infra/docker-compose.yml`
  - `infra/nginx.conf`
  - `run-all.sh`
  - `scripts/push-harbor.sh`
  - 루트 `package.json`, `package-lock.json`
- `docker-compose.yaml` 에서 `gateway` 서비스 블록도 제거했다.
- 현재 기본 실행 경로는 `postgres`, `redis`, 5개 백엔드 서비스, `frontend` 만 남는다.
- 최종 검증 결과:
  - `docker compose config --services` 기준 `gateway` 없이 `postgres`, `redis`, 5개 백엔드 서비스, `frontend`만 남았다.
  - `./gradlew build -x test` 가 삭제 이후에도 다시 성공했다.

## 다음 작업

1. 최종 빌드와 compose 구성을 다시 검증한다.
2. ECR 업로드용 env 체계와 스크립트 기준을 정리한다.
3. 다른 저장소나 배포 경로에서 관리할 k8s/YAML 입력값을 공유한다.
