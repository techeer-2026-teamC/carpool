/**
 * 시나리오 5: Location Update Spike
 * 목적: 드라이버 초당 1회 위치 업데이트 + 30 VU 승객 동시 폴링
 *       Redis 쓰기 압박 vs 읽기 응답시간 괴리 측정
 * 기대: GET location p95 < POST location p95 (Redis 캐시 효과)
 * 실행: k6 run k6/scenarios/05_location_spike.js
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL, authHeaders } from '../utils/auth.js';
import { randomDriverPayload, randomLocation } from '../utils/data.js';

export const options = {
    scenarios: {
        driver_updates: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: '1s',
            duration: '3m',
            preAllocatedVUs: 5,
            maxVUs: 10,
            exec: 'driverUpdates',
        },
        passenger_polls: {
            executor: 'ramping-vus',
            startVUs: 5,
            stages: [
                { duration: '30s', target: 30 },
                { duration: '2m',  target: 30 },
                { duration: '30s', target: 0  },
            ],
            exec: 'passengerPolls',
        },
    },
    thresholds: {
        'http_req_duration{endpoint:location_update}': ['p(95)<300'],
        'http_req_duration{endpoint:location_poll}':   ['p(95)<100'],
        http_req_failed: ['rate<0.02'],
    },
};

export function setup() {
    const password = 'password1234';
    const ts = Date.now();

    const driverEmail = `loc_driver_${ts}@test.com`;
    http.post(`${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email: driverEmail, password, nickname: 'locdriver' }),
        { headers: { 'Content-Type': 'application/json' } });
    const dLogin = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: driverEmail, password }),
        { headers: { 'Content-Type': 'application/json' } });
    const driverToken = dLogin.json('data.accessToken');

    let driverRes = http.post(`${BASE_URL}/api/v1/drivers`,
        JSON.stringify(randomDriverPayload()), authHeaders(driverToken));
    if (driverRes.status === 409) {
        http.post(`${BASE_URL}/api/v1/drivers`,
            JSON.stringify(randomDriverPayload()), authHeaders(driverToken));
    }

    const postRes = http.post(`${BASE_URL}/api/v1/posts`,
        JSON.stringify({
            title: '위치 업데이트 스파이크 테스트',
            departureLocation: '강남역', departureLat: 37.4979, departureLng: 127.0276,
            destinationLocation: '판교역', destinationLat: 37.3943, destinationLng: 127.1110,
            departureTime: new Date(Date.now() + 3600000).toISOString().replace('Z', ''),
            maxPassengers: 4, autoAccept: false, price: 5000, tagIds: [],
        }),
        authHeaders(driverToken));
    const postId = postRes.json('data.id');

    // 게시글 마감 → Ride 생성
    http.post(`${BASE_URL}/api/v1/posts/${postId}/close`, null, authHeaders(driverToken));
    const ridesRes = http.get(`${BASE_URL}/api/v1/rides/me`, authHeaders(driverToken));
    const rides = ridesRes.json('data');
    const ride = (rides || []).find(r => r.postId === postId);
    const rideId = ride ? ride.id : null;

    // 라이드 시작
    if (rideId) {
        http.post(`${BASE_URL}/api/v1/rides/${rideId}/start`, null, authHeaders(driverToken));
    }

    // 승객 10명 토큰 준비
    const passengerTokens = [];
    for (let i = 0; i < 10; i++) {
        const email = `loc_pass_${i}_${ts}@test.com`;
        http.post(`${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password, nickname: `locpass${i}` }),
            { headers: { 'Content-Type': 'application/json' } });
        const pLogin = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password }),
            { headers: { 'Content-Type': 'application/json' } });
        if (pLogin.status === 200) passengerTokens.push(pLogin.json('data.accessToken'));
        sleep(0.05);
    }

    return { driverToken, passengerTokens, rideId };
}

// 드라이버: 초당 1회 위치 업데이트
export function driverUpdates(data) {
    if (!data.rideId) return;
    const loc = randomLocation(37.498, 127.028);
    const res = http.post(
        `${BASE_URL}/api/v1/rides/${data.rideId}/location`,
        JSON.stringify(loc),
        { ...authHeaders(data.driverToken), tags: { endpoint: 'location_update' } }
    );
    check(res, { 'location update 200': (r) => r.status === 200 });
}

// 승객: 1초 간격으로 위치 폴링
export function passengerPolls(data) {
    if (!data.rideId) return;
    const res = http.get(
        `${BASE_URL}/api/v1/rides/${data.rideId}/location`,
        { tags: { endpoint: 'location_poll' } }
    );
    check(res, { 'location poll 200 or 404': (r) => r.status === 200 || r.status === 404 });
    sleep(1);
}
