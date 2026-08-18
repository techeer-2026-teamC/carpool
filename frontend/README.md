<div align="center">

# 🗺️ 같이타 (Carpool) — Frontend

**실시간 카풀 매칭 서비스의 프론트엔드**

카풀 게시글을 지도에서 탐색하고, 운행 중 드라이버·승객 위치를 실시간으로 확인합니다.

<br>

![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-5-646CFF?style=flat-square&logo=vite&logoColor=white)
![Kakao Map](https://img.shields.io/badge/Kakao%20Map-SDK-FFCD00?style=flat-square&logo=kakao&logoColor=black)
![STOMP](https://img.shields.io/badge/STOMP-WebSocket-010101?style=flat-square&logo=socketdotio&logoColor=white)

</div>

---

## 📖 목차

- [기술 스택](#-기술-스택)
- [주요 기능](#-주요-기능)
- [빠른 시작](#-빠른-시작)
- [프로젝트 구조](#-프로젝트-구조)
- [실시간 운행 데모](#-실시간-운행-데모)

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| **Framework** | React 18 + Vite 5 |
| **Routing** | React Router DOM |
| **지도** | Kakao Map SDK · Leaflet (react-leaflet) |
| **실시간** | STOMP over WebSocket (`@stomp/stompjs`) |
| **스타일** | 인라인 스타일 + CSS 변수 (외부 UI 라이브러리 미사용) |

---

## ✨ 주요 기능

- 🔐 **로그인 / 회원가입** — JWT 토큰 기반 인증
- 📋 **카풀 목록** — 카드뷰 / 지도뷰 토글, 필터(전체·오늘·자리있음·저렴순), 태그·검색
- 🗺️ **지도 보기** — 카카오맵 위에 게시글 마커 + **실제 도로 경로** 표시 (OSRM)
- 📝 **게시글 등록** — 출발지/목적지 키워드 검색으로 좌표 자동 입력
- 🙋 **참여 신청** — 상세 모달에서 카풀 신청 / 마감
- 🚘 **실시간 운행** — WebSocket으로 드라이버·승객 위치 실시간 지도 표시
- 👤 **프로필 · 드라이버 등록 · 리뷰**

---

## 🚀 빠른 시작

### 1️⃣ 의존성 설치

```bash
npm install
```

### 2️⃣ 환경변수 설정

`.env.example`을 복사해 `.env`를 만들고 카카오 API 키를 입력합니다.

```bash
cp .env.example .env
```

```env
VITE_KAKAO_JS_KEY=발급받은_JavaScript_키
VITE_KAKAO_REST_API_KEY=발급받은_REST_API_키
```

> 카카오 API 키는 [Kakao Developers](https://developers.kakao.com)에서 발급받습니다.
> 앱 생성 후 **플랫폼 → Web**에 `http://localhost:5173`을 등록하세요.

### 3️⃣ 실행

```bash
npm run dev       # 개발 서버 (http://localhost:5173) — 백엔드(8080) 프록시 연결
npm run mock      # 백엔드 없이 Mock API로 단독 실행
npm run build     # 프로덕션 빌드
npm run preview   # 빌드 결과 미리보기
```

> `npm run dev`는 `/api`, `/ws` 요청을 `localhost:8080`(백엔드)로 프록시합니다.
> 백엔드 없이 UI만 보려면 `npm run mock`을 사용하세요.

---

## 📂 프로젝트 구조

```
Front/
├── src/
│   ├── App.jsx              # 최상위 — 라우팅 · 상태 허브
│   ├── api/                 # 백엔드 API 클라이언트 (도메인별)
│   ├── components/
│   │   ├── Nav · Hero · LoginPage
│   │   ├── SearchSection    # 검색 + 태그 필터
│   │   ├── CarpoolCard      # 카풀 카드
│   │   ├── MapView          # 카카오맵 뷰 (마커 + 도로 경로)
│   │   ├── DetailModal      # 상세 + 참여
│   │   ├── PostModal        # 게시글 등록
│   │   ├── RidePage         # 실시간 운행 (WebSocket 지도)
│   │   ├── ProfilePage · ReviewModal · Toast
│   ├── hooks/
│   │   └── useCarpool.js    # 전역 상태 + 액션 커스텀 훅
│   └── data/tags.js
├── vite.config.js
└── vite.mocks.js            # Mock API 플러그인
```

---

## 🎬 실시간 운행 데모

`RidePage`는 STOMP WebSocket으로 위치를 주고받아 지도에 실시간 표시합니다.

| 구분 | 위치 출처 |
|------|----------|
| **실제 서비스** | 브라우저 GPS (`navigator.geolocation`) |
| **테스트(k6)** | 시나리오 하드코딩 좌표 |

테스트 시 브라우저 콘솔에서 아래 실행 후 새로고침하면 GPS 대신 시나리오 좌표를 사용합니다.

```js
localStorage.setItem('rideTestMode', '1')
```

> 백엔드 저장소의 `k6/scenarios/07_ride_demo_scenario.js`와 함께 실행하면
> 드라이버 🚗 와 승객 🧍 마커가 지도 위에서 실시간으로 움직이는 것을 볼 수 있습니다.

---

<div align="center">

**Techeer 2026 Team-C**

</div>
