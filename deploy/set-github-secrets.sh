#!/usr/bin/env bash
# =============================================================
# GitHub Secrets 일괄 등록 스크립트
# 사용: 로컬에서 값을 채운 뒤 실행 (gh CLI 로그인 필요)
#   chmod +x set-github-secrets.sh && ./set-github-secrets.sh
# =============================================================
set -euo pipefail

REPO="techeer-2026-teamC/carpool"

# ↓↓↓ 실제 값으로 교체하세요 ↓↓↓
DOCKER_USERNAME="여기에_도커허브_아이디"
DOCKER_PASSWORD="여기에_도커허브_액세스토큰"     # https://hub.docker.com/settings/security
EC2_HOST="여기에_EC2_퍼블릭IP_또는_도메인"
JWT_SECRET="여기에_32자이상_랜덤시크릿"           # 예: openssl rand -base64 48
CORS_ALLOWED_ORIGINS="https://*.vercel.app"      # 프론트 도메인 확정 시 좁혀도 됨
EC2_SSH_KEY_PATH="$HOME/Downloads/carpool-key.pem" # EC2 키페어 .pem 경로
# ↑↑↑ 실제 값으로 교체하세요 ↑↑↑

echo "==> $REPO 에 Secrets 등록"
gh secret set DOCKER_USERNAME      -R "$REPO" --body "$DOCKER_USERNAME"
gh secret set DOCKER_PASSWORD      -R "$REPO" --body "$DOCKER_PASSWORD"
gh secret set EC2_HOST             -R "$REPO" --body "$EC2_HOST"
gh secret set JWT_SECRET           -R "$REPO" --body "$JWT_SECRET"
gh secret set CORS_ALLOWED_ORIGINS -R "$REPO" --body "$CORS_ALLOWED_ORIGINS"
gh secret set EC2_SSH_KEY          -R "$REPO" < "$EC2_SSH_KEY_PATH"

echo "==> 완료. 확인:"
gh secret list -R "$REPO"
