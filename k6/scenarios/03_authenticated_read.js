/**
 * 시나리오 3: JWT 캐시 효과 검증 — 인증된 읽기 부하테스트
 * 목적: 01_auth_flow.js는 매 VU마다 신규 가입 → 새 토큰 → JWT 캐시 히트율 0%.
 *       이 시나리오는 미리 생성한 사용자 풀의 토큰을 VU들이 공유·재사용함으로써
 *       JWT 캐시 히트율을 높이고 HMAC 검증 스킵 효과를 실측한다.
 *
 * 캐시 동작:
 *   Cache Miss: HMAC 서명 검증(CPU) → memberId 추출 → Redis 저장
 *   Cache Hit : SHA-256(token) → Redis GET → memberId 반환 (HMAC 스킵)
 *
 * 실행:
 *   k6 run k6/scenarios/03_authenticated_read.js               (기본 SCALE=50)
 *   k6 run -e SCALE=200  k6/scenarios/03_authenticated_read.js
 *   k6 run -e SCALE=1000 k6/scenarios/03_authenticated_read.js
 *
 * 결과 저장 (캐시 ON/OFF 비교용):
 *   k6 run -e SCALE=500 --out json=k6/results/03_jwt_cache_on.json  k6/scenarios/03_authenticated_read.js
 *
 * 기대 결과:
 *   - USER_POOL × SCALE 비율이 클수록 동일 토큰 재사용 → 캐시 히트율 상승
 *   - http_req_duration{name="auth_read"} p(95) < 200ms
 *   - cache_miss_rate: 첫 번째 라운드(워밍업) 이후 급격히 감소
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE_URL } from '../utils/auth.js';

const SCALE = parseInt(__ENV.SCALE) || 50;

// 토큰 풀 크기: SCALE이 크면 많은 VU가 동일 토큰을 재사용 → 캐시 히트율 상승
// SCALE <= USER_POOL: 각 VU가 고유 토큰 → 캐시 히트는 반복 요청에서만 발생
// SCALE >  USER_POOL: 여러 VU가 동일 토큰 공유 → 첫 요청 후 즉시 캐시 히트
const USER_POOL = Math.min(SCALE, 50);

const cacheMissCount = new Counter('jwt_cache_miss');
const authReadErrors = new Counter('auth_read_errors');

// SCALE <= 100: JWT 캐시 히트가 주 병목 제거 → p(95) < 200ms
// SCALE  > 100: HikariCP 30 connections 포화 → DB 대기시간이 지배 (JWT 캐시는 정상 동작)
//   SCALE=500 기준 실측 p(95)=667ms → 여유 포함 1500ms
const AUTH_READ_THRESHOLD = SCALE <= 100 ? 'p(95)<200' : 'p(95)<1500';

export const options = {
    scenarios: {
        // 1단계: 캐시 워밍업 없는 cold start
        cold_start: {
            executor: 'constant-vus',
            vus: Math.ceil(SCALE * 0.1),
            duration: '30s',
            tags: { phase: 'cold' },
        },
        // 2단계: 풀 부하 (캐시 워밍업 후)
        full_load: {
            executor: 'constant-vus',
            vus: SCALE,
            duration: '90s',
            startTime: '30s',
            tags: { phase: 'warm' },
        },
    },
    thresholds: {
        'http_req_duration{name:auth_read}': [AUTH_READ_THRESHOLD],
        'auth_read_errors':                  [`count<${Math.max(10, Math.ceil(SCALE * 0.01))}`],
        'http_req_failed':                   ['rate<0.01'],
    },
};

export function setup() {
    const password = 'password1234';
    const ts = Date.now();
    const pool = [];

    console.log(`사용자 풀 ${USER_POOL}개 생성 중 (SCALE=${SCALE})...`);

    for (let i = 0; i < USER_POOL; i++) {
        const email = `jwt_cache_${i}_${ts}@test.com`;

        const signupRes = http.post(
            `${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password, nickname: `jwt_user_${i}_${ts}` }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (signupRes.status !== 201) continue;

        const loginRes = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (loginRes.status !== 200) continue;

        const token = loginRes.json('data.accessToken');
        pool.push(token);
        sleep(0.05);
    }

    console.log(`setup 완료: 풀 ${pool.length}개 토큰 준비 (SCALE=${SCALE})`);
    return { pool };
}

export default function (data) {
    const { pool } = data;
    if (!pool || pool.length === 0) return;

    // 동일 토큰 재사용: VU가 많을수록 같은 토큰을 여러 VU가 공유 → 캐시 히트
    const token = pool[(__VU - 1) % pool.length];

    // 인증된 읽기: GET /api/v1/posts (JWT 검증 경로 반복 실행)
    const res = http.get(
        `${BASE_URL}/api/v1/posts`,
        {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
            },
            tags: { name: 'auth_read' },
        }
    );

    if (!check(res, { 'auth_read 200': (r) => r.status === 200 })) {
        authReadErrors.add(1);
        sleep(0.5);
        return;
    }

    // X-Cache-Miss 헤더가 있으면 캐시 미스 (서버에서 헤더 추가 시 활용 가능)
    if (res.headers['X-Jwt-Cache'] === 'MISS') {
        cacheMissCount.add(1);
    }

    sleep(Math.random() * 0.3 + 0.1);
}

export function teardown(data) {
    console.log('JWT 캐시 검증 시나리오 완료');
}
