#!/bin/bash
set -e

AWS_REGION=ap-northeast-2

# AWS 로그인 확인
echo "AWS 계정 확인 중..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null)
if [ -z "$ACCOUNT_ID" ]; then
  echo "AWS CLI 로그인이 필요합니다. 'aws configure' 또는 SSO 로그인 후 다시 실행하세요."
  exit 1
fi

REGISTRY=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
echo "Account: $ACCOUNT_ID"
echo "Registry: $REGISTRY"
echo ""

# ECR 로그인
echo "ECR 로그인 중..."
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $REGISTRY
echo ""

# 빌드 대상 선택
if [ "$1" == "all" ] || [ -z "$1" ]; then
  SERVICES="user-auth-service concert-service queue-service ticketing-service payment-service"
  BUILD_FRONTEND=true
else
  SERVICES=""
  BUILD_FRONTEND=false
  for ARG in "$@"; do
    if [ "$ARG" == "frontend" ]; then
      BUILD_FRONTEND=true
    else
      SERVICES="$SERVICES $ARG"
    fi
  done
fi

# Spring Boot 서비스 빌드 & Push
for SERVICE in $SERVICES; do
  echo "=========================================="
  echo "Building: $SERVICE"
  echo "=========================================="
  docker build --platform linux/amd64 \
    -t $REGISTRY/team4-${SERVICE}:latest \
    -f ${SERVICE}/Dockerfile .
  docker push $REGISTRY/team4-${SERVICE}:latest
  echo ""
done

# frontend 빌드 & Push
if [ "$BUILD_FRONTEND" = true ]; then
  echo "=========================================="
  echo "Building: frontend"
  echo "=========================================="
  docker build --no-cache --platform linux/amd64 \
    -t $REGISTRY/team4-frontend:latest \
    -f frontend/Dockerfile ./frontend
  docker push $REGISTRY/team4-frontend:latest
  echo ""
fi

echo "모든 이미지 push 완료!"
