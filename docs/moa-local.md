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
- JVM과 각 DB 연결은 Asia/Seoul을 사용합니다.

## 모니터링

관측 구성 PR 적용 후 다음 명령으로 함께 시작합니다.

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
