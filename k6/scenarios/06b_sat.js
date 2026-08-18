// 포화(saturation) 벤치마크 — inbound 채널을 의도적으로 포화시켜 스레드 모델별 "처리량 천장"을 비교.
// 실제 GPS(1초 간격)와 달리 전송 간격을 SEND_INTERVAL_MS로 확 줄여 메시지를 쏟아붓는다.
// 성능 기준은 서버 메트릭 carpool_location_updates_total(실제 처리 수)로 측정한다.
import http from 'k6/http';
import ws from 'k6/ws';
import { sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { randomDriverPayload, randomPostPayload, randomLocation, stompConnectFrame, stompSendLocationFrame } from '../utils/data.js';
import { BASE_URL, WS_URL, authHeaders } from '../utils/auth.js';

const SCALE      = parseInt(__ENV.SCALE) || 100;
const INTERVAL   = parseInt(__ENV.SEND_INTERVAL_MS) || 20;   // ms — 작을수록 포화
const FLOOD_SEC  = parseInt(__ENV.FLOOD_SEC) || 60;
const POOL_SIZE  = Math.min(SCALE, 100);

export const options = {
    scenarios: { sat: { executor: 'constant-vus', vus: SCALE, duration: `${FLOOD_SEC}s` } },
    thresholds: {},  // 포화 벤치마크 — pass/fail 아님
};

const wsConnectDuration = new Trend('ws_connect_duration');
const clientSent        = new Counter('client_sent');
const sendErrors        = new Counter('send_errors');

export function setup() {
    const password = 'password1234';
    const ts = Date.now();
    const pool = [];
    console.log(`[sat] 드라이버 풀 ${POOL_SIZE}개 생성 (SCALE=${SCALE}, interval=${INTERVAL}ms)`);
    for (let i = 0; i < POOL_SIZE; i++) {
        const email = `sat_driver_${i}_${ts}@test.com`;
        http.post(`${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password, nickname: `sat_driver_${i}_${ts}` }),
            { headers: { 'Content-Type': 'application/json' } });
        const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password }), { headers: { 'Content-Type': 'application/json' } });
        if (loginRes.status !== 200) continue;
        const token = loginRes.json('data.accessToken');
        const hdr = authHeaders(token);
        http.post(`${BASE_URL}/api/v1/drivers`, JSON.stringify(randomDriverPayload()), hdr);
        const postRes = http.post(`${BASE_URL}/api/v1/posts`, JSON.stringify(randomPostPayload(120)), hdr);
        if (postRes.status !== 201) continue;
        const postId = postRes.json('data.id');
        const rideRes = http.post(`${BASE_URL}/api/v1/rides`, JSON.stringify({ postId }), hdr);
        if (rideRes.status !== 201) continue;
        const rideId = rideRes.json('data.id');
        const startRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/start`, null, hdr);
        if (startRes.status !== 200) continue;
        pool.push({ token, rideId });
        sleep(0.01);
    }
    console.log(`[sat] setup 완료: 풀 ${pool.length}개`);
    return { pool };
}

export default function (data) {
    const { pool } = data;
    if (!pool || pool.length === 0) return;
    const { token, rideId } = pool[(__VU - 1) % pool.length];
    const startMs = Date.now();
    ws.connect(WS_URL, { headers: { 'Authorization': `Bearer ${token}` } }, function (socket) {
        let connected = false;
        socket.on('open', () => socket.send(stompConnectFrame(token)));
        socket.on('message', (msg) => {
            if (msg.startsWith('CONNECTED') && !connected) {
                connected = true;
                wsConnectDuration.add(Date.now() - startMs);
                socket.setInterval(() => {
                    try {
                        const { latitude, longitude } = randomLocation(37.5, 127.0);
                        socket.send(stompSendLocationFrame(rideId, latitude, longitude));
                        clientSent.add(1);
                    } catch (e) { sendErrors.add(1); }
                }, INTERVAL);
            }
        });
        socket.on('error', () => sendErrors.add(1));
        socket.setTimeout(() => socket.close(), (FLOOD_SEC - 2) * 1000);
    });
}
