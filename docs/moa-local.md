# 모아 로컬 실행

## 구성

API 두 개와 워커 한 개가 같은 PostgreSQL/PostGIS와 Redis를 사용합니다.
nginx는 API 요청을 분산하고 SSE와 만남 WebSocket 연결을 전달합니다.
워커는 모집 예약 작업과 Outbox 발행을 담당하며 업무 API 접근을 차단합니다.

## 시작

Docker Compose와 JDK 17을 사용합니다. 저장소 루트에서 실행합니다.

```sh
docker compose -f docker-compose.moa.yml up -d --build
```

- 서비스: http://localhost:18080 (API 직접 접근은 18081, 18082)
- PostgreSQL: localhost:15432 / DB·계정 moa
- Redis: localhost:16379
- Compose의 계정과 비밀번호는 로컬 검증용입니다.
- DB 마이그레이션은 빈 전용 데이터베이스에서 시작합니다. 기존 운영 DB에 baseline을 자동 적용하지 않습니다.
- Flyway V1–V8을 적용합니다. V8은 회원당 활성 운전자 하나를 보장하며 기존 중복이 있으면 데이터를 지우지 않고 중단합니다. [사전 점검·정리 절차](migrations/V8-active-driver.md)를 확인합니다.
- JVM과 각 DB 연결은 Asia/Seoul을 사용합니다.

## 모니터링

다음 명령으로 관측 구성을 함께 시작합니다.

```sh
docker compose -f docker-compose.moa.yml --profile monitoring up -d --build
```

Prometheus는 localhost:19090, Grafana는 localhost:13000입니다.
API·워커의 관리 포트 9091은 Docker 네트워크 내부에서 개별 수집합니다.
nginx는 actuator 경로를 외부 서비스 주소에 노출하지 않습니다.
기존 DB 볼륨에서는 pg_stat_statements preload 설정으로 재시작한 뒤 extension을 별도 생성합니다.

## 기능 검증

```sh
MOA_POSTGIS_IMAGE=moa-postgis:15 REDIS_PORT=16379 ./backend/gradlew -p backend test
```

공간 검색 테스트용 이미지가 없다면 먼저 Compose의 DB 이미지를 빌드합니다.
테스트는 별도 PostgreSQL/Redis 컨테이너를 사용합니다. 기존 인증·API 테스트에는 위 로컬 Redis가 필요합니다.
부하 생성은 이 절차에 포함하지 않습니다. DAU 10만 처리 성능을 검증한 구성도 아닙니다.

```sh
docker compose -f docker-compose.moa.yml --profile monitoring down
```

이 명령은 컨테이너를 중지하며 DB·Redis·관측 데이터 볼륨을 보존합니다.

## 만남 위치 공유 세션

- 공유를 켤 때 `POST /api/v1/posts/{postId}/meeting/locations/me`를 호출해 `data.generation` UUID를 받습니다.
- `/app/meetings/{postId}/location` STOMP 메시지는 `{latitude, longitude, generation}`을 보냅니다. generation 없는 기존 클라이언트는 사용할 수 없으므로 프론트와 함께 반영합니다.
- 공유 중지는 `DELETE /api/v1/posts/{postId}/meeting/locations/me?generation={UUID}`입니다. 이전 세션의 늦은 중지 요청은 새 세션에 영향을 주지 않습니다.
- Redis Lua가 세션 비교·4초 제한·좌표 저장·발행을 원자적으로 처리합니다. 중지 이후 도착한 이전 전송은 저장·발행하지 않습니다. 좌표는 최대 60초, 세션은 공유 가능 시간까지 보관합니다.
- 공유 시작과 참가 취소는 같은 DB 모집 행 잠금 아래 Redis 세션을 생성·폐기합니다. 빈도가 낮은 이 두 경로에는 Redis I/O가 포함되며 연결·명령 timeout은 각각 1초입니다. 위치 전송마다 DB 잠금을 잡지는 않습니다.
- 취소 중 Redis 폐기에 실패하면 DB 취소도 롤백합니다. Redis 폐기 후 DB가 롤백하면 공유만 중지되므로 다시 켜야 합니다. 참가 취소·재승인 후에는 새 generation이 필요합니다.
- 시작 응답 전에 화면을 닫거나 중지한 프론트는 늦게 반환된 generation도 정리해야 합니다. 이미 전송된 메시지를 회수하지는 않으며, 수신 API는 기존대로 현재 좌표와 참가 권한을 재확인합니다.
