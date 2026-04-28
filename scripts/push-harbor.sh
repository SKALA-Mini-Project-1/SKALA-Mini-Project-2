#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${REGISTRY:-amdp-registry.skala-ai.com}"
PROJECT="${PROJECT:-skala25a}"
TAG="${TAG:-latest}"
PLATFORM="${PLATFORM:-linux/amd64}"

services=(
  "team4-user-auth-service|.|user-auth-service/Dockerfile"
  "team4-concert-service|.|concert-service/Dockerfile"
  "team4-queue-service|.|queue-service/Dockerfile"
  "team4-ticketing-service|.|ticketing-service/Dockerfile"
  "team4-payment-service|.|payment-service/Dockerfile"
  "team4-frontend|./frontend|frontend/Dockerfile"
)

for service in "${services[@]}"; do
  IFS="|" read -r image_name build_context dockerfile_path <<< "${service}"
  full_image="${REGISTRY}/${PROJECT}/${image_name}:${TAG}"

  echo "==> Building and pushing ${full_image} for ${PLATFORM}"
  docker buildx build \
    --platform "${PLATFORM}" \
    --tag "${full_image}" \
    --file "${dockerfile_path}" \
    "${build_context}" \
    --push
done
