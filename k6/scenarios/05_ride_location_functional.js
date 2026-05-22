/**
 * 시나리오 5: 운행 기능 테스트 — 상태별 위치 API 검증
 * 목적: 운행 상태(SCHEDULED→IN_PROGRESS→COMPLETED)에 따른 위치 저장/조회 동작을 순서대로 검증
 *       - SCHEDULED: 위치 null
 *       - IN_PROGRESS: WebSocket STOMP로 위치 전송 → DB INSERT → GET 조회 확인
 *       - COMPLETED: 마지막 위치 유지
 *
 * 실행: k6 run k6/scenarios/05_ride_location_functional.js
 * 특징: 1 VU, 기능 정확성 검증 목적 (부하 아님)
 */
import http from 'k6/http';
import ws from 'k6/ws';
import { sleep, check } from 'k6';
import { BASE_URL, WS_URL, authHeaders } from '../utils/auth.js';
import { randomDriverPayload, randomPostPayload, randomLocation, stompConnectFrame, stompSendLocationFrame } from '../utils/data.js';

export const options = {
    scenarios: {
        functional: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '3m',
        },
    },
    thresholds: {
        checks: ['rate==1.0'],  // 모든 check 통과해야 함
    },
};

export default function () {
    const ts       = Date.now();
    const email    = `func_driver_${ts}@test.com`;
    const password = 'password1234';

    // ── 1. 드라이버 계정 생성 및 로그인 ──────────────────────────────
    http.post(`${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email, password, nickname: `func_driver_${ts}` }),
        { headers: { 'Content-Type': 'application/json' } });

    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } });

    check(loginRes, { '드라이버 로그인 200': (r) => r.status === 200 });
    if (loginRes.status !== 200) return;

    const token = loginRes.json('data.accessToken');
    const hdr   = authHeaders(token);

    // ── 2. 드라이버 등록 ────────────────────────────────────────────
    const driverRes = http.post(`${BASE_URL}/api/v1/drivers`,
        JSON.stringify(randomDriverPayload()), hdr);
    check(driverRes, { '드라이버 등록 201': (r) => r.status === 201 });

    // ── 3. 게시글 + 운행 생성 ────────────────────────────────────────
    const postRes = http.post(`${BASE_URL}/api/v1/posts`,
        JSON.stringify(randomPostPayload(120)), hdr);
    check(postRes, { '게시글 생성 201': (r) => r.status === 201 });
    if (postRes.status !== 201) return;

    const postId = postRes.json('data.id');

    const rideRes = http.post(`${BASE_URL}/api/v1/rides`,
        JSON.stringify({ postId }), hdr);
    check(rideRes, { '운행 생성 201': (r) => r.status === 201 });
    if (rideRes.status !== 201) return;

    const rideId = rideRes.json('data.id');
    console.log(`운행 생성: rideId=${rideId}`);

    // ── 4. SCHEDULED 상태: 위치 null 확인 ────────────────────────────
    sleep(0.3);
    const locBeforeStart = http.get(`${BASE_URL}/api/v1/rides/${rideId}/location`, hdr);
    check(locBeforeStart, {
        'SCHEDULED: 위치 API 200': (r) => r.status === 200,
        'SCHEDULED: latitude null': (r) => r.json('data.driverLatitude') == null,
    });

    // ── 5. 운행 시작 (IN_PROGRESS) ───────────────────────────────────
    sleep(0.3);
    const startRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/start`, null, hdr);
    check(startRes, { '운행 시작 200': (r) => r.status === 200 });
    if (startRes.status !== 200) return;

    // ── 6. WebSocket STOMP 위치 전송 (3회, 1초 간격) ─────────────────
    let sendCount  = 0;
    let connected  = false;
    const TARGET   = 3;
    const wsParams = { headers: { 'Authorization': `Bearer ${token}` } };

    ws.connect(WS_URL, wsParams, function (socket) {
        socket.on('open', () => {
            socket.send(stompConnectFrame(token));
        });

        socket.on('message', (data) => {
            if (data.startsWith('CONNECTED') && !connected) {
                connected = true;
                console.log('STOMP CONNECTED');

                socket.setInterval(() => {
                    if (sendCount >= TARGET) {
                        socket.close();
                        return;
                    }
                    const { latitude, longitude } = randomLocation(37.5, 127.0);
                    socket.send(stompSendLocationFrame(rideId, latitude, longitude));
                    sendCount++;
                    console.log(`위치 전송 ${sendCount}/${TARGET}: (${latitude.toFixed(4)}, ${longitude.toFixed(4)})`);
                }, 1000);
            }
        });

        socket.on('error', (e) => {
            console.error('WebSocket 오류:', e);
        });

        // 최대 10초 후 강제 종료
        socket.setTimeout(() => socket.close(), 10000);
    });

    check({ sendCount }, { [`WebSocket 위치 전송 ${TARGET}회`]: (d) => d.sendCount >= TARGET });

    // ── 7. IN_PROGRESS 상태: 위치 존재 확인 ──────────────────────────
    sleep(0.5);
    const locAfterSend = http.get(`${BASE_URL}/api/v1/rides/${rideId}/location`, hdr);
    check(locAfterSend, {
        'IN_PROGRESS: 위치 API 200':      (r) => r.status === 200,
        'IN_PROGRESS: latitude 존재':     (r) => r.json('data.driverLatitude') != null,
        'IN_PROGRESS: longitude 존재':    (r) => r.json('data.driverLongitude') != null,
        'IN_PROGRESS: recordedAt 존재':   (r) => r.json('data.recordedAt') != null,
    });

    // ── 8. 운행 완료 ─────────────────────────────────────────────────
    sleep(0.3);
    const completeRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/complete`, null, hdr);
    check(completeRes, { '운행 완료 200': (r) => r.status === 200 });

    // ── 9. COMPLETED 상태: 마지막 위치 유지 확인 ─────────────────────
    sleep(0.3);
    const locAfterComplete = http.get(`${BASE_URL}/api/v1/rides/${rideId}/location`, hdr);
    check(locAfterComplete, {
        'COMPLETED: 위치 API 200':    (r) => r.status === 200,
        'COMPLETED: 마지막 위치 유지': (r) => r.json('data.driverLatitude') != null,
    });

    console.log('모든 검증 완료');
}
