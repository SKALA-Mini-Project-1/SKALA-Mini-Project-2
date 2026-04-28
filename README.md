# Ticketing Service

<!-- Redis 실행 방법 -->
cd infra
docker-compose up -d

## Harbor Push

Apple Silicon Mac 에서 일반 `docker build` 로 이미지를 만들면 `linux/arm64` 로 올라가서 AMD64 클러스터에서 실행되지 않을 수 있습니다.

Harbor 로 올릴 때는 아래처럼 `buildx` 로 Linux 이미지를 바로 push 하세요.

```bash
docker login amdp-registry.skala-ai.com
./scripts/push-harbor.sh
```

기본값:

- `REGISTRY=amdp-registry.skala-ai.com`
- `PROJECT=skala25a`
- `TAG=latest`
- `PLATFORM=linux/amd64`

예시:

```bash
TAG=v1 PLATFORM=linux/amd64 ./scripts/push-harbor.sh
```

로컬 compose 빌드도 기본적으로 AMD64 로 맞추기 위해 `docker-compose.yaml` 의 커스텀 서비스에 `platform: ${DOCKER_DEFAULT_PLATFORM:-linux/amd64}` 를 넣어두었습니다.
