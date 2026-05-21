/**
 * 시나리오 1: Auth Flow
 * 목적: 회원가입 → 로그인 → 토큰 갱신 → 로그아웃 사이클의 처리량 측정
 * JWT 발급 지연과 Redis 토큰 저장 압박을 검증
 * 실행: k6 run k6/scenarios/01_auth_flow.js
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL, signup, login, logout } from '../utils/auth.js';
import { randomEmail, randomNickname } from '../utils/data.js';

export const options = {
    scenarios: {
        auth_ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '1m',  target: 20 },
                { duration: '30s', target: 50 },
                { duration: '1m',  target: 50 },
                { duration: '30s', target: 0  },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed:   ['rate<0.02'],
        'http_req_duration{endpoint:login}':  ['p(95)<300'],
        'http_req_duration{endpoint:signup}': ['p(95)<800'],
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
        { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'signup' } }
    );
    check(signupRes, { 'signup 201': (r) => r.status === 201 });
    sleep(0.5);

    // 2. 로그인
    const loginRes = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } }
    );
    if (!check(loginRes, { 'login 200': (r) => r.status === 200 })) {
        sleep(1);
        return;
    }
    const token = loginRes.json('data.accessToken');
    sleep(0.5);

    // 3. 토큰 갱신 (쿠키 기반 — k6 쿠키 jar가 자동 처리)
    const refreshRes = http.post(
        `${BASE_URL}/api/v1/auth/refresh`,
        null,
        { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'refresh' } }
    );
    check(refreshRes, { 'refresh 200 or 401': (r) => r.status === 200 || r.status === 401 });
    sleep(0.5);

    // 4. 로그아웃
    const logoutRes = http.post(
        `${BASE_URL}/api/v1/auth/logout`,
        null,
        {
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            tags: { endpoint: 'logout' },
        }
    );
    check(logoutRes, { 'logout 200': (r) => r.status === 200 });
    sleep(1);
}
