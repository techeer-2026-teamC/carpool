<div align="center">

# 🌿 모아 — Backend

**카풀·택시 동승의 검색 → 신청·승인 → 만남을 연결하는 서버**

[프로젝트 소개](https://github.com/techeer-2026-teamC) · [Notion 기술 문서](https://www.notion.so/3dc226545d1581feae7fe91dbd0c68dd) · [프론트엔드](https://github.com/techeer-2026-teamC/carpool-front)

Java 17 · Spring Boot 4 · PostgreSQL 15/PostGIS · Redis 7 · Prometheus/Grafana

</div>

## 핵심 기능

- **모집·매칭:** 카풀과 택시 동승, 출발지·목적지 반경/시간 검색, 신청·승인·거절·취소
- **정원 보호:** PostgreSQL 행 락과 DB 제약, 회원 탈퇴와 상태 변경의 잠금 순서
- **실시간 알림:** 업무 변경·알림·Outbox의 원자적 저장, 워커 발행, Redis Pub/Sub, API별 SSE
- **만남:** 참가자 만남 확인·불참·완료, 동의한 참가자의 제한된 위치 공유
- **택시 분담:** 실제 탑승자별 금액과 외부 수금 기록·변경 이력. 앱 내 결제·송금 없음
- **관측:** API 2개·워커 1개와 PostgreSQL·Redis 지표 수집

카풀은 차량 등록을 검사하고 화면에서 평일 출퇴근을 안내합니다. 요일·시간·공휴일 서버 검증과 운전자 면허·보험 검증은 현재 구현 범위 밖입니다. 법적 운영 조건은 기술 문서에서 별도로 구분합니다.

## 실행

Docker Compose와 Node.js 22를 준비합니다. 백엔드 테스트에는 JDK 17이 필요합니다.

```bash
docker compose -f docker-compose.moa.yml --profile monitoring up -d --build
```

| 서비스 | 로컬 주소 |
| --- | --- |
| API gateway | http://localhost:18080 |
| API 1 / API 2 | localhost:18081 / localhost:18082 |
| Swagger UI | http://localhost:18080/swagger-ui/index.html |
| OpenAPI | http://localhost:18080/v3/api-docs |
| PostgreSQL / Redis | localhost:15432 / localhost:16379 |
| Prometheus | http://localhost:19090 |
| Grafana | http://localhost:13000 |

Compose의 비밀번호·JWT 값은 로컬 예제입니다. 전용 빈 DB에서 Flyway를 시작하며 기존 운영 DB에 자동 baseline하지 않습니다. JVM·DB 연결 시간대는 Asia/Seoul입니다.

프론트는 [저장소의 `main`](https://github.com/techeer-2026-teamC/carpool-front)에서 `npm ci && npm run dev`로 실행합니다. CORS origin은 `http://localhost:5173`입니다. 신규 UI는 구형 mock API·자동 생성 테스트 계정을 전제로 하지 않습니다.

[상세 실행·중지·테스트 안내](docs/moa-local.md)

## 코드 탐색

| 도메인 | 소유하는 책임 |
| --- | --- |
| `auth`, `member`, `driver` | 인증·회원·차량 등록 |
| `post` | 모집 상태·정원·태그·예약 마감·예정 목록 캐시 |
| `discovery` | PostGIS 조건 검색과 커서 페이지 |
| `application` | 신청 상태 전이와 정원 변경 |
| `notification` | DB 알림함·Outbox·워커·Pub/Sub·SSE 수명 |
| `meeting` | 만남 확인·완료, 별도 위치 동의·조회·STOMP |
| `expense` | 택시 분담금과 외부 수금·감사 기록 |
| `ride`, `review`, `comment` | 보존된 운행·평가·댓글 기능 |
| `global` | 인증 필터·설정·오류 응답·업무 지표 |

새 흐름은 검색과 참가 확정이 중심입니다. 레거시 운행은 `app.legacy-ride.enabled=false`로 기본 비활성화했습니다. 설정을 켜는 것만으로 구형 WebSocket의 인증·라우팅까지 복구되는 것은 아닙니다. 만남 위치 STOMP는 독립 모듈로 유지합니다.

## 기술 선택과 한계

- **PostGIS:** 공간 조건과 모집 상태·시간·잔여석을 같은 DB에서 검사합니다. Redis GEO도 공간 검색을 지원하지만 별도 인덱스 동기화가 필요하므로 이번 기본 검색에는 추가하지 않았습니다.
- **Redis 캐시:** 예정 모집 목록만 5분 TTL로 공유합니다. 신규 discovery 검색에는 응답 캐시가 없습니다. 승인·취소·자동 마감 반영은 목록 TTL까지 늦을 수 있으며, 좌석 확정은 DB를 다시 검사합니다.
- **DB 락:** API 프로세스가 여러 개여도 공유 DB 행을 잠가 정원 상태를 직렬화합니다. 낙관적 락·조건부 UPDATE·Redis 분산 락과의 성능 순위는 측정하지 않았습니다.
- **알림 복구:** Pub/Sub 누락은 DB 알림함 조회로 보정하고, Outbox 재발행 중복은 알림 ID로 구분합니다. 발행 성공은 브라우저 수신 완료가 아닙니다.
- **SSE 수명:** 완료·오류·타임아웃 시 보관소 참조와 대기 큐·할당량을 한 번만 정리합니다. 프로세스별 연결·전송 큐 상한을 둡니다.
- **운영 범위:** API 2개 구성은 확인했지만 PostgreSQL·Redis·gateway는 단일 노드입니다. DAU 10만 처리량과 전체 고가용성을 검증한 배포 구성이 아닙니다.

## 검증

```bash
MOA_POSTGIS_IMAGE=moa-postgis:15 REDIS_PORT=16379 ./backend/gradlew -p backend test
```

DB 이미지는 위 Compose 빌드로 준비합니다. 테스트는 별도 PostgreSQL/PostGIS·Redis 컨테이너를 사용하고, 기존 인증 테스트는 로컬 Redis도 필요합니다.

### 2026-09-21 — 수정 검증과 main 반영

- 백엔드 **186개 테스트 통과**, 실패·오류·skip 0. PostgreSQL/PostGIS·Redis 통합 검증과 `build`·`bootJar` 생성 성공
- 수정 PR #162–#169를 `main@a334837`에 병합했습니다. 전체 파일 트리가 통합 검증본 `verify/moa-review-fixes@0ad6cac`와 동일함을 확인했습니다.
- [main@a334837 CI](https://github.com/techeer-2026-teamC/carpool/actions/runs/35563997538)의 빌드·의존성 제출이 성공했습니다. 현재 workflow에는 배포 작업이 없습니다.
- 프로필·탈퇴 경합, Refresh 토큰 원자적 교체, 위치 공유 세션, 활성 운전자 유일성, 모집 수정 계약과 인증 실패 처리를 검증했습니다.
- 같은 코드 트리에서 실제 HTTP/STOMP 연동 테스트 **1개 통과**: HTTP 요청 27건으로 TAXI 모집·승인과 위치 UUID 시작·전송·중지, 늦은 전송 및 이전 UUID의 삭제 차단을 확인했습니다. 전용 서버·DB·Redis는 정리했습니다.
- 프론트 기능 PR #8–#19와 CI PR #20도 `main@08bf67b`에 병합했고 [최종 main CI](https://github.com/techeer-2026-teamC/carpool-front/actions/runs/35564576077)가 성공했습니다. 테스트 **49개 통과**·빌드 성공을 확인한 코드 트리와 같으며, 아래 브라우저·관측 검증을 이번에 다시 실행한 것은 아닙니다.

### 2026-09-15 — 이전 기능·관측 검증 이력

- 당시 백엔드 **159개 테스트 통과**, API1/API2/gateway의 SSE에서 동일 알림 ID 수신과 DB 알림함 대조
- Prometheus **6개 타깃 UP**, Grafana 모아 개요 대시보드 **10개 패널**과 datasource 응답 확인
- 워커 업무 API 403, gateway actuator 404 확인
- [당시 main CI](https://github.com/techeer-2026-teamC/carpool/actions/runs/34928324351) 성공. 배포는 실행하지 않음

[실행·관측·검증 상세](https://www.notion.so/3dc226545d1581d09941c0fb137e1423)에 확인한 커밋·범위와 미검증 조건을 기록했습니다. 현재 GitHub Actions는 수동 실행을 포함해 빌드·테스트·의존성 검증만 수행합니다. **모아의 운영 배포는 지원하지 않으며**, [배포 재개 조건](docs/deployment-status.md)을 먼저 충족해야 합니다.

## 문서

| 읽는 목적 | 문서 |
| --- | --- |
| JWT 검증과 Redis 인증 상태 | [인증 처리 순서·claims 캐시 제거 근거](docs/jwt-authentication.md) |
| 활성 운전자 유일성 | [V8 사전 점검·마이그레이션](docs/migrations/V8-active-driver.md) |
| 전체 문서 탐색 | [Notion 백엔드 문서](https://www.notion.so/3dc226545d1581feae7fe91dbd0c68dd) |
| 도메인과 코드 책임 | [도메인 지도](https://www.notion.so/3dc226545d1581d8884be3d69a27fbd0) · [핵심 객체·함수](https://www.notion.so/3dc226545d1581f48898f6a88cad1e85) |
| API 요청·응답·오류 | [공통·인증](https://www.notion.so/3dc226545d1581a4beeffdcf1a01f969) · [모집·신청](https://www.notion.so/3dc226545d1581ee98fffb1c4f67fdfd) · [알림·만남·분담](https://www.notion.so/3dc226545d1581d78a5cf37bc41dcfeb) · [레거시](https://www.notion.so/3dc226545d15812e8c05d811f2779bc6) |
| 테이블·제약·인덱스 | [ERD와 데이터 사전](https://www.notion.so/3dc226545d15811c984cd7cd09d107de) |
| 프로세스와 관측 연결 | [Figma 시스템 아키텍처](https://www.figma.com/board/TRGHDXwe8ThtE5dQviUPCG) |
| 요청부터 알림 복구까지 | [서버 동작 흐름](https://www.notion.so/3dc226545d158187adedef9de21d6696) |
| 분산 락을 추가하지 않은 이유 | [정원 동시성과 Redis 락의 고려사항](https://www.notion.so/3dc226545d1581bba794f3df8255c9ba) |
| PostGIS와 Redis GEO 비교 | [공간 검색 설계](https://www.notion.so/3dc226545d15816ca2fdd5ba33d57d00) |
| SSE와 Pub/Sub 복구 범위 | [실시간 알림 설계](https://www.notion.so/3dc226545d158107a86efde7804574c8) |
| 향후 측정 조건 | [새 부하 테스트 실행 계획](https://www.notion.so/3dc226545d1581ddbd30ff7479185476) |
| 운영 전 법적 검토 | [현재 구현과 운영 조건](https://www.notion.so/3e2226545d1581278778ec9a770bc75f) · [법령·타사 참고](https://www.notion.so/3e2226545d1581c8be9ff377bf83028a) |
| 이력서에 사용할 근거 | [기술 경험 3가지와 증거의 범위](https://www.notion.so/3e2226545d15819d806ecdde0673529e) |

**이번 작업에서는 부하 테스트를 실행하지 않았습니다.** 기존 `k6/` 실험 스크립트는 현재 모아 기능과 자동으로 호환되는 실행 안내가 아닙니다. 이력서의 과거 SSE 실험과 신규 구현의 기능 검증을 구분합니다.
