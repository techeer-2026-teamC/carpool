/**
 * 시나리오 4: 신청 동시성 테스트
 * 목적: maxPassengers=3인 게시글에 SCALE명이 동시 신청 → Optimistic Lock으로 3건만 성공 검증
 *       10 / 100 / 1000 규모별 락 동작 및 성능 측정
 *
 * 실행:
 *   k6 run -e SCALE=10   k6/scenarios/04_race_condition.js   # 10명 동시
 *   k6 run -e SCALE=100  k6/scenarios/04_race_condition.js   # 100명 동시
 *   k6 run -e SCALE=1000 k6/scenarios/04_race_condition.js   # 1000명 동시
 *
 * Grafana 확인:
 *   carpool_optimistic_lock_conflicts_total — SCALE 증가 시 급등 관찰
 *   successful_applications — 항상 3 이하여야 함
 *
 * 향후 비교:
 *   -e USE_LOCK=false 옵션으로 락 없는 버전과 비교 예정 (API 추가 후 활성화)
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, authHeaders } from '../utils/auth.js';
import { randomDriverPayload } from '../utils/data.js';

const SCALE = parseInt(__ENV.SCALE) || 10;

// 풀 크기: SCALE이 크면 계정 생성 시간을 줄이기 위해 최대 200개로 제한
// (같은 토큰을 여러 VU가 공유 — 서버 측 동시성은 SCALE 그대로 발생)
const POOL_SIZE = Math.min(SCALE, 200);

export const options = {
    scenarios: {
        race: {
            executor: 'shared-iterations',
            vus: SCALE,
            iterations: SCALE,
            maxDuration: SCALE <= 100 ? '5m' : '15m',
        },
    },
    thresholds: {
        // 낙관적 락이 정상 작동하면 성공 신청 수 ≤ maxPassengers(3)
        'successful_applications': ['count<=3'],
        // 201(성공) 또는 409(정원 마감/락 충돌) 이외 응답이 없어야 함
        'checks{type:apply_result}': ['rate>0.99'],
    },
};

const successfulApplications = new Counter('successful_applications');

export function setup() {
    const password = 'password1234';
    const ts       = Date.now();

    // 드라이버 계정 생성
    const driverEmail = `race_driver_${ts}@test.com`;
    http.post(`${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email: driverEmail, password, nickname: `racedriver_${ts}` }),
        { headers: { 'Content-Type': 'application/json' } });
    const dLogin = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: driverEmail, password }),
        { headers: { 'Content-Type': 'application/json' } });
    const driverToken = dLogin.json('data.accessToken');

    // 드라이버 등록
    let driverPayload = randomDriverPayload();
    let driverRes = http.post(`${BASE_URL}/api/v1/drivers`,
        JSON.stringify(driverPayload), authHeaders(driverToken));
    if (driverRes.status === 409) {
        http.post(`${BASE_URL}/api/v1/drivers`,
            JSON.stringify(randomDriverPayload()), authHeaders(driverToken));
    }

    // 게시글 생성 (autoAccept:true → 신청 즉시 incrementPassengers 실행 → 락 경쟁 최대화)
    const postRes = http.post(`${BASE_URL}/api/v1/posts`,
        JSON.stringify({
            title: `동시 신청 경쟁 테스트 SCALE=${SCALE}`,
            departureLocation: '강남역', departureLat: 37.4979, departureLng: 127.0276,
            destinationLocation: '판교역', destinationLat: 37.3943, destinationLng: 127.1110,
            departureTime: (function() {
                const d = new Date(Date.now() + 3600000);
                const pad = n => String(n).padStart(2, '0');
                return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
            })(),
            maxPassengers: 3,
            autoAccept: true,
            price: 5000,
            tagIds: [],
        }),
        authHeaders(driverToken));
    const postId = postRes.json('data.id');

    // 승객 계정 생성 (POOL_SIZE개)
    const tokens = [];
    for (let i = 0; i < POOL_SIZE; i++) {
        const email = `race_pass_${i}_${ts}@test.com`;
        http.post(`${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password, nickname: `racer_${i}_${ts}` }),
            { headers: { 'Content-Type': 'application/json' } });
        const pLogin = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password }),
            { headers: { 'Content-Type': 'application/json' } });
        if (pLogin.status === 200) tokens.push(pLogin.json('data.accessToken'));
        sleep(0.01);
    }

    console.log(`setup 완료: postId=${postId}, 계정 ${tokens.length}개 (SCALE=${SCALE})`);
    return { tokens, postId };
}

export default function (data) {
    const { tokens, postId } = data;

    // USE_LOCK=false 설정 시 락 없는 엔드포인트로 전환 (향후 구현 예정)
    // const endpoint = __ENV.USE_LOCK === 'false'
    //     ? `${BASE_URL}/api/v1/posts/${postId}/applications/no-lock`
    //     : `${BASE_URL}/api/v1/posts/${postId}/applications`;
    const endpoint = `${BASE_URL}/api/v1/posts/${postId}/applications`;

    const token = tokens[(__VU - 1) % tokens.length];

    const res = http.post(
        endpoint,
        null,
        { ...authHeaders(token), tags: { type: 'apply_result' } }
    );

    check(res, {
        'apply: 201 or 409': (r) => r.status === 201 || r.status === 409,
    }, { type: 'apply_result' });

    if (res.status === 201) {
        successfulApplications.add(1);
        console.log(`VU ${__VU}: 신청 성공`);
    } else if (res.status === 409) {
        const code = res.json('code') || '';
        console.log(`VU ${__VU}: 409 (${code}) — 정원 마감 또는 락 충돌`);
    }
}
