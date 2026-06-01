# 🚀 배포 런북 (Vercel + AWS EC2)

프론트 → Vercel / 백엔드 → AWS EC2(컨테이너). 브랜치: **main=배포, develop=개발**.
순서: **① 백엔드(EC2) → ② 프론트(Vercel)** (프론트가 가리킬 백엔드 도메인이 먼저 필요).

---

## 0. 사전 준비물
- AWS 계정 (프리티어 또는 크레딧)
- Docker Hub 계정 + Access Token
- DuckDNS 계정 (무료): https://www.duckdns.org
- Kakao Developers 앱 (지도 키): https://developers.kakao.com

---

## ① 백엔드 (AWS EC2)

### 1-1. EC2 생성
- 리전: 서울(ap-northeast-2), 타입: **t3.micro**, OS: Ubuntu 22.04
- 키페어(.pem) 생성/다운로드
- 보안그룹 인바운드: **22, 80, 443만** 오픈 (5432/6379/8080 비공개)

### 1-2. DuckDNS 도메인 연결
- duckdns.org 로그인 → 서브도메인 생성 (예: `carpool-api`)
- current ip = EC2 퍼블릭 IP 입력 → update

### 1-3. EC2 셋업 (SSH 접속 후)
```bash
ssh -i carpool-key.pem ubuntu@<EC2_IP>
# 셋업 스크립트 실행 (docker + swap + certbot 인증서)
curl -fsSL https://raw.githubusercontent.com/techeer-2026-teamC/carpool/main/deploy/ec2-setup.sh -o ec2-setup.sh
chmod +x ec2-setup.sh
./ec2-setup.sh carpool-api.duckdns.org
```

### 1-4. compose / nginx 배치
```bash
# 로컬에서 또는 git clone 으로 ~/carpool 에 배치
#   docker-compose.prod.yml
#   nginx/nginx.conf   ← server_name 과 인증서 경로를 본인 도메인으로 교체
```

### 1-5. GitHub Secrets 등록 (로컬에서)
`deploy/set-github-secrets.sh` 값 채운 뒤 실행. 등록 항목:
`DOCKER_USERNAME, DOCKER_PASSWORD, EC2_HOST, EC2_SSH_KEY, JWT_SECRET, CORS_ALLOWED_ORIGINS`

### 1-6. 배포 실행
- **자동**: `develop → main` PR 머지 → GitHub Actions가 이미지 빌드/푸시 → EC2 자동 배포
- **수동(최초 확인용)**: `cd ~/carpool && docker compose -f docker-compose.prod.yml up -d`
- ✅ 확인: `curl https://carpool-api.duckdns.org/actuator/health` → `{"status":"UP"}`

---

## ② 프론트 (Vercel)

### 2-1. repo 연결
- vercel.com → New Project → `carpool-front` import
- **Production Branch = main**, Framework = Vite (자동감지)

### 2-2. 환경변수 (Vercel → Settings → Environment Variables)
```
VITE_API_BASE=https://carpool-api.duckdns.org
VITE_WS_BASE=wss://carpool-api.duckdns.org
VITE_KAKAO_JS_KEY=<카카오 JS 키>
VITE_KAKAO_REST_API_KEY=<카카오 REST 키>
```

### 2-3. Kakao 도메인 등록
- Kakao Developers → 내 앱 → 플랫폼 → Web → 사이트 도메인에 Vercel 주소 추가
- (안 하면 지도가 안 뜸)

### 2-4. 배포
- main 에 머지(또는 import 시 자동) → Vercel 자동 빌드/배포
- ✅ 확인: Vercel URL 접속 → 로그인 / 지도 / 실시간 위치 동작

---

## ③ 배포 후 검증 체크리스트
- [ ] `https://<도메인>/actuator/health` → UP
- [ ] Vercel 페이지 로그인 성공 (네트워크 탭에서 refresh 쿠키 SameSite=None; Secure)
- [ ] 운행 페이지 WebSocket 연결 (콘솔 STOMP CONNECTED) + 마커 실시간
- [ ] Kakao 지도 렌더 정상
- [ ] develop push → 배포 안 됨 / main 머지 → 배포됨 확인

---

## 💰 비용 메모
- t3.micro 24h ≈ 월 $9.5 (프리티어면 $0). 안 쓸 땐 `stop` (초 단위 과금).
- ALB / NAT Gateway / 유휴 Elastic IP 금지 (과금 주범).
- Let's Encrypt 인증서 90일 유효 → 1개월 데모는 갱신 불필요.
