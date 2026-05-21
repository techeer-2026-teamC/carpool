/**
 * 시나리오 6: Soak Test — 지속 부하
 * 목적: 50 VU 10분 지속 부하로 메모리 누수, 커넥션 풀 고갈, 응답시간 점진 악화 감지
 * Grafana 관찰: JVM Heap 우상향 = 메모리 누수, hikaricp_connections_active max 근접 = 풀 고갈
 * 실행: k6 run k6/scenarios/06_soak_test.js
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL } from '../utils/auth.js';
import { randomEmail, randomNickname } from '../utils/data.js';

export const options = {
    scenarios: {
        soak: {
            executor: 'constant-vus',
            vus: 50,
            duration: '10m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1500'],
        http_req_failed:   ['rate<0.02'],
    },
};

export function setup() {
    const res = http.get(`${BASE_URL}/api/v1/posts`);
    if (res.status !== 200) return { postIds: [] };
    const page = res.json('data');
    const posts = (page && page.content) ? page.content : (Array.isArray(page) ? page : []);
    return { postIds: posts.map(p => p.id) };
}

export default function (data) {
    const { postIds } = data;
    const action = Math.random();

    if (action < 0.40) {
        // 40% — 게시글 목록 조회
        const res = http.get(`${BASE_URL}/api/v1/posts`, { tags: { action: 'list_posts' } });
        check(res, { 'list 200': (r) => r.status === 200 });
        sleep(1);

    } else if (action < 0.65 && postIds.length > 0) {
        // 25% — 게시글 상세 조회
        const id = postIds[Math.floor(Math.random() * postIds.length)];
        const res = http.get(`${BASE_URL}/api/v1/posts/${id}`, { tags: { action: 'post_detail' } });
        check(res, { 'detail 200 or 404': (r) => r.status === 200 || r.status === 404 });
        sleep(1.5);

    } else if (action < 0.75) {
        // 10% — 태그 조회
        const res = http.get(`${BASE_URL}/api/v1/tags`, { tags: { action: 'tags' } });
        check(res, { 'tags 200': (r) => r.status === 200 });
        sleep(0.5);

    } else if (action < 0.85) {
        // 10% — 헬스체크 (모니터링 시스템 시뮬레이션)
        const res = http.get(`${BASE_URL}/actuator/health`, { tags: { action: 'health' } });
        check(res, { 'health 200': (r) => r.status === 200 });
        sleep(0.3);

    } else {
        // 15% — 회원가입 + 로그인 + 로그아웃 사이클 (Redis 부하)
        const email = randomEmail();
        const password = 'password1234';
        http.post(`${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password, nickname: randomNickname() }),
            { headers: { 'Content-Type': 'application/json' }, tags: { action: 'signup' } });
        const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password }),
            { headers: { 'Content-Type': 'application/json' }, tags: { action: 'login' } });
        if (loginRes.status === 200) {
            const token = loginRes.json('data.accessToken');
            sleep(0.5);
            http.post(`${BASE_URL}/api/v1/auth/logout`, null, {
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
                tags: { action: 'logout' },
            });
        }
        sleep(1);
    }

    sleep(Math.random() * 1 + 0.5);
}
