/**
 * 시나리오 2: Read-Heavy Browse
 * 목적: 게시글 목록 → 상세 조회 → 태그 조회 패턴을 100 VU로 검증
 * DB 읽기 쿼리 성능과 N+1 문제를 부하 상황에서 측정
 * 실행: k6 run k6/scenarios/02_read_heavy.js
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL } from '../utils/auth.js';
import { checkStatus } from '../utils/checks.js';

export const options = {
    scenarios: {
        browsing: {
            executor: 'constant-vus',
            vus: 100,
            duration: '3m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<300', 'p(99)<800'],
        http_req_failed:   ['rate<0.01'],
        'http_req_duration{endpoint:list_posts}':  ['p(95)<200'],
        'http_req_duration{endpoint:post_detail}': ['p(95)<250'],
        'http_req_duration{endpoint:tags}':        ['p(95)<100'],
    },
};

export function setup() {
    const res = http.get(`${BASE_URL}/api/v1/posts`);
    if (res.status !== 200) return { postIds: [1, 2, 3, 4, 5] };
    const posts = res.json('data');
    return { postIds: (posts || []).slice(0, 20).map(p => p.id) };
}

export default function (data) {
    const { postIds } = data;

    // GET /tags — 정적 데이터, 매우 빠르게 응답해야 함
    const tagsRes = http.get(`${BASE_URL}/api/v1/tags`, { tags: { endpoint: 'tags' } });
    checkStatus(tagsRes, 200, 'tags');
    sleep(0.2);

    // GET /posts — 전체 목록
    const listRes = http.get(`${BASE_URL}/api/v1/posts`, { tags: { endpoint: 'list_posts' } });
    if (checkStatus(listRes, 200, 'list_posts')) {
        const posts = listRes.json('data');
        if (posts && posts.length > 0) {
            const post = posts[Math.floor(Math.random() * posts.length)];
            sleep(0.5);

            // GET /posts/{id} — 상세 조회 (댓글 포함)
            const detailRes = http.get(
                `${BASE_URL}/api/v1/posts/${post.id}`,
                { tags: { endpoint: 'post_detail' } }
            );
            checkStatus(detailRes, 200, 'post_detail');
            sleep(1);
        }
    }

    // GET /rides/{id}/location — 위치 폴링 (30% 확률)
    if (Math.random() < 0.3 && postIds.length > 0) {
        const rideId = postIds[Math.floor(Math.random() * postIds.length)];
        const locationRes = http.get(
            `${BASE_URL}/api/v1/rides/${rideId}/location`,
            { tags: { endpoint: 'location_poll' } }
        );
        check(locationRes, { 'location 200 or 404': (r) => r.status === 200 || r.status === 404 });
    }

    sleep(Math.random() * 2 + 1);
}
