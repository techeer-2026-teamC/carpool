# 운영 배포 상태

현재 모아의 운영 배포 경로는 지원하지 않습니다. `Java CI with Gradle`은 PR, main push, 수동 실행(`workflow_dispatch`)에서 빌드·테스트와 의존성 제출을 수행합니다. 배포 입력, 이미지 업로드, EC2 SSH 작업은 제거했습니다.

## 기존 구성을 사용할 수 없는 이유

- `docker-compose.prod.yml`은 이전 서비스의 참고 구성입니다. `postgres:15`에 V4 마이그레이션이 요구하는 PostGIS 확장이 없고, Outbox와 모집 예약 작업을 처리하는 worker도 없습니다.
- `docker-compose.moa.yml`은 개발·기능 검증용입니다. 로컬 자격 증명과 공개 개발 포트를 포함하므로 운영 배포 파일로 대체하지 않습니다.
- 현재 CI 성공이나 로컬 API 두 개의 기동 성공은 운영 데이터 전환·외부 접속·장애 복구 검증을 의미하지 않습니다.

## 배포를 다시 추가하기 전 필요한 작업

1. **데이터베이스 전환:** 운영 PostgreSQL에 맞는 PostGIS 설치 경로와 권한을 정합니다. 기존 스키마·데이터를 확인해 Flyway baseline과 이후 마이그레이션을 적용하고, 백업 복원까지 별도 검증합니다. 기존 운영 DB에 자동 baseline을 켜지 않습니다.
2. **프로세스 구성:** API와 worker의 `APP_ROLE`, DB·Redis 연결, JWT/CORS 설정을 운영 환경에 맞게 구성합니다. Outbox와 모집 예약 작업이 worker에서 실행되는지 확인합니다.
3. **접속·관측:** TLS, SSE/WebSocket 전달, 외부 actuator 차단과 내부 Prometheus 수집을 검증합니다. 개발 포트·기본 비밀번호를 운영 설정으로 사용하지 않습니다.
4. **배포·복구 절차:** 검토한 커밋에 연결된 이미지, 상태 확인, 실패 시 이전 버전 복구와 DB 마이그레이션의 되돌리기 한계를 정합니다. 운영 설정·배포 workflow를 별도 PR로 리뷰합니다.
5. **운영 환경 검증:** 빈 DB와 기존 데이터 전환을 각각 검증하고, API 간 알림 전달·worker 재시도·Redis 장애 복구를 확인합니다. DAU 10만 처리량은 별도 부하 테스트 계획의 검증 대상입니다.

이 변경에서는 실제 배포나 부하 테스트를 실행하지 않습니다. 위 조건을 충족한 운영 구성과 검증 근거가 준비된 뒤 배포 workflow를 다시 추가합니다.

## CI 디렉터리 설정

빌드 명령은 `working-directory: ./backend`에서 실행합니다. `setup-gradle@v4`에는 `build-root-directory` 입력이 없으므로 제거하고, 해당 입력을 지원하는 `dependency-submission@v4`에는 유지했습니다. [공식 setup-gradle 정의](https://github.com/gradle/actions/blob/v4/setup-gradle/action.yml) · [공식 dependency-submission 정의](https://github.com/gradle/actions/blob/v4/dependency-submission/action.yml).
