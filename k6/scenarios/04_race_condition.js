/**
 * 시나리오 4: Race Condition — 동시 신청 경쟁
 * 목적: maxPassengers=3인 게시글에 35명이 동시에 신청
 *       Optimistic Lock 동작 검증 — 3건만 성공, 나머지는 409
 * Grafana: carpool_optimistic_lock_conflicts_total 급등 관찰
 * 실행: k6 run k6/scenarios/04_race_condition.js
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL, authHeaders } from '../utils/auth.js';
import { randomDriverPayload } from '../utils/data.js';

export const options = {
    scenarios: {
        race: {
            executor: 'shared-iterations',
            vus: 35,
            iterations: 35,
            maxDuration: '2m',
        },
    },
    thresholds: {
        // 201(성공) 또는 409(정원 마감/충돌) 이외 응답이 없어야 함
        'checks{type:apply_result}': ['rate>0.99'],
    },
};

export function setup() {
    const password = 'password1234';
    const ts = Date.now();

    // 드라이버 계정 생성
    const driverEmail = `race_driver_${ts}@test.com`;
    http.post(`${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email: driverEmail, password, nickname: 'racedriver' }),
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
        driverPayload = randomDriverPayload();
        http.post(`${BASE_URL}/api/v1/drivers`,
            JSON.stringify(driverPayload), authHeaders(driverToken));
    }

    // 게시글 생성 (autoAccept:true → 신청 즉시 incrementPassengers 실행 → 락 경쟁 최대화)
    const postRes = http.post(`${BASE_URL}/api/v1/posts`,
        JSON.stringify({
            title: '동시 신청 경쟁 테스트',
            departureLocation: '강남역', departureLat: 37.4979, departureLng: 127.0276,
            destinationLocation: '판교역', destinationLat: 37.3943, destinationLng: 127.1110,
            departureTime: new Date(Date.now() + 3600000).toISOString().replace('Z', ''),
            maxPassengers: 3,
            autoAccept: true,
            price: 5000,
            tagIds: [],
        }),
        authHeaders(driverToken));
    const postId = postRes.json('data.id');

    // 승객 35명 계정 생성
    const tokens = [];
    for (let i = 0; i < 35; i++) {
        const email = `race_pass_${i}_${ts}@test.com`;
        http.post(`${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password, nickname: `racer${i}` }),
            { headers: { 'Content-Type': 'application/json' } });
        const pLogin = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password }),
            { headers: { 'Content-Type': 'application/json' } });
        if (pLogin.status === 200) tokens.push(pLogin.json('data.accessToken'));
        sleep(0.05);
    }

    return { tokens, postId };
}

export default function (data) {
    const { tokens, postId } = data;
    const token = tokens[__VU % tokens.length];

    const res = http.post(
        `${BASE_URL}/api/v1/posts/${postId}/applications`,
        null,
        { ...authHeaders(token), tags: { type: 'apply_result' } }
    );

    // 201(성공) 또는 409(정원 초과 / Optimistic Lock 충돌) 모두 예상된 결과
    check(res, {
        'apply: 201 or 409': (r) => r.status === 201 || r.status === 409,
    }, { type: 'apply_result' });

    if (res.status === 201) {
        console.log(`VU ${__VU}: 신청 성공`);
    } else if (res.status === 409) {
        const code = res.json('code');
        console.log(`VU ${__VU}: 409 (${code}) — 정원 마감 또는 락 충돌`);
    }
}
