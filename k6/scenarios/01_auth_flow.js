/**
 * 시나리오 1: 로그인 부하테스트 — 출퇴근 러시아워 시뮬레이션
 * 목적: 아침/저녁 출퇴근 트래픽 패턴 재현 → Redis JWT 캐싱 전/후 성능 비교 기준선
 *
 * 실행:
 *   k6 run k6/scenarios/01_auth_flow.js                         (기본 SCALE=80)
 *   k6 run -e SCALE=100   k6/scenarios/01_auth_flow.js
 *   k6 run -e SCALE=1000  k6/scenarios/01_auth_flow.js
 *   k6 run -e SCALE=10000 k6/scenarios/01_auth_flow.js
 *
 * 결과 저장 (비교용):
 *   k6 run -e SCALE=100 --out json=k6/results/01_auth_scale100.json k6/scenarios/01_auth_flow.js
 *
 * Grafana 확인 지표:
 *   - http_req_duration{name="login"}         → 로그인 응답시간 (핵심)
 *   - http_req_duration{name="token_refresh"} → 토큰 갱신 응답시간
 *   - http_req_failed                         → 에러율
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL } from '../utils/auth.js';
import { randomEmail, randomNickname } from '../utils/data.js';

// 저녁 피크 VU 수 (기본 80). 러시아워 비율은 고정 유지.
const SCALE   = parseInt(__ENV.SCALE) || 80;
const MORNING = Math.ceil(SCALE * 0.625);  // 아침 피크 (원래 50/80)
const LULL    = Math.ceil(SCALE * 0.1875); // 한가한 낮 (원래 15/80)
const EVENING = SCALE;                      // 저녁 피크 (원래 80)

export const options = {
    scenarios: {
        rush_hour: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '2m', target: MORNING },  // 아침 러시 시작 (출근 시작)
                { duration: '2m', target: MORNING },  // 아침 피크 유지 (7~9시)
                { duration: '2m', target: LULL    },  // 러시 완화 (업무 시작)
                { duration: '2m', target: LULL    },  // 한가한 낮
                { duration: '2m', target: EVENING },  // 저녁 러시 시작 (퇴근, 18~20시)
                { duration: '2m', target: EVENING },  // 저녁 피크 유지
                { duration: '2m', target: 0       },  // 러시 종료
            ],
        },
    },
    thresholds: {
        'http_req_duration{name:login}':         ['p(95)<500'],
        'http_req_duration{name:token_refresh}': ['p(95)<300'],
        'http_req_duration{name:signup}':        ['p(95)<800'],
        'http_req_failed':                       ['rate<0.01'],
    },
};

export default function () {
    const email    = randomEmail();
    const password = 'password1234';
    const nickname = randomNickname();

    // 1. 회원가입
    const signupRes = http.post(
        `${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email, password, nickname }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'signup' } }
    );
    check(signupRes, { 'signup 201': (r) => r.status === 201 });
    sleep(0.3);

    // 2. 로그인 (핵심 측정 지표 — Redis 캐싱 전/후 비교 대상)
    const loginRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );
    if (!check(loginRes, { 'login 200': (r) => r.status === 200 })) {
        sleep(1);
        return;
    }
    const token = loginRes.json('data.accessToken');
    sleep(0.5);

    // 3. 토큰 갱신 (출퇴근 시 앱 재진입 패턴, 70% 확률)
    if (Math.random() < 0.7) {
        const refreshRes = http.post(
            `${BASE_URL}/api/v1/auth/refresh`,
            null,
            { headers: { 'Content-Type': 'application/json' }, tags: { name: 'token_refresh' } }
        );
        check(refreshRes, { 'refresh 200 or 401': (r) => r.status === 200 || r.status === 401 });
        sleep(0.3);
    }

    // 4. 로그아웃
    const logoutRes = http.post(
        `${BASE_URL}/api/v1/auth/logout`,
        null,
        {
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            tags: { name: 'logout' },
        }
    );
    check(logoutRes, { 'logout 200': (r) => r.status === 200 });

    sleep(Math.random() * 2 + 0.5);
}
