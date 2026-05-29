<div align="center">

# 🚗 같이타 (Carpool) — Backend

**실시간 카풀 매칭 서비스의 백엔드 저장소**

목적지가 같은 사람들을 연결하고, 운행 중 위치를 실시간으로 공유합니다.

<br>

![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.4-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)

</div>

---

## 📖 목차

- [기술 스택](#-기술-스택)
- [주요 기능](#-주요-기능)
- [빠른 시작](#-빠른-시작-quick-start)
- [프로젝트 구조](#-프로젝트-구조)
- [API 개요](#-api-개요)
- [테스트](#-테스트)
- [부하 테스트 & 모니터링](#-부하-테스트--모니터링)

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| **Language** | Java 17 |
| **Framework** | Spring Boot 4.0.4, Spring Security 7 |
| **Database** | PostgreSQL 15 (운영) · H2 (테스트) |
| **Cache / PubSub** | Redis 7 |
| **Realtime** | WebSocket (STOMP) |
| **Auth** | JWT (JJWT 0.12.6) · BCrypt |
| **API Docs** | Swagger (SpringDoc) |
| **Infra** | Docker Compose |
| **Monitoring** | Prometheus · Grafana |
| **Load Test** | k6 |

---

## ✨ 주요 기능

- 🔐 **인증** — JWT 기반 로그인 / 회원가입, Refresh Token Rotation (HttpOnly 쿠키)
- 👤 **회원 · 드라이버** — 프로필 관리, 차량 등록, 평점 시스템
- 📝 **게시글** — 카풀 모집 CRUD, 태그 필터, 날짜/위치 기반 검색, 자동 마감 스케줄러
- 🙋 **신청** — 카풀 참여 신청 / 수락 / 거절, 동시성 제어(좌석 초과·중복 방지)
- 🚘 **운행** — 운행 시작/종료, 탑승/하차 확인, **WebSocket 실시간 위치 공유**
- ⭐ **리뷰** — 운행 완료 후 드라이버 평가
- 🔔 **알림** — Redis Pub/Sub 기반 SSE 실시간 알림

---

## 🚀 빠른 시작 (Quick Start)

### 1️⃣ 환경 변수 설정

루트에 `.env` 파일을 생성합니다. (`.env.sample` 참고)

```bash
cp .env.sample .env
```

```env
JWT_SECRET=local-dev-secret-key-please-change-in-production
DOCKER_USERNAME=local
# Google OAuth2 (소셜 로그인 사용 시 — 팀 단톡방 자격증명 참고)
GOOGLE_CLIENT_ID=your_google_client_id_here
GOOGLE_CLIENT_SECRET=your_google_client_secret_here
```

### 2️⃣ 실행

```bash
# DB · Redis · 앱 전체 기동
docker-compose up -d

# 모니터링 스택까지 함께 기동하려면
docker-compose --profile monitoring up -d
```

| 서비스 | 주소 |
|--------|------|
| 🌐 API | http://localhost:8080 |
| 📚 Swagger | http://localhost:8080/swagger-ui.html |
| 📊 Grafana | http://localhost:3000 (admin / admin) |
| 📈 Prometheus | http://localhost:9090 |

> **로컬 프로파일**에서 앱 실행 시 테스트 계정이 자동 생성됩니다.
> - 드라이버: `test@carpool.com` / `password1234`
> - 승객: `admin@carpool.com` / `admin1234!`

---

## 📂 프로젝트 구조

```
Backend/
├── backend/                    # Spring Boot 애플리케이션
│   ├── src/main/java/com/techeer/carpool/
│   │   ├── domain/             # 도메인별 패키지
│   │   │   ├── auth/           #   인증 (JWT, 로그인)
│   │   │   ├── member/         #   회원
│   │   │   ├── driver/         #   드라이버 · 차량
│   │   │   ├── post/           #   게시글 · 태그
│   │   │   ├── application/    #   카풀 신청
│   │   │   ├── ride/           #   운행 · 실시간 위치
│   │   │   ├── review/         #   리뷰 · 평점
│   │   │   └── notification/   #   알림 (Redis Pub/Sub + SSE)
│   │   └── global/             # 공통 (config, jwt, exception, metrics)
│   └── Dockerfile
├── k6/                         # 부하 테스트 시나리오
│   ├── scenarios/              #   01~07 시나리오
│   └── utils/                  #   auth · data · checks 헬퍼
├── grafana/                    # Grafana 프로비저닝
├── docker-compose.yml
├── prometheus.yml
└── .env.sample
```

---

## 🔌 API 개요

| 도메인 | 대표 엔드포인트 |
|--------|----------------|
| **Auth** | `POST /api/v1/auth/signup` · `login` · `refresh` · `logout` |
| **Member** | `GET /api/v1/members/me` · 프로필 수정 · 탈퇴 |
| **Driver** | `POST /api/v1/drivers` · `GET /drivers/me` · 차량 색상 목록 |
| **Post** | `GET·POST /api/v1/posts` · 단건 조회 · 수정 · 삭제 · 마감 |
| **Application** | `POST /api/v1/posts/{id}/applications` · 수락 / 거절 |
| **Ride** | `POST /api/v1/rides` · 시작 · 종료 · 탑승 · 하차 · 위치 |
| **Review** | `POST /api/v1/reviews/rides/{id}` · 드라이버 평점 |
| **WebSocket** | `/ws` · `/app/ride/{id}/location` · `/topic/ride/{id}` |

> 전체 명세는 [Swagger UI](http://localhost:8080/swagger-ui.html)에서 확인하세요.

---

## 🧪 테스트

```bash
cd backend
./gradlew test            # 전체 통합 테스트 (H2 인메모리)
./gradlew test --tests "com.techeer.carpool.domain.post.*"   # 특정 도메인
```

테스트 리포트: `backend/build/reports/tests/test/index.html`

---

## 📊 부하 테스트 & 모니터링

[k6](https://k6.io)로 시나리오별 부하 테스트를 수행합니다.

```bash
# 인증 흐름
k6 run k6/scenarios/01_auth_flow.js

# 운행 위치 부하 (SCALE 조정 가능)
k6 run -e SCALE=100 k6/scenarios/06_ride_location_load.js

# 🎬 운행 데모 (브라우저에서 실시간 위치 시각 확인)
k6 run k6/scenarios/07_ride_demo_scenario.js
```

### 🎬 운행 데모 시나리오 (`07_ride_demo_scenario.js`)

브라우저 2탭(드라이버 / 승객)을 열어두고 실행하면, **출발 전 집결 → 탑승 → 이동 → 하차** 전 과정을 지도에서 실시간으로 확인할 수 있습니다.

```
PHASE 1  위치 공유 시작     드라이버 + 승객 3명 마커 표시
PHASE 2  출발점 집결        승객들이 출발점으로 동시 이동
PHASE 3  운행 시작 + 탑승    한 명씩 탑승 확인
PHASE 4  목적지 이동         실제 도로 경로 따라 🚗 이동
PHASE 5  하차 + 종료         평가하기 버튼 노출
```

> 테스트 시 브라우저 콘솔에서 `localStorage.setItem('rideTestMode','1')` 후 새로고침하면 GPS 대신 시나리오 좌표가 사용됩니다.

---

<div align="center">

**Techeer 2026 Team-C**

</div>
