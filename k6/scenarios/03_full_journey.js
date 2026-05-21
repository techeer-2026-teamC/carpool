/**
 * 시나리오 3: Full Carpool Journey
 * 목적: 드라이버 등록 → 게시글 생성 → 승객 신청 → 수락 → 라이드 시작/완료 → 리뷰 전체 플로우 검증
 * 실행: k6 run k6/scenarios/03_full_journey.js
 */
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL, authHeaders } from '../utils/auth.js';
import { randomEmail, randomNickname, randomPostPayload, randomDriverPayload, randomLocation } from '../utils/data.js';
import { checkStatus, checkAndExtractId } from '../utils/checks.js';

export const options = {
    scenarios: {
        full_journey: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '1m', target: 5  },
                { duration: '3m', target: 10 },
                { duration: '1m', target: 0  },
            ],
        },
    },
    thresholds: {
        http_req_failed:   ['rate<0.05'],
        http_req_duration: ['p(95)<1000'],
        'http_req_duration{step:ride_complete}': ['p(95)<500'],
    },
};

const PASSWORD = 'password1234';

function createAndLogin(email, nickname) {
    http.post(`${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email, password: PASSWORD, nickname }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const res = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    if (res.status !== 200) return null;
    return res.json('data.accessToken');
}

export default function () {
    // === 드라이버 계정 ===
    const driverEmail = randomEmail();
    const driverToken = createAndLogin(driverEmail, randomNickname());
    if (!driverToken) return;
    sleep(0.3);

    // 드라이버 등록 (게시글 생성 전 필수)
    let driverPayload = randomDriverPayload();
    let driverRes = http.post(`${BASE_URL}/api/v1/drivers`,
        JSON.stringify(driverPayload), authHeaders(driverToken));
    // 차량번호 충돌 시 재시도
    if (driverRes.status === 409) {
        driverPayload = randomDriverPayload();
        driverRes = http.post(`${BASE_URL}/api/v1/drivers`,
            JSON.stringify(driverPayload), authHeaders(driverToken));
    }
    if (!checkStatus(driverRes, 201, 'driver register')) return;
    sleep(0.3);

    // 게시글 생성
    const postRes = http.post(`${BASE_URL}/api/v1/posts`,
        JSON.stringify(randomPostPayload(120)), authHeaders(driverToken));
    const postId = checkAndExtractId(postRes, 201, 'create post');
    if (!postId) return;
    sleep(0.5);

    // === 승객 계정 ===
    const passengerEmail = randomEmail();
    const passengerToken = createAndLogin(passengerEmail, randomNickname());
    if (!passengerToken) return;
    sleep(0.3);

    // 카풀 신청
    const applyRes = http.post(
        `${BASE_URL}/api/v1/posts/${postId}/applications`,
        null, authHeaders(passengerToken)
    );
    const applicationId = checkAndExtractId(applyRes, 201, 'apply');
    if (!applicationId) return;
    sleep(0.5);

    // 드라이버가 신청 수락
    const acceptRes = http.patch(
        `${BASE_URL}/api/v1/applications/${applicationId}/accept`,
        null, authHeaders(driverToken)
    );
    if (!checkStatus(acceptRes, 200, 'accept application')) return;
    sleep(0.3);

    // 게시글 마감 (Ride 자동 생성)
    const closeRes = http.post(
        `${BASE_URL}/api/v1/posts/${postId}/close`,
        null, authHeaders(driverToken)
    );
    if (!checkStatus(closeRes, 200, 'close post')) return;
    sleep(0.5);

    // 라이드 ID 조회
    const ridesRes = http.get(`${BASE_URL}/api/v1/rides/me`, authHeaders(driverToken));
    if (!checkStatus(ridesRes, 200, 'get my rides')) return;
    const rides = ridesRes.json('data');
    const ride = (rides || []).find(r => r.postId === postId);
    if (!ride) { console.warn(`postId ${postId}에 해당하는 라이드 없음`); return; }
    const rideId = ride.id;
    sleep(0.3);

    // 라이드 시작
    const startRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/start`,
        null, authHeaders(driverToken));
    if (!checkStatus(startRes, 200, 'start ride')) return;
    sleep(0.5);

    // 위치 업데이트 3회
    for (let i = 0; i < 3; i++) {
        const loc = randomLocation(37.5, 127.0);
        http.post(`${BASE_URL}/api/v1/rides/${rideId}/location`,
            JSON.stringify(loc), authHeaders(driverToken));
        sleep(1);
    }

    // 라이드 완료
    const completeRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/complete`,
        null, { ...authHeaders(driverToken), tags: { step: 'ride_complete' } });
    if (!checkStatus(completeRes, 200, 'complete ride')) return;
    sleep(0.5);

    // 승객이 리뷰 작성
    const reviewRes = http.post(
        `${BASE_URL}/api/v1/reviews/rides/${rideId}`,
        JSON.stringify({ rating: 5, comment: 'K6 부하테스트 리뷰' }),
        authHeaders(passengerToken)
    );
    check(reviewRes, { 'review 201': (r) => r.status === 201 });
    sleep(1);
}
