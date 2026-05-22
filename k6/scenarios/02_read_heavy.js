/**
 * 시나리오 2: 경량 API 동시성 테스트
 * 목적: 게시글 목록/상세, 태그, 운행 위치 폴링 API를 100 VU로 동시 검증
 * 실행: k6 run k6/scenarios/02_read_heavy.js
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL } from '../utils/auth.js';
import { checkStatus } from '../utils/checks.js';
import { randomDriverPayload, randomPostPayload } from '../utils/data.js';

export const options = {
    scenarios: {
        browsing: {
            executor: 'constant-vus',
            vus: 100,
            duration: '3m',
        },
    },
    thresholds: {
        http_req_duration:                          ['p(95)<300', 'p(99)<800'],
        http_req_failed:                            ['rate<0.01'],
        'http_req_duration{name:list_posts}':       ['p(95)<200'],
        'http_req_duration{name:post_detail}':      ['p(95)<250'],
        'http_req_duration{name:tags}':             ['p(95)<100'],
        'http_req_duration{name:location_poll}':    ['p(95)<200'],
    },
};

export function setup() {
    // 게시글 목록 확보
    const postsRes = http.get(`${BASE_URL}/api/v1/posts`);
    const page  = postsRes.status === 200 ? postsRes.json('data') : null;
    const posts = (page && page.content) ? page.content : (Array.isArray(page) ? page : []);
    const postIds = posts.slice(0, 20).map(p => p.id);

    // 위치 폴링용 rideId 확보: 드라이버 계정 생성 → 게시글 → 운행 생성
    const ts       = Date.now();
    const email    = `readheavy_${ts}@test.com`;
    const password = 'password1234';

    http.post(`${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email, password, nickname: `readheavy_${ts}` }),
        { headers: { 'Content-Type': 'application/json' } });

    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } });

    if (loginRes.status !== 200) return { postIds, rideIds: [] };

    const token   = loginRes.json('data.accessToken');
    const headers = { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } };

    // 드라이버 등록
    const driverPayload = randomDriverPayload();
    http.post(`${BASE_URL}/api/v1/drivers`, JSON.stringify(driverPayload), headers);

    // 게시글 생성
    const postRes = http.post(`${BASE_URL}/api/v1/posts`,
        JSON.stringify(randomPostPayload(120)), headers);
    if (postRes.status !== 201) return { postIds, rideIds: [] };

    const setupPostId = postRes.json('data.id');

    // 운행 생성 (SCHEDULED 상태 — 위치는 null이지만 200 응답)
    const rideRes = http.post(`${BASE_URL}/api/v1/rides`,
        JSON.stringify({ postId: setupPostId }), headers);

    const rideIds = rideRes.status === 201 ? [rideRes.json('data.id')] : [];

    return { postIds, rideIds };
}

export default function (data) {
    const { postIds, rideIds } = data;

    // GET /tags
    const tagsRes = http.get(`${BASE_URL}/api/v1/tags`, { tags: { name: 'tags' } });
    checkStatus(tagsRes, 200, 'tags');
    sleep(0.2);

    // GET /posts → 상세 조회
    const listRes = http.get(`${BASE_URL}/api/v1/posts`, { tags: { name: 'list_posts' } });
    if (checkStatus(listRes, 200, 'list_posts')) {
        const page  = listRes.json('data');
        const posts = (page && page.content) ? page.content : (Array.isArray(page) ? page : []);
        if (posts && posts.length > 0) {
            const post = posts[Math.floor(Math.random() * posts.length)];
            sleep(0.5);
            const detailRes = http.get(
                `${BASE_URL}/api/v1/posts/${post.id}`,
                { tags: { name: 'post_detail' } }
            );
            checkStatus(detailRes, 200, 'post_detail');
            sleep(1);
        }
    }

    // GET /rides/{id}/location — 위치 폴링 (30% 확률, 실제 rideId 사용)
    if (Math.random() < 0.3 && rideIds.length > 0) {
        const rideId      = rideIds[Math.floor(Math.random() * rideIds.length)];
        const locationRes = http.get(
            `${BASE_URL}/api/v1/rides/${rideId}/location`,
            { tags: { name: 'location_poll' } }
        );
        check(locationRes, { 'location 200': (r) => r.status === 200 });
    }

    sleep(Math.random() * 2 + 1);
}
