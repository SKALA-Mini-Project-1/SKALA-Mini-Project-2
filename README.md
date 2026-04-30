# SKALA Mini Project 2

현재 레포는 5개 백엔드 서비스를 멀티모듈로 분리한 상태를 기준으로 관리합니다.

- `user-auth-service`
- `concert-service`
- `queue-service`
- `ticketing-service`
- `payment-service`
- `shared-kernel`
- `frontend`

## 로컬 실행

```bash
docker compose up --build
```

기본 compose 경로는 아래를 함께 띄웁니다.

- `postgres`
- `redis`
- 5개 백엔드 서비스
- `frontend`

## 현재 기준

- `backend` 레거시 모놀리스는 제거했습니다.
- `gateway` 레거시 프록시는 제거했습니다.
- 외부 진입 분기는 프론트의 서비스별 직접 프록시와 이후 Ingress 기준을 전제로 합니다.
- `shared-kernel` 은 배포 대상이 아니라 공통 빌드 모듈입니다.

## 참고 문서

- [MSA_WORK_LOG_KO.md](./msa-docs/MSA_WORK_LOG_KO.md)
- [MSA_SERVICE_MAP_KO.md](./msa-docs/MSA_SERVICE_MAP_KO.md)
- [MSA_DEPLOY_PREP_KO.md](./msa-docs/MSA_DEPLOY_PREP_KO.md)
