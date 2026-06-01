#!/usr/bin/env bash
# =============================================================
# EC2 최초 1회 셋업 스크립트 (Ubuntu 기준)
# 사용: EC2에 SSH 접속 후
#   curl -fsSL <raw url>/deploy/ec2-setup.sh -o ec2-setup.sh
#   chmod +x ec2-setup.sh && ./ec2-setup.sh <duckdns-도메인>
# 예) ./ec2-setup.sh carpool-api.duckdns.org
# =============================================================
set -euo pipefail

DOMAIN="${1:-}"
if [ -z "$DOMAIN" ]; then
  echo "사용법: ./ec2-setup.sh <duckdns-도메인>  (예: carpool-api.duckdns.org)"
  exit 1
fi

echo "==> 1) 시스템 업데이트"
sudo apt-get update -y

echo "==> 2) swap 2GB 생성 (t3.micro 1GB RAM 대응)"
if ! sudo swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  echo "   swap 2GB 활성화 완료"
else
  echo "   swap 이미 존재 — 건너뜀"
fi

echo "==> 3) Docker 설치"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
  echo "   Docker 설치 완료 (그룹 적용 위해 재로그인 필요할 수 있음)"
else
  echo "   Docker 이미 설치됨 — 건너뜀"
fi

echo "==> 4) certbot 설치 + Let's Encrypt 인증서 발급 (standalone)"
echo "   ※ 사전조건: DuckDNS에서 $DOMAIN 이 이 EC2의 퍼블릭 IP를 가리켜야 함"
echo "   ※ 보안그룹 80 포트 열려 있어야 함"
sudo apt-get install -y certbot
if [ ! -d "/etc/letsencrypt/live/$DOMAIN" ]; then
  sudo certbot certonly --standalone -d "$DOMAIN" --non-interactive --agree-tos --register-unsafely-without-email
  echo "   인증서 발급 완료 (90일 유효 — 1개월 데모는 갱신 불필요)"
else
  echo "   인증서 이미 존재 — 건너뜀"
fi

echo "==> 5) ~/carpool 디렉터리 준비"
mkdir -p ~/carpool/nginx/certbot-www
echo "   다음 파일을 ~/carpool 에 올리세요:"
echo "     - docker-compose.prod.yml"
echo "     - nginx/nginx.conf  (server_name 을 $DOMAIN 으로 교체)"

echo ""
echo "===================================================="
echo " EC2 셋업 완료. 남은 단계:"
echo " 1) docker-compose.prod.yml, nginx/nginx.conf 를 ~/carpool 에 배치"
echo " 2) nginx.conf 의 server_name / 인증서 경로를 $DOMAIN 으로 교체"
echo " 3) GitHub Secrets 등록 후 main 머지 → 자동 배포"
echo "    또는 수동: cd ~/carpool && docker compose -f docker-compose.prod.yml up -d"
echo "===================================================="
